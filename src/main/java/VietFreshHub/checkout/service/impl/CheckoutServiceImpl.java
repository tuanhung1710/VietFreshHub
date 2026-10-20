package VietFreshHub.checkout.service.impl;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.checkout.service.InventoryReservationService;
import VietFreshHub.checkout.service.CheckoutShippingQuoteService;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.dto.CheckoutResult;
import VietFreshHub.checkout.dto.ShopShippingQuote;
import VietFreshHub.checkout.repository.CheckoutAddressRepository;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.OrderPlacedEvent;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.entity.PaymentMethod;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.sellerapplication.entity.SellerApplication;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Điều phối checkout COD: kiểm tra dữ liệu, tạo đơn, giữ kho và ghi nhận payment chờ thu.
 * Các helper ghi dữ liệu dùng chung transaction của confirm().
 */
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final CheckoutAddressRepository checkoutAddressRepository;
    private final CustomerAddressService customerAddressService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final CatalogService catalogService;
    private final InventoryReservationService inventoryReservationService;
    private final CheckoutShippingQuoteService shippingQuoteService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long customerId) {
        return preview(customerId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long customerId, List<Long> selectedItemIds) {
        CartDto selectedCart = selectCartItems(cartService.getActiveCart(customerId), selectedItemIds);
        List<CheckoutPreview.AddressOption> addressOptions = new ArrayList<>();
        for (Address address : checkoutAddressRepository.findByUserUserIdOrderByIsDefaultDescAddressIdAsc(customerId)) {
            addressOptions.add(new CheckoutPreview.AddressOption(
                    address.getAddressId(), address.getRecipientName(), address.getPhone(),
                    formatAddress(address), address.getIsDefault(),
                    customerAddressService.isDeliveryLocationComplete(address)
            ));
        }
        boolean codAvailable = paymentMethodRepository.findByMethodCodeAndStatus("COD", "ACTIVE").isPresent();
        // Phí giao hàng sẽ được bổ sung sau khi khách chọn địa chỉ nhận hàng.
        return new CheckoutPreview(selectedCart, List.copyOf(addressOptions), null, Map.of(), null, null,
                codAvailable, createCartFingerprint(selectedCart), null, false);
    }

    @Override
    @Transactional
    public CheckoutResult confirm(Long customerId, CheckoutDraft draft, CheckoutRequest request) {
        // 1. Kiểm tra phiên và trả lại đơn đã có nếu khách gửi lặp cùng token.
        validateDraft(customerId, draft, request);
        User buyer = lockBuyer(customerId);
        String orderCode = "VF-" + draft.token();
        Optional<Order> existingOrder = orderRepository.findByOrderCodeAndCustomerUserId(orderCode, customerId);
        if (existingOrder.isPresent()) {
            return toCheckoutResult(existingOrder.get());
        }

        // 2. Kiểm tra lại địa chỉ, mặt hàng và báo giá ngay trước khi tạo đơn.
        PaymentMethod paymentMethod = requireCod(request.getPaymentMethodCode());
        Address deliveryAddress = lockAddress(customerId, request.getAddressId());
        Cart cart = requireActiveCart(customerId, draft.cartId());
        List<CartItem> selectedItems = requireSelectedItems(cart, draft.selectedItemIds());
        validateCurrentItems(customerId, draft, selectedItems);
        Map<Long, List<CartItem>> itemsByShopId = groupItemsByShop(selectedItems);
        BigDecimal subtotal = calculateSubtotal(selectedItems);
        BigDecimal shippingFee = shippingQuoteService.validateForConfirmation(
                draft, deliveryAddress, itemsByShopId.keySet());

        // 3. Tạo đơn tổng, đơn từng shop, giữ kho và ghi nhận COD chưa thu tiền.
        Order order = createOrder(buyer, orderCode, subtotal, shippingFee, deliveryAddress);
        CreatedShopOrders createdShopOrders = createShopOrders(order, buyer, itemsByShopId, draft.shopQuotes());
        inventoryReservationService.reserve(createdShopOrders.reservations(), customerId);
        createPendingPayment(order, paymentMethod);

        // 4. Giữ lại món chưa chọn; nếu đặt hết giỏ thì đóng giỏ hiện tại.
        if (selectedItems.size() == cart.getItems().size()) {
            cart.setStatus("CHECKED_OUT");
        } else {
            cart.getItems().removeAll(selectedItems);
        }
        // flush chưa phải commit; consumer gửi thông báo cần xử lý AFTER_COMMIT.
        entityManager.flush();
        eventPublisher.publishEvent(new OrderPlacedEvent(
                order.getOrderId(), customerId, List.copyOf(createdShopOrders.shopOrderIds())));
        return toCheckoutResult(order);
    }

    // Kiểm tra và khóa dữ liệu trước khi tạo đơn.

    private void validateDraft(Long customerId, CheckoutDraft draft, CheckoutRequest request) {
        if (customerId == null || draft == null || request == null || !customerId.equals(draft.customerId())
                || !Objects.equals(draft.token(), request.getCheckoutToken()) || draft.token() == null
                || !draft.token().matches("[0-9a-f]{32}")) {
            throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng tải lại trang.");
        }
        if (request.getAddressId() == null || !Objects.equals(request.getAddressId(), draft.addressId())) {
            throw new CheckoutException("Địa chỉ nhận hàng khác với báo giá. Vui lòng tính lại phí giao hàng trước khi xác nhận.");
        }
    }

    private User lockBuyer(Long customerId) {
        // Khóa user để các yêu cầu của cùng một khách không sửa giỏ/đặt đơn đồng thời.
        User buyer = userRepository.findByUserIdForUpdate(customerId)
                .orElseThrow(() -> new CheckoutException("Tài khoản không hợp lệ."));
        entityManager.refresh(buyer, LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(buyer.getStatus())) {
            throw new CheckoutException("Tài khoản không còn được phép đặt hàng.");
        }
        return buyer;
    }

    private PaymentMethod requireCod(String methodCode) {
        if (!"COD".equals(methodCode)) {
            throw new CheckoutException("Hiện chỉ hỗ trợ thanh toán khi nhận hàng.");
        }
        PaymentMethod paymentMethod = paymentMethodRepository.findByMethodCodeAndStatus("COD", "ACTIVE")
                .orElseThrow(() -> new CheckoutException("Thanh toán khi nhận hàng hiện chưa khả dụng."));
        entityManager.refresh(paymentMethod, LockModeType.PESSIMISTIC_READ);
        if (!"ACTIVE".equals(paymentMethod.getStatus())) {
            throw new CheckoutException("Thanh toán khi nhận hàng hiện chưa khả dụng.");
        }
        return paymentMethod;
    }

    private Address lockAddress(Long customerId, Long addressId) {
        Address address;
        try {
            address = customerAddressService.getDeliveryAddress(customerId, addressId);
        } catch (IllegalArgumentException ex) {
            throw new CheckoutException(ex.getMessage());
        }
        entityManager.refresh(address, LockModeType.PESSIMISTIC_WRITE);
        if (!customerId.equals(address.getUser().getUserId())) {
            throw new CheckoutException("Địa chỉ nhận hàng không hợp lệ.");
        }
        if (!customerAddressService.isDeliveryLocationComplete(address)) {
            throw new CheckoutException("Địa chỉ nhận hàng chưa đủ thông tin vị trí. Vui lòng cập nhật địa chỉ bằng cách chọn gợi ý VietMap trong mục Địa chỉ giao hàng.");
        }
        return address;
    }

    private Cart requireActiveCart(Long customerId, Long cartId) {
        Cart cart = cartRepository.findByUserUserIdAndStatus(customerId,"ACTIVE")
                .orElseThrow(() -> new CheckoutException("Giỏ hàng đang trống hoặc đã được đặt hàng."));
        if (!Objects.equals(cart.getCartId(), cartId) || cart.getItems().isEmpty()) {
            throw new CheckoutException("Giỏ hàng đã thay đổi. Vui lòng xem lại trước khi đặt hàng.");
        }
        return cart;
    }

    private List<CartItem> requireSelectedItems(Cart cart, List<Long> selectedItemIds) {
        Set<Long> selection = new HashSet<>(selectedItemIds);
        if (selection.isEmpty() || selection.size() != selectedItemIds.size()) {
            throw new CheckoutException("Vui lòng chọn ít nhất một sản phẩm hợp lệ để đặt hàng.");
        }
        List<CartItem> selected = cart.getItems().stream().filter(item -> selection.contains(item.getCartItemId())).toList();
        if (selected.size() != selection.size()) {
            throw new CheckoutException("Sản phẩm đã chọn không còn trong giỏ. Vui lòng chọn lại.");
        }
        return selected;
    }

    private void validateCurrentItems(Long customerId, CheckoutDraft draft, List<CartItem> selectedItems) {
        lockCatalog(selectedItems);
        for (CartItem item : selectedItems) {
            if (item.getQuantity() == null || item.getQuantity() < 1 || !catalogService.isPurchasable(item.getVariant())) {
                throw new CheckoutException("Một sản phẩm hoặc cửa hàng không còn nhận đơn. Vui lòng kiểm tra lại giỏ.");
            }
        }
        CartDto currentSelection = selectCartItems(cartService.getActiveCart(customerId), draft.selectedItemIds());
        if (!createCartFingerprint(currentSelection).equals(draft.fingerprint())) {
            throw new CheckoutException("Giá hoặc số lượng trong giỏ vừa thay đổi. Vui lòng xem lại đơn và xác nhận lần nữa.");
        }
    }

    private void lockCatalog(List<CartItem> selected) {
        // TreeMap loại ID trùng và giữ thứ tự khóa giống nhau giữa các request.
        Map<Long, CatalogVariant> variantsById = new TreeMap<>();
        for (CartItem item : selected) {
            CatalogVariant variant = item.getVariant();
            variantsById.putIfAbsent(variant.getVariantId(), variant);
        }
        Map<Long, CatalogProduct> productsById = new TreeMap<>();
        for (CatalogVariant variant : variantsById.values()) {
            CatalogProduct product = variant.getProduct();
            productsById.putIfAbsent(product.getProductId(), product);
        }
        Map<Long, Shop> shopsById = new TreeMap<>();
        for (CatalogProduct product : productsById.values()) {
            Shop shop = product.getShop();
            shopsById.putIfAbsent(shop.getShopId(), shop);
        }

        for (Shop shop : shopsById.values()) {
            entityManager.refresh(shop, LockModeType.PESSIMISTIC_READ);
        }
        Set<Long> applicationIds = new TreeSet<>();
        for (Shop shop : shopsById.values()) {
            if (shop.getApplicationId() != null) {
                applicationIds.add(shop.getApplicationId());
            }
        }
        for (Long applicationId : applicationIds) {
            SellerApplication application = entityManager.getReference(SellerApplication.class, applicationId);
            entityManager.refresh(application, LockModeType.PESSIMISTIC_READ);
        }
        for (CatalogProduct product : productsById.values()) {
            entityManager.refresh(product, LockModeType.PESSIMISTIC_READ);
        }
        for (CatalogVariant variant : variantsById.values()) {
            entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
        }

        // Quan hệ shop/product có thể đã đổi trước khi lấy khóa.
        for (CatalogProduct product : productsById.values()) {
            if (!shopsById.containsKey(product.getShop().getShopId())) {
                throw new CheckoutException("Sản phẩm vừa thay đổi cửa hàng. Vui lòng kiểm tra lại giỏ.");
            }
        }
        for (CatalogVariant variant : variantsById.values()) {
            if (!productsById.containsKey(variant.getProduct().getProductId())) {
                throw new CheckoutException("Sản phẩm vừa thay đổi cửa hàng. Vui lòng kiểm tra lại giỏ.");
            }
        }
    }

    // Tạo entity và snapshot trong transaction của confirm().

    private Order createOrder(User buyer, String orderCode, BigDecimal subtotal, BigDecimal shippingFee, Address address) {
        Order order = new Order();
        order.setCustomer(buyer);
        order.setOrderCode(orderCode);
        order.setSubtotal(normalizeMoney(subtotal));
        order.setShippingFee(shippingFee);
        order.setGrandTotal(normalizeMoney(subtotal.add(shippingFee)));
        orderRepository.save(order);
        entityManager.persist(snapshotAddress(order, address));
        return order;
    }

    private CreatedShopOrders createShopOrders(Order order, User buyer, Map<Long, List<CartItem>> itemsByShopId,
                                               Map<Long, ShopShippingQuote> quotesByShopId) {
        List<InventoryReservationService.Reservation> reservations = new ArrayList<>();
        List<Long> shopOrderIds = new ArrayList<>();
        for (List<CartItem> shopItems : itemsByShopId.values()) {
            Long shopId = shopItems.getFirst().getVariant().getProduct().getShop().getShopId();
            ShopOrder shopOrder = createShopOrder(order, shopItems, quotesByShopId.get(shopId));
            shopOrderIds.add(shopOrder.getShopOrderId());
            for (CartItem cartItem : shopItems) {
                OrderItem orderItem = createOrderItem(shopOrder, cartItem);
                reservations.add(new InventoryReservationService.Reservation(
                        orderItem.getOrderItemId(), cartItem.getVariant().getVariantId(), cartItem.getQuantity()));
            }
            recordPlacedHistory(shopOrder, buyer);
        }
        return new CreatedShopOrders(shopOrderIds, reservations);
    }

    private ShopOrder createShopOrder(Order order, List<CartItem> shopItems, ShopShippingQuote shippingQuote) {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setOrder(order);
        shopOrder.setShop(shopItems.getFirst().getVariant().getProduct().getShop());
        BigDecimal shippingFee = shippingQuote.shippingFee();
        shopOrder.setShippingDistanceMeters(shippingQuoteService.distanceSnapshot(shippingQuote.distanceMeters()));
        BigDecimal subtotal = calculateSubtotal(shopItems);
        shopOrder.setSubtotal(normalizeMoney(subtotal));
        shopOrder.setShippingFee(shippingFee);
        shopOrder.setTotalAmount(normalizeMoney(subtotal.add(shippingFee)));
        entityManager.persist(shopOrder);
        return shopOrder;
    }

    private OrderItem createOrderItem(ShopOrder shopOrder, CartItem cartItem) {
        CatalogVariant variant = cartItem.getVariant();
        OrderItem orderItem = new OrderItem();
        orderItem.setShopOrder(shopOrder);
        orderItem.setVariant(variant);
        orderItem.setProductNameSnapshot(variant.getProduct().getName());
        orderItem.setVariantNameSnapshot(variant.getVariantName());
        orderItem.setSkuSnapshot(variant.getSku());
        orderItem.setUnitPrice(variant.getPrice());
        orderItem.setQuantity(cartItem.getQuantity());
        orderItem.setTotalAmount(normalizeMoney(calculateLineTotal(cartItem)));
        entityManager.persist(orderItem);
        return orderItem;
    }

    private void recordPlacedHistory(ShopOrder shopOrder, User buyer) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setShopOrder(shopOrder);
        history.setStatus("PENDING");
        history.setChangedBy(buyer);
        history.setNote("Khách hàng đặt đơn COD.");
        entityManager.persist(history);
    }

    private void createPendingPayment(Order order, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(order.getGrandTotal());
        entityManager.persist(payment);
    }

    // Tính toán và chuyển đổi dữ liệu, không ghi DB.

    private Map<Long, List<CartItem>> groupItemsByShop(List<CartItem> selectedItems) {
        Map<Long, List<CartItem>> itemsByShopId = new TreeMap<>();
        for (CartItem item : selectedItems) {
            Long shopId = item.getVariant().getProduct().getShop().getShopId();
            // Các món cùng shop sẽ được tạo chung một ShopOrder.
            itemsByShopId.computeIfAbsent(shopId, ignored -> new ArrayList<>()).add(item);
        }
        return itemsByShopId;
    }

    private BigDecimal calculateSubtotal(List<CartItem> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            subtotal = subtotal.add(calculateLineTotal(item));
        }
        return subtotal;
    }

    private BigDecimal calculateLineTotal(CartItem item) {
        return item.getVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private CartDto selectCartItems(CartDto cart, List<Long> itemIds) {
        if (itemIds == null) {
            return cart;
        }
        if (itemIds.isEmpty() || itemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CheckoutException("Vui lòng chọn ít nhất một sản phẩm hợp lệ.");
        }
        Set<Long> selectedIds = new HashSet<>(itemIds);
        List<CartItemDto> selected = cart.getItems().stream().filter(item -> selectedIds.contains(item.getCartItemId())).toList();
        if (selected.size() != selectedIds.size()) {
            throw new CheckoutException("Sản phẩm đã chọn không thuộc giỏ hàng hiện tại của bạn.");
        }
        return CartDto.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .status(cart.getStatus())
                .items(selected)
                .totalItems(selected.stream().mapToLong(CartItemDto::getQuantity).sum())
                .totalAmount(selected.stream().map(CartItemDto::getItemSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .hasUnavailableItems(selected.stream().anyMatch(item -> !item.isPurchasable()))
                .build();
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalizedAmount.signum() < 0 || normalizedAmount.precision() > 18) {
            throw new CheckoutException("Giá trị đơn vượt giới hạn cho phép.");
        }
        return normalizedAmount;
    }

    private CheckoutResult toCheckoutResult(Order order) {
        return new CheckoutResult(order.getOrderId(), order.getOrderCode(), order.getGrandTotal());
    }

    private OrderAddress snapshotAddress(Order order, Address address) {
        OrderAddress snapshot = new OrderAddress();
        snapshot.setOrder(order);
        snapshot.setRecipientName(address.getRecipientName());
        snapshot.setPhone(address.getPhone());
        snapshot.setProvince(address.getProvince());
        snapshot.setDistrict(address.getDistrict());
        snapshot.setWard(address.getWard());
        snapshot.setAddressLine(address.getAddressLine());
        snapshot.setProvinceId(address.getProvinceId());
        snapshot.setDistrictId(address.getDistrictId());
        snapshot.setWardId(address.getWardId());
        snapshot.setFormattedAddress(address.getFormattedAddress());
        snapshot.setLatitude(address.getLatitude());
        snapshot.setLongitude(address.getLongitude());
        return snapshot;
    }

    private String formatAddress(Address address) {
        if (address.getFormattedAddress() != null && !address.getFormattedAddress().isBlank()) {
            return address.getFormattedAddress();
        }
        List<String> parts = new ArrayList<>();
        if (address.getAddressLine() != null) {
            Arrays.stream(address.getAddressLine().split(",")).map(String::strip)
                    .filter(part -> !part.isBlank()).forEach(parts::add);
        }
        for (String part : Arrays.asList(address.getWard(), address.getDistrict(), address.getProvince())) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String component = part.strip();
            if (parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(component))) {
                parts.add(component);
            }
        }
        return String.join(", ", parts);
    }

    private String createCartFingerprint(CartDto cart) {
        // Sắp xếp cố định để fingerprint không đổi khi thứ tự hiển thị trong giỏ thay đổi.
        List<CartItemDto> sortedItems = new ArrayList<>(cart.getItems());
        sortedItems.sort(Comparator.comparing(CartItemDto::getCartItemId));
        StringBuilder cartSignature = new StringBuilder(String.valueOf(cart.getCartId()));
        for (CartItemDto item : sortedItems) {
            cartSignature.append('|')
                    .append(item.getCartItemId()).append(':')
                    .append(item.getVariantId()).append(':')
                    .append(item.getQuantity()).append(':')
                    .append(item.getUnitPriceSnapshot().stripTrailingZeros().toPlainString()).append(':')
                    .append(item.getShopId());
        }
        try {
            byte[] signatureBytes = cartSignature.toString().getBytes(StandardCharsets.UTF_8);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signatureBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private record CreatedShopOrders(
            List<Long> shopOrderIds,
            List<InventoryReservationService.Reservation> reservations
    ) {}

}
