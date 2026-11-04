package VietFreshHub.checkout.service.impl;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.coupon.services.CouponApplicationService;
import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.dto.CheckoutResult;
import VietFreshHub.checkout.repository.CheckoutAddressRepository;
import VietFreshHub.checkout.service.CheckoutOrderService;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.checkout.service.CheckoutShippingQuoteService;
import VietFreshHub.checkout.service.InventoryReservationService;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.loyalty.services.LoyaltyService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.OrderPlacedEvent;
import VietFreshHub.payment.entity.PaymentMethod;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.sellerapplication.entity.SellerApplication;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static VietFreshHub.checkout.service.CheckoutCartSupport.calculateSubtotal;
import static VietFreshHub.checkout.service.CheckoutCartSupport.createCartFingerprint;
import static VietFreshHub.checkout.service.CheckoutCartSupport.groupItemsByShop;
import static VietFreshHub.checkout.service.CheckoutCartSupport.selectCartItems;


@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private final VietFreshHub.catalog.repository.CatalogVariantRepository buyNowVariants;
    private final VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository buyNowStock;
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
    private final CheckoutOrderService checkoutOrderService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final VietFreshHub.payment.repository.PaymentRepository paymentRepository;
    private final VietFreshHub.payment.vnpay.VnpayProperties vnpayProperties;
    private final CouponApplicationService couponApplicationService;
    private final LoyaltyService loyaltyService;

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long customerId) {
        return preview(customerId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long customerId, List<Long> selectedItemIds) {
        CartDto selectedCart = selectCartItems(cartService.getActiveCart(customerId), selectedItemIds);
        return previewForSelection(customerId, selectedCart);
    }

    private CheckoutPreview previewForSelection(Long customerId, CartDto selectedCart) {
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
                codAvailable, vnpayProperties.isReady() && paymentMethodRepository.findByMethodCodeAndStatus("BANK_TRANSFER", "ACTIVE").isPresent(), createCartFingerprint(selectedCart), null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview previewBuyNow(Long customerId, Long variantId, Integer quantity) {
        CartItem item = buyNowItem(variantId, quantity);
        return previewForSelection(customerId, buyNowCart(customerId, item));
    }

    private CartItem buyNowItem(Long variantId, Integer quantity) {
        if (variantId == null || variantId < 1 || quantity == null || quantity < 1)
            throw new CheckoutException("Sản phẩm hoặc số lượng mua ngay không hợp lệ.");
        var variant = buyNowVariants.findById(variantId).orElseThrow(() -> new CheckoutException("Sản phẩm không còn tồn tại."));
        var item = new CartItem();
        item.setCartItemId(variantId);
        item.setVariant(variant);
        item.setQuantity(quantity);
        item.setUnitPriceSnapshot(variant.getPrice());
        return item;
    }

    private CartDto buyNowCart(Long customerId, CartItem item) {
        var dto = VietFreshHub.cart.service.CartViewMapper.toCartItem(item,
                buyNowStock.getAvailableStockByVariantId(item.getVariant().getVariantId()), catalogService.isPurchasable(item.getVariant()));
        return CartDto.builder().userId(customerId).status("BUY_NOW").items(List.of(dto))
                .totalItems(item.getQuantity()).totalAmount(dto.getItemSubtotal()).hasUnavailableItems(!dto.isPurchasable()).build();
    }

    @Override
    @Transactional
    public CheckoutResult confirm(Long customerId, CheckoutDraft draft, CheckoutRequest request) {

        validateDraft(customerId, draft, request);
        User buyer = lockBuyer(customerId);
        String checkoutReference = "VF-" + draft.token();
        Optional<Order> existingOrder = orderRepository.findByOrderCodeAndCustomerUserId(checkoutReference, customerId);
        if (existingOrder.isPresent()) {
            return toCheckoutResult(existingOrder.get());
        }

        // Kiểm tra lại địa chỉ, mặt hàng và báo giá ngay trước khi tạo đơn.
        PaymentMethod paymentMethod = requirePaymentMethod(request.getPaymentMethodCode());
        Address deliveryAddress = lockAddress(customerId, request.getAddressId());
        Cart cart = draft.buyNow() ? null : requireActiveCart(customerId, draft.cartId());
        List<CartItem> selectedItems = draft.buyNow() ? List.of(buyNowItem(draft.buyNowVariantId(), draft.buyNowQuantity())) : requireSelectedItems(cart, draft.selectedItemIds());
        validateCurrentItems(customerId, draft, selectedItems);
        Map<Long, List<CartItem>> itemsByShopId = groupItemsByShop(selectedItems);
        BigDecimal subtotal = calculateSubtotal(selectedItems);
        BigDecimal shippingFee = shippingQuoteService.validateForConfirmation(
                draft, deliveryAddress, itemsByShopId.keySet());

        // Tạo đơn tổng, đơn từng shop, giữ kho và ghi nhận payment theo phương thức đã chọn.
        Order order = checkoutOrderService.createOrder(buyer, orderCode, subtotal, shippingFee, deliveryAddress);
        if ("BANK_TRANSFER".equals(paymentMethod.getMethodCode())) order.setPaymentStatus("PENDING");
        CheckoutOrderService.CreatedShopOrders createdShopOrders = checkoutOrderService.createShopOrders(order, buyer, itemsByShopId, draft.shopQuotes());
        inventoryReservationService.reserve(createdShopOrders.reservations(), customerId);
        if ("BANK_TRANSFER".equals(paymentMethod.getMethodCode())) {
            for (Long shopOrderId : createdShopOrders.shopOrderIds()) {
                entityManager.getReference(VietFreshHub.order.entity.ShopOrder.class, shopOrderId).setPaymentStatus("PENDING");
            }
        }

        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            try {
                couponApplicationService.applyToOrder(customerId, order.getOrderId(), request.getCouponCode());
            } catch (IllegalArgumentException ex) {
                throw new CheckoutException(ex.getMessage());
            }
        }
        BigDecimal rewardAmountToUse = request.getRewardAmountToUse();
        if (rewardAmountToUse == null || rewardAmountToUse.signum() < 0
                || rewardAmountToUse.stripTrailingZeros().scale() > 0
                || rewardAmountToUse.compareTo(new BigDecimal("9999999999999999")) > 0) {
            throw new CheckoutException("Tiền thưởng phải là số đồng nguyên không âm, tối đa 16 chữ số");
        }
        if (rewardAmountToUse.signum() > 0) {
            try {
                loyaltyService.redeemToOrder(customerId, order.getOrderId(), rewardAmountToUse);
            } catch (IllegalArgumentException ex) {
                throw new CheckoutException(ex.getMessage());
            }
        }

        var payment = checkoutOrderService.createPendingPayment(order, paymentMethod);
        if ("BANK_TRANSFER".equals(paymentMethod.getMethodCode())) {
            payment.setProvider("VNPAY");
            entityManager.flush();
            payment.setTransactionReference("VF" + payment.getPaymentId());
        }

        // Giữ lại món chưa chọn; nếu đặt hết giỏ thì đóng giỏ hiện tại.
        if (cart != null && selectedItems.size() == cart.getItems().size()) {
            cart.setStatus("CHECKED_OUT");
        } else if (cart != null) {
            cart.getItems().removeAll(selectedItems);
        }
        // flush chưa phải commit; consumer gửi thông báo cần xử lý AFTER_COMMIT.
        entityManager.flush();
        eventPublisher.publishEvent(new OrderPlacedEvent(
                order.getOrderId(), customerId, List.copyOf(createdShopOrders.shopOrderIds())));
        return new CheckoutResult(order.getOrderId(), order.getOrderCode(), order.getGrandTotal(),
                payment.getPaymentId(), paymentMethod.getMethodCode());
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

    private PaymentMethod requirePaymentMethod(String methodCode) {
        if (methodCode == null || !Set.of("COD", "BANK_TRANSFER").contains(methodCode))
            throw new CheckoutException("Vui lòng chọn phương thức thanh toán hợp lệ.");
        if ("BANK_TRANSFER".equals(methodCode) && !vnpayProperties.isReady())
            throw new CheckoutException("Chuyển khoản qua VNPAY chưa được cấu hình. Vui lòng chọn COD hoặc thử lại sau.");
        var method = paymentMethodRepository.findByMethodCodeAndStatus(methodCode, "ACTIVE")
                .orElseThrow(() -> new CheckoutException("Phương thức thanh toán hiện chưa khả dụng."));
        entityManager.refresh(method, LockModeType.PESSIMISTIC_READ);
        if (!"ACTIVE".equals(method.getStatus()))
            throw new CheckoutException("Phương thức thanh toán hiện chưa khả dụng.");
        return method;
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
        Cart cart = cartRepository.findByUserUserIdAndStatus(customerId, "ACTIVE")
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
        List<CartItem> selected = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            if (selection.contains(item.getCartItemId())) {
                selected.add(item);
            }
        }
        if (selected.size() != selection.size()) {
            throw new CheckoutException("Sản phẩm đã chọn không còn trong giỏ. Vui lòng chọn lại.");
        }
        return selected;
    }

    private void validateCurrentItems(Long customerId, CheckoutDraft draft, List<CartItem> selectedItems) {
        lockCatalog(selectedItems);
        for (CartItem item : selectedItems) {
            if (item.getQuantity() == null || item.getQuantity() < 1 || !catalogService.isPurchasable(item.getVariant())) {
                throw new CheckoutException(item.getVariant().getProduct().getName() + " — "
                        + item.getVariant().getVariantName()
                        + ": sản phẩm hoặc cửa hàng không còn nhận đơn. Vui lòng kiểm tra lại giỏ.");
            }
        }
        CartDto currentSelection = draft.buyNow() ? buyNowCart(customerId, selectedItems.getFirst()) : selectCartItems(cartService.getActiveCart(customerId), draft.selectedItemIds());
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

    private CheckoutResult toCheckoutResult(Order order) {
        Payment payment = paymentRepository.findTopByOrderOrderIdAndProviderOrderByPaymentIdDesc(order.getOrderId(), "VNPAY")
                .or(() -> paymentRepository.findTopByOrderOrderIdAndPaymentMethodMethodCodeOrderByPaymentIdDesc(order.getOrderId(), "VNPAY"))
                .orElseGet(() -> paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(order.getOrderId()).stream().findFirst()
                        .orElseThrow(() -> new CheckoutException("Không tìm thấy bản ghi thanh toán của đơn hàng.")));
        return result(order, payment);
    }

    private CheckoutResult result(Order order, Payment payment) {
        return new CheckoutResult(order.getOrderId(), order.getOrderCode(), order.getGrandTotal(), payment.getPaymentId(),
                payment.getPaymentMethod().getMethodCode());
    }

    private String formatAddress(Address address) {
        if (address.getFormattedAddress() != null && !address.getFormattedAddress().isBlank()) {
            return address.getFormattedAddress();
        }
        List<String> parts = new ArrayList<>();
        if (address.getAddressLine() != null) {
            for (String part : address.getAddressLine().split(",")) {
                String component = part.strip();
                if (!component.isBlank()) {
                    parts.add(component);
                }
            }
        }
        for (String part : Arrays.asList(address.getWard(), address.getDistrict(), address.getProvince())) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String component = part.strip();
            boolean alreadyIncluded = false;
            for (String existing : parts) {
                if (existing.equalsIgnoreCase(component)) {
                    alreadyIncluded = true;
                    break;
                }
            }
            if (!alreadyIncluded) {
                parts.add(component);
            }
        }
        return String.join(", ", parts);
    }

}
