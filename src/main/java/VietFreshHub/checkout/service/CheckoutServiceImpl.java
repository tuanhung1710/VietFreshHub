package VietFreshHub.checkout.service;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.sellerapplication.entity.SellerApplication;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.checkout.service.InventoryReservationService;
import VietFreshHub.checkout.service.ShippingQuoteService;
import VietFreshHub.checkout.repository.CheckoutAddressRepository;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.service.OrderPlacedEvent;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.payment.vnpay.VnpayProperties;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.service.CatalogService;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private final UserRepository users;
    private final CartRepository carts;
    private final CartService cartService;
    private final CheckoutAddressRepository addresses;
    private final CustomerAddressService customerAddresses;
    private final PaymentMethodRepository paymentMethods;
    private final PaymentRepository payments;
    private final VnpayProperties vnpayProperties;
    private final OrderRepository orders;
    private final CatalogService catalog;
    private final InventoryReservationService inventory;
    private final CheckoutShippingQuoteService shippingQuotes;
    private final EntityManager em;
    private final ApplicationEventPublisher events;

    @Override @Transactional(readOnly=true)
    public CheckoutPreview preview(Long customerId) {
        return preview(customerId,null);
    }

    @Override @Transactional(readOnly=true)
    public CheckoutPreview preview(Long customerId,List<Long> selectedItemIds) {
        CartDto cart=selectCart(cartService.getActiveCart(customerId),selectedItemIds);
        var options=addresses.findByUserUserIdOrderByIsDefaultDescAddressIdAsc(customerId).stream()
                .map(a -> new CheckoutPreview.AddressOption(a.getAddressId(),a.getRecipientName(),a.getPhone(),
                        addressText(a),a.getIsDefault(),
                        customerAddresses.isDeliveryLocationComplete(a))).toList();
        return new CheckoutPreview(cart,options,null,Map.of(),null,null,
                paymentMethods.findByMethodCodeAndStatus("COD","ACTIVE").isPresent(),
                vnpayProperties.isReady() && paymentMethods.findByMethodCodeAndStatus("VNPAY","ACTIVE").isPresent(),
                fingerprint(cart),null,false);
    }

    private String addressText(Address address) {
        if(address.getFormattedAddress()!=null && !address.getFormattedAddress().isBlank()) {
            return address.getFormattedAddress();
        }
        List<String> parts=new ArrayList<>();
        if(address.getAddressLine()!=null) {
            Arrays.stream(address.getAddressLine().split(",")).map(String::strip)
                    .filter(part -> !part.isBlank()).forEach(parts::add);
        }
        for(String part:Arrays.asList(address.getWard(),address.getDistrict(),address.getProvince())) {
            if(part==null || part.isBlank()) continue;
            String component=part.strip();
            if(parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(component))) parts.add(component);
        }
        return String.join(", ",parts);
    }

    @Override @Transactional
    public CheckoutResult confirm(Long customerId,CheckoutDraft draft,CheckoutRequest request) {
        if(customerId==null || draft==null || request==null || !customerId.equals(draft.customerId())
                || !Objects.equals(draft.token(),request.getCheckoutToken()) || draft.token()==null
                || !draft.token().matches("[0-9a-f]{32}")) {
            throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng tải lại trang.");
        }
        if(request.getAddressId()==null || !Objects.equals(request.getAddressId(),draft.addressId())) {
            throw new CheckoutException("Địa chỉ nhận hàng khác với báo giá. Vui lòng tính lại phí giao hàng trước khi xác nhận.");
        }
        User buyer=users.findByUserIdForUpdate(customerId).orElseThrow(() -> new CheckoutException("Tài khoản không hợp lệ."));
        em.refresh(buyer,LockModeType.PESSIMISTIC_WRITE);
        if(!"ACTIVE".equals(buyer.getStatus())) throw new CheckoutException("Tài khoản không còn được phép đặt hàng.");
        String orderCode="VF-"+draft.token();
        Optional<Order> duplicate=orders.findByOrderCodeAndCustomerUserId(orderCode,customerId);
        if(duplicate.isPresent()) return result(duplicate.get());
        String methodCode=request.getPaymentMethodCode();
        if(methodCode==null || !Set.of("COD","VNPAY").contains(methodCode)) throw new CheckoutException("Vui lòng chọn phương thức thanh toán hợp lệ.");
        if("VNPAY".equals(methodCode) && !vnpayProperties.isReady())
            throw new CheckoutException("Thanh toán VNPAY chưa được cấu hình. Vui lòng chọn COD hoặc thử lại sau.");
        var method=paymentMethods.findByMethodCodeAndStatus(methodCode,"ACTIVE")
                .orElseThrow(() -> new CheckoutException("Phương thức thanh toán hiện chưa khả dụng."));
        em.refresh(method,LockModeType.PESSIMISTIC_READ);
        if(!"ACTIVE".equals(method.getStatus())) throw new CheckoutException("Phương thức thanh toán hiện chưa khả dụng.");
        Address address;
        try {
            address=customerAddresses.getDeliveryAddress(customerId,request.getAddressId());
        } catch(IllegalArgumentException ex) {
            throw new CheckoutException(ex.getMessage());
        }
        em.refresh(address,LockModeType.PESSIMISTIC_WRITE);
        if(!customerId.equals(address.getUser().getUserId())) throw new CheckoutException("Địa chỉ nhận hàng không hợp lệ.");
        if(!customerAddresses.isDeliveryLocationComplete(address)) {
            throw new CheckoutException("Địa chỉ nhận hàng chưa đủ thông tin vị trí. Vui lòng cập nhật địa chỉ bằng cách chọn gợi ý VietMap trong mục Địa chỉ giao hàng.");
        }
        Cart cart=carts.findByUserUserIdAndStatus(customerId,"ACTIVE")
                .orElseThrow(() -> new CheckoutException("Giỏ hàng đang trống hoặc đã được đặt hàng."));
        if(!Objects.equals(cart.getCartId(),draft.cartId()) || cart.getItems().isEmpty()) {
            throw new CheckoutException("Giỏ hàng đã thay đổi. Vui lòng xem lại trước khi đặt hàng.");
        }
        Set<Long> selection=new HashSet<>(draft.selectedItemIds());
        if(selection.isEmpty() || selection.size()!=draft.selectedItemIds().size())
            throw new CheckoutException("Vui lòng chọn ít nhất một sản phẩm hợp lệ để đặt hàng.");
        List<CartItem> selected=cart.getItems().stream().filter(item -> selection.contains(item.getCartItemId())).toList();
        if(selected.size()!=selection.size()) throw new CheckoutException("Sản phẩm đã chọn không còn trong giỏ. Vui lòng chọn lại.");
        lockCatalog(selected);
        for(CartItem item:selected) {
            if(item.getQuantity()==null || item.getQuantity()<1 || !catalog.isPurchasable(item.getVariant())) {
                throw new CheckoutException("Một sản phẩm hoặc cửa hàng không còn nhận đơn. Vui lòng kiểm tra lại giỏ.");
            }
        }
        CartDto current=selectCart(cartService.getActiveCart(customerId),draft.selectedItemIds());
        if(!fingerprint(current).equals(draft.fingerprint())) {
            throw new CheckoutException("Giá hoặc số lượng trong giỏ vừa thay đổi. Vui lòng xem lại đơn và xác nhận lần nữa.");
        }
        Map<Long,List<CartItem>> groups=selected.stream().collect(Collectors.groupingBy(
                item -> item.getVariant().getProduct().getShop().getShopId(),TreeMap::new,Collectors.toList()));
        BigDecimal subtotal=selected.stream().map(this::lineTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal shippingTotal=shippingQuotes.validateForConfirmation(draft,address,groups.keySet());
        Order order=new Order(); order.setCustomer(buyer); order.setOrderCode(orderCode);
        if("VNPAY".equals(methodCode)) order.setPaymentStatus("PENDING");
        order.setSubtotal(money(subtotal)); order.setShippingFee(shippingTotal);
        order.setGrandTotal(money(subtotal.add(shippingTotal))); orders.save(order);
        em.persist(snapshotAddress(order,address));
        List<InventoryReservationService.Reservation> reservations=new ArrayList<>();
        List<Long> shopOrderIds=new ArrayList<>();
        for(List<CartItem> lines:groups.values()) {
            ShopOrder shopOrder=new ShopOrder(); shopOrder.setOrder(order);
            shopOrder.setShop(lines.getFirst().getVariant().getProduct().getShop());
            ShopShippingQuote quote=draft.shopQuotes().get(shopOrder.getShopId());
            BigDecimal fee=quote.shippingFee();
            shopOrder.setShippingDistanceMeters(shippingQuotes.distanceSnapshot(quote.distanceMeters()));
            BigDecimal shopSubtotal=lines.stream().map(this::lineTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
            shopOrder.setSubtotal(money(shopSubtotal)); shopOrder.setShippingFee(fee);
            shopOrder.setTotalAmount(money(shopSubtotal.add(fee))); em.persist(shopOrder);
            shopOrderIds.add(shopOrder.getShopOrderId());
            for(CartItem line:lines) {
                CatalogVariant variant=line.getVariant(); OrderItem item=new OrderItem(); item.setShopOrder(shopOrder);
                item.setVariant(variant); item.setProductNameSnapshot(variant.getProduct().getName());
                item.setVariantNameSnapshot(variant.getVariantName()); item.setSkuSnapshot(variant.getSku());
                item.setUnitPrice(variant.getPrice()); item.setQuantity(line.getQuantity()); item.setTotalAmount(money(lineTotal(line)));
                em.persist(item); reservations.add(new InventoryReservationService.Reservation(item.getOrderItemId(),variant.getVariantId(),line.getQuantity()));
            }
            OrderStatusHistory history=new OrderStatusHistory(); history.setShopOrder(shopOrder); history.setStatus("PENDING");
            history.setChangedBy(buyer); history.setNote("Khách hàng đặt đơn."); em.persist(history);
        }
        inventory.reserve(reservations,customerId);
        Payment payment=new Payment(); payment.setOrder(order); payment.setPaymentMethod(method); payment.setAmount(order.getGrandTotal());
        if("VNPAY".equals(methodCode)) payment.setProvider("VNPAY");
        em.persist(payment); em.flush();
        if("VNPAY".equals(methodCode)) payment.setTransactionReference("VF"+payment.getPaymentId());
        if(selected.size()==cart.getItems().size()) cart.setStatus("CHECKED_OUT");
        else cart.getItems().removeAll(selected);
        em.flush();
        events.publishEvent(new OrderPlacedEvent(order.getOrderId(),customerId,List.copyOf(shopOrderIds)));
        return result(order,payment);
    }

    private void lockCatalog(List<CartItem> selected) {
        var variants=selected.stream().map(CartItem::getVariant).collect(Collectors.toMap(
                CatalogVariant::getVariantId,Function.identity(),(a,b)->a,TreeMap::new));
        var products=variants.values().stream().map(CatalogVariant::getProduct).collect(Collectors.toMap(
                CatalogProduct::getProductId,Function.identity(),(a,b)->a,TreeMap::new));
        var shops=products.values().stream().map(CatalogProduct::getShop).collect(Collectors.toMap(
                Shop::getShopId,Function.identity(),(a,b)->a,TreeMap::new));
        shops.values().forEach(shop -> em.refresh(shop,LockModeType.PESSIMISTIC_READ));
        shops.values().stream().map(Shop::getApplicationId).filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> em.refresh(em.getReference(SellerApplication.class,id),LockModeType.PESSIMISTIC_READ));
        products.values().forEach(product -> em.refresh(product,LockModeType.PESSIMISTIC_READ));
        variants.values().forEach(variant -> em.refresh(variant,LockModeType.PESSIMISTIC_READ));
        if(products.values().stream().anyMatch(p -> !shops.containsKey(p.getShop().getShopId()))
                || variants.values().stream().anyMatch(v -> !products.containsKey(v.getProduct().getProductId()))) {
            throw new CheckoutException("Sản phẩm vừa thay đổi cửa hàng. Vui lòng kiểm tra lại giỏ.");
        }
    }
    private BigDecimal lineTotal(CartItem item) { return item.getVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())); }
    private CartDto selectCart(CartDto cart,List<Long> itemIds) {
        if(itemIds==null) return cart;
        if(itemIds.isEmpty() || itemIds.stream().anyMatch(id -> id==null || id<=0))
            throw new CheckoutException("Vui lòng chọn ít nhất một sản phẩm hợp lệ.");
        Set<Long> selectedIds=new HashSet<>(itemIds);
        List<CartItemDto> selected=cart.getItems().stream().filter(item -> selectedIds.contains(item.getCartItemId())).toList();
        if(selected.size()!=selectedIds.size()) throw new CheckoutException("Sản phẩm đã chọn không thuộc giỏ hàng hiện tại của bạn.");
        return CartDto.builder().cartId(cart.getCartId()).userId(cart.getUserId()).status(cart.getStatus()).items(selected)
                .totalItems(selected.stream().mapToLong(CartItemDto::getQuantity).sum())
                .totalAmount(selected.stream().map(CartItemDto::getItemSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add))
                .hasUnavailableItems(selected.stream().anyMatch(item -> !item.isPurchasable())).build();
    }
    private BigDecimal money(BigDecimal amount) {
        BigDecimal normalized=amount.setScale(2,RoundingMode.UNNECESSARY);
        if(normalized.signum()<0 || normalized.precision()>18) throw new CheckoutException("Giá trị đơn vượt giới hạn cho phép.");
        return normalized;
    }
    private CheckoutResult result(Order order) {
        Payment payment=payments.findTopByOrderOrderIdAndPaymentMethodMethodCodeOrderByPaymentIdDesc(order.getOrderId(),"VNPAY")
                .orElseGet(() -> payments.findByOrderOrderIdOrderByPaymentIdAsc(order.getOrderId()).stream().findFirst()
                        .orElseThrow(() -> new CheckoutException("Không tìm thấy bản ghi thanh toán của đơn hàng.")));
        return result(order,payment);
    }
    private CheckoutResult result(Order order,Payment payment) {
        return new CheckoutResult(order.getOrderId(),order.getOrderCode(),order.getGrandTotal(),payment.getPaymentId(),
                payment.getPaymentMethod().getMethodCode());
    }
    private OrderAddress snapshotAddress(Order order,Address address) {
        OrderAddress snapshot=new OrderAddress(); snapshot.setOrder(order); snapshot.setRecipientName(address.getRecipientName());
        snapshot.setPhone(address.getPhone()); snapshot.setProvince(address.getProvince()); snapshot.setDistrict(address.getDistrict());
        snapshot.setWard(address.getWard()); snapshot.setAddressLine(address.getAddressLine());
        snapshot.setProvinceId(address.getProvinceId()); snapshot.setDistrictId(address.getDistrictId()); snapshot.setWardId(address.getWardId());
        snapshot.setFormattedAddress(address.getFormattedAddress()); snapshot.setLatitude(address.getLatitude()); snapshot.setLongitude(address.getLongitude());
        return snapshot;
    }
    private String fingerprint(CartDto cart) {
        StringBuilder canonical=new StringBuilder(String.valueOf(cart.getCartId()));
        cart.getItems().stream().sorted(Comparator.comparing(CartItemDto::getCartItemId)).forEach(item -> canonical.append('|')
                .append(item.getCartItemId()).append(':').append(item.getVariantId()).append(':').append(item.getQuantity())
                .append(':').append(item.getUnitPriceSnapshot().stripTrailingZeros().toPlainString()).append(':').append(item.getShopId()));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is required by the Java runtime",ex); }
    }
}
