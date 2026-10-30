package VietFreshHub.order.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.*;
import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.delivery.service.DeliveryStatusHistoryWriter;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomerOrderServiceImpl implements CustomerOrderService {
    private final ShopOrderRepository shopOrders;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final OrderDeliveryReadRepository deliveries;
    private final OrderDeliveryHistoryReadRepository deliveryHistory;
    private final OrderStatusHistoryRepository orderHistory;
    private final DeliveryAssignmentService deliveryAssignments;
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final CatalogVariantRepository variants;
    private final CatalogService catalog;
    private final CheckoutInventoryBatchRepository stock;
    private final OrderInventoryReleaseService inventory;
    private final OrderService orderService;
    private final OrderNotificationService orderNotifications;
    private final EntityManager em;

    @Override @Transactional(readOnly=true)
    public Page<OrderListRow> history(Long customerId,CustomerOrderFilterRequest filter) {
        if(filter==null || filter.getPage()<1 || filter.getPage()>10000 || filter.getKeyword()==null
                || filter.getKeyword().length()>100 || !filter.isDateRangeValid() || !filter.isDatabaseDateRangeValid())
            throw new CustomerOrderException("Bộ lọc đơn hàng không hợp lệ.");
        String statusText=filter.getStatus()==null ? "" : filter.getStatus().trim();
        if(!statusText.isEmpty() && !OrderLabels.customerStatusOptions().containsKey(statusText))
            throw new CustomerOrderException("Trạng thái lọc không hợp lệ.");
        filter.setKeyword(filter.getKeyword().trim()); filter.setStatus(statusText);
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime from=filter.getFromDate()==null ? null : LocalDateTime.ofInstant(filter.getFromDate().atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        if(from!=null && from.getYear()<1) from=LocalDateTime.of(1,1,1,0,0);
        LocalDateTime until=filter.getToDate()==null ? null : LocalDateTime.ofInstant(filter.getToDate().plusDays(1).atStartOfDay(zone).toInstant(),ZoneOffset.UTC);
        String codeKeyword=filter.getKeyword().toLowerCase(Locale.ROOT);
        if(codeKeyword.equals("vhf") || codeKeyword.startsWith("vhf-")) {
            codeKeyword="vfh"+codeKeyword.substring(3);
        }
        Long referenceId=VietFreshHub.common.BusinessCodes.parse("VFH",codeKeyword);
        String keywordPattern="%"+codeKeyword
                .replace("!","!!").replace("\\","!\\").replace("%","!%").replace("_","!_").replace("[","![")+"%";
        var page=shopOrders.findCustomerHistory(customerId,statusText,filter.getKeyword(),keywordPattern,referenceId,from,until,PageRequest.of(filter.getPage()-1,10));
        if(page.getTotalElements()==0) return Page.empty(PageRequest.of(0,10));
        if(page.getNumber()>=page.getTotalPages())
            page=shopOrders.findCustomerHistory(customerId,statusText,filter.getKeyword(),keywordPattern,referenceId,from,until,PageRequest.of(page.getTotalPages()-1,10));
        Map<Long,Boolean> codOrders=new HashMap<>();
        return page.map(row -> {
            if(!"CANCELLED".equals(row.status()) || !"UNPAID".equals(row.paymentStatus())) return row;
            boolean cod=codOrders.computeIfAbsent(row.orderId(),paymentService::isCodOrder);
            return new OrderListRow(row.shopOrderId(),row.orderId(),row.shopName(),row.status(),row.paymentStatus(),
                    row.totalAmount(),row.placedAt(),OrderLabels.customerPayment(row.status(),row.paymentStatus(),cod));
        });
    }

    @Override @Transactional(readOnly=true)
    public OrderActionsResponse shopActions(Long customerId,Long shopOrderId) {
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(shop.getOrder().getOrderId());
        var delivery=deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List.of(shopOrderId));
        String hint=cancellationHint(shop,pay,delivery);
        return new OrderActionsResponse(hint==null,hint,pay.isEmpty() ? "Chưa có phương thức thanh toán" : pay.getFirst().getPaymentMethod().getName());
    }

    @Override @Transactional
    public void cancelShop(Long customerId,Long shopOrderId,String reason) {
        if(reason!=null && reason.length()>500) throw new CustomerOrderException("Lý do hủy tối đa 500 ký tự.");
        User actor=lockCustomer(customerId);
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        Order order=shop.getOrder();
        em.refresh(order,LockModeType.PESSIMISTIC_WRITE);
        em.refresh(shop,LockModeType.PESSIMISTIC_WRITE);
        if(!customerId.equals(order.getCustomer().getUserId()) || !order.getOrderId().equals(shop.getOrder().getOrderId()))
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
        if("CANCELLED".equals(shop.getStatus())) return;
        var pay=payments.findByOrderOrderIdOrderByPaymentIdAsc(order.getOrderId());
        pay.forEach(payment -> em.refresh(payment,LockModeType.PESSIMISTIC_WRITE));
        var delivery=deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(List.of(shopOrderId));
        delivery.forEach(row -> em.refresh(row,LockModeType.PESSIMISTIC_READ));
        String hint=cancellationHint(shop,pay,delivery);
        if(hint!=null) throw new CustomerOrderException(hint);
        var lines=shop.getItems().stream().map(item -> new OrderInventoryReleaseService.Line(
                item.getOrderItemId(),item.getVariant().getVariantId(),item.getQuantity())).toList();
        inventory.release(lines,customerId);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        shop.setStatus("CANCELLED");
        shop.setCancelledAt(now);
        OrderStatusHistory history=new OrderStatusHistory();
        history.setShopOrder(shop); history.setStatus("CANCELLED"); history.setChangedBy(actor);
        history.setNote("Khách hàng hủy đơn."+(reason==null || reason.isBlank() ? "" : " Lý do: "+reason.trim()));
        em.persist(history);
        var remaining=shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(order.getOrderId()).stream()
                .filter(child -> !"CANCELLED".equals(child.getStatus())).toList();
        if(remaining.isEmpty()) {
            order.setStatus(Order.Status.CANCELLED); order.setCancelledAt(now);
            pay.stream().filter(payment -> payment.getStatus()!=Payment.Status.PAID)
                    .forEach(payment -> payment.setStatus(Payment.Status.CANCELLED));
        }
        orderService.reconcileCodPayment(order.getOrderId());
        orderNotifications.notifyCancelledByCustomer(shop);
        em.flush();
    }
    private String cancellationHint(ShopOrder shop,List<Payment> pay,List<OrderDeliveryView> delivery) {
        Order order=shop.getOrder();
        if("CANCELLED".equals(shop.getStatus())) return "Đơn cửa hàng đã được hủy.";
        if(!"PENDING".equals(shop.getStatus()) || shop.getConfirmedAt()!=null)
            return "Đơn hàng đã được cửa hàng xác nhận nên không thể hủy.";
        if(delivery.stream().anyMatch(d -> !Set.of("UNASSIGNED","ASSIGNED","ACCEPTED","CANCELLED").contains(d.getStatus())))
            return "Đơn đã được lấy hoặc đang giao, không thể hủy.";
        String paymentStatus=shop.getPaymentStatus();
        boolean paidOnline="PAID".equals(paymentStatus) && order.getGrandTotal()!=null
                && paymentService.getPaidOnlineOrderAmount(order.getOrderId())
                .filter(amount -> amount.compareTo(order.getGrandTotal())==0).isPresent();
        if(!paidOnline && ("PAID".equals(paymentStatus) || "PARTIALLY_REFUNDED".equals(paymentStatus) || "REFUNDED".equals(paymentStatus)
                || pay.stream().anyMatch(p -> !"COD".equals(p.getPaymentMethod().getMethodCode()) || p.getPaidAt()!=null
                || !Set.of(Payment.Status.PENDING,Payment.Status.FAILED,Payment.Status.CANCELLED).contains(p.getStatus()))))
            return "Đơn có giao dịch cần đối soát/hoàn tiền. Vui lòng liên hệ hỗ trợ để hủy an toàn.";
        if(order.getDiscountTotal().signum()>0 || shop.getDiscountTotal().signum()>0)
            return "Đơn có ưu đãi cần đối soát trước khi hủy. Vui lòng liên hệ hỗ trợ.";
        return null;
    }

    @Override @Transactional
    public ReorderResult reorderShop(Long customerId,Long shopOrderId) {
        User buyer=lockCustomer(customerId);
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var lines=shop.getItems();
        Map<Long,Long> quantities=new TreeMap<>(); Map<Long,OrderItem> original=new HashMap<>();
        lines.forEach(line -> { Long id=line.getVariant().getVariantId(); quantities.merge(id,line.getQuantity().longValue(),Long::sum); original.putIfAbsent(id,line); });
        Cart cart=carts.findByUserUserIdAndStatus(customerId,"ACTIVE").orElse(null);
        List<ReorderResult.SkippedItem> skipped=new ArrayList<>(); int added=0; long units=0;
        for(var entry:quantities.entrySet()) {
            var past=original.get(entry.getKey()); var variant=variants.findById(entry.getKey()).orElse(null);
            String rejection=null; CartItem existing=cart==null ? null : cartItems.findByCartCartIdAndVariantVariantId(cart.getCartId(),entry.getKey()).orElse(null);
            long desired=entry.getValue()+(existing==null ? 0L : existing.getQuantity());
            if(variant==null || !catalog.isPurchasable(variant)) rejection="Sản phẩm hoặc cửa hàng hiện không nhận đơn.";
            else if(entry.getValue()<1 || desired>Integer.MAX_VALUE || desired>stock.getAvailableStockByVariantId(entry.getKey())) rejection="Không đủ hàng cho số lượng mua lại và số lượng đang có trong giỏ.";
            if(rejection!=null) { skipped.add(new ReorderResult.SkippedItem(past.getProductNameSnapshot(),past.getVariantNameSnapshot(),rejection)); continue; }
            if(cart==null) { cart=new Cart(); cart.setUser(buyer); carts.save(cart); }
            if(existing==null) { existing=new CartItem(); existing.setCart(cart); existing.setVariant(variant); }
            existing.setQuantity((int)desired); existing.setUnitPriceSnapshot(variant.getPrice()); cartItems.save(existing);
            added++; units+=entry.getValue();
        }
        return new ReorderResult(added,units,List.copyOf(skipped));
    }

    @Override @Transactional(readOnly=true)
    public OrderTrackingResponse shopTracking(Long customerId,Long shopOrderId) {
        var shop=shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId,customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        return tracking(customerId,shop.getOrder(),List.of(shop));
    }

    private OrderTrackingResponse tracking(Long customerId,Order order,List<ShopOrder> children) {
        Long orderId=order.getOrderId();
        var shopOrderIds=children.stream().map(ShopOrder::getShopOrderId).toList();
        var rows=shopOrderIds.isEmpty() ? List.<OrderDeliveryView>of() : deliveries.findByShopOrderShopOrderIdInOrderByDeliveryIdAsc(shopOrderIds);
        var history=rows.isEmpty() ? List.<OrderDeliveryHistoryView>of() : deliveryHistory.findByDeliveryDeliveryIdInOrderByCreatedAtAscHistoryIdAsc(rows.stream().map(OrderDeliveryView::getDeliveryId).toList());
        var statuses=shopOrderIds.isEmpty() ? List.<OrderStatusHistory>of() : orderHistory.findByShopOrderShopOrderIdInOrderByCreatedAtAscHistoryIdAsc(shopOrderIds);
        var currentStatuses=shopOrderIds.isEmpty() ? List.<CustomerShopOrderStatus>of() : shopOrders.findCustomerStatuses(customerId,shopOrderIds);
        var staff=rows.isEmpty() ? List.<DeliveryTrackingStaff>of() : deliveryAssignments.getTrackingStaff(customerId,orderId);
        var groups=children.stream().map(child -> {
            OrderDeliveryView delivery=rows.stream().filter(d -> d.getShopOrder().getShopOrderId().equals(child.getShopOrderId())).findFirst().orElse(null);
            var shopHistory=statuses.stream().filter(h -> h.getShopOrder().getShopOrderId().equals(child.getShopOrderId())).toList();
            var deliveryEvents=history.stream().filter(h -> delivery!=null && h.getDelivery().getDeliveryId().equals(delivery.getDeliveryId())).toList();
            List<OrderTrackingResponse.Event> timeline=new ArrayList<>();
            shopHistory.stream()
                    .filter(h -> !duplicatesDeliveryEvent(h,delivery,deliveryEvents,shopHistory))
                    .forEach(h -> timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(h.getCreatedAt()),OrderLabels.customerStatus(h.getStatus()),h.getNote(),"ORDER")));
            if(delivery!=null) {
                for(var event:deliveryEvents) {
                    String staffName=event.getChangedBy()==null ? null : trackingStaffName(staff,delivery.getDeliveryId(),event.getChangedBy());
                    boolean goodsReturned="FAILED".equals(event.getStatus()) && event.getChangedBy()!=null
                            && event.getNote()!=null && event.getNote().startsWith(DeliveryStatusHistoryWriter.GOODS_RETURN_NOTE_PREFIX);
                    if(goodsReturned) continue;
                    String eventLabel=trackingDeliveryLabel(event.getStatus(),staffName);
                    if(eventLabel!=null) timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(event.getCreatedAt()),eventLabel,null,"DELIVERY"));
                }
            }
            addMissingConfirmation(child,shopHistory,timeline);
            timeline.sort(Comparator.comparing(OrderTrackingResponse.Event::occurredAt,Comparator.nullsLast(Comparator.naturalOrder())));
            var currentStatus=currentStatuses.stream().filter(s -> s.shopOrderId().equals(child.getShopOrderId())).findFirst().orElse(null);
            String deliveryStatusCode=currentStatus==null ? null : currentStatus.status();
            if(delivery!=null && "COMPLETED".equals(child.getStatus()) && "DELIVERED".equals(delivery.getStatus()))
                deliveryStatusCode=delivery.getStatus();
            String label=OrderLabels.customerStatus(deliveryStatusCode);
            if(delivery!=null && "ACCEPTED".equals(deliveryStatusCode)) {
                String staffName=trackingStaffName(staff,delivery.getDeliveryId(),null);
                label=trackingDeliveryLabel(deliveryStatusCode,staffName);
            }
            return new OrderTrackingResponse.ShopTracking(child.getShopOrderId(),child.getShop().getShopName(),OrderLabels.customerStatus(child.getStatus()),
                    child.getStatus(),label,deliveryStatusCode,List.copyOf(timeline));
        }).toList();
        var child=children.getFirst();
        return new OrderTrackingResponse(orderId,child.getShopOrderCode(),OrderLabels.customerStatus(child.getStatus()),child.getStatus(),groups);
    }
    private boolean duplicatesDeliveryEvent(OrderStatusHistory event,OrderDeliveryView delivery,
                                            List<OrderDeliveryHistoryView> deliveryEvents,List<OrderStatusHistory> shopHistory) {
        String deliveryStatus=switch(event.getStatus()) {
            case "OUT_FOR_DELIVERY" -> "OUT_FOR_DELIVERY";
            case "COMPLETED" -> "DELIVERED";
            default -> null;
        };
        if(deliveryStatus==null || delivery==null) return false;
        for(var deliveryEvent:deliveryEvents) {
            if(!delivery.getDeliveryId().equals(deliveryEvent.getDelivery().getDeliveryId())
                    || !deliveryStatus.equals(deliveryEvent.getStatus())) continue;
            if("COMPLETED".equals(event.getStatus())) return true;
            if(shopHistory.stream().filter(row -> "OUT_FOR_DELIVERY".equals(row.getStatus())).count()==1
                    && deliveryEvents.stream().filter(row -> "OUT_FOR_DELIVERY".equals(row.getStatus())).count()==1) return true;
            if(event.getCreatedAt()!=null && event.getCreatedAt().equals(deliveryEvent.getCreatedAt())) return true;
        }
        return false;
    }
    private void addMissingConfirmation(ShopOrder child,List<OrderStatusHistory> history,List<OrderTrackingResponse.Event> timeline) {
        if(child.getConfirmedAt()==null || history.stream().anyMatch(entry -> "CONFIRMED".equals(entry.getStatus()))) return;
        timeline.add(new OrderTrackingResponse.Event(OrderLabels.local(child.getConfirmedAt()),
                OrderLabels.customerStatus("CONFIRMED"),null,"ORDER"));
    }
    private String trackingStaffName(List<DeliveryTrackingStaff> staff,Long deliveryId,Long staffId) {
        return staff.stream().filter(s -> s.deliveryId().equals(deliveryId) && (staffId==null || s.staffId().equals(staffId)))
                .findFirst().map(DeliveryTrackingStaff::staffName).orElse(null);
    }
    private String trackingDeliveryLabel(String status,String staffName) {
        return switch(status) {
            case "ACCEPTED" -> staffName==null || staffName.isBlank() ? "Nhân viên đã nhận nhiệm vụ" : "Nhân viên "+staffName.strip()+" đã nhận nhiệm vụ";
            case "PICKED_UP" -> "Nhân viên giao hàng đã lấy hàng";
            case "OUT_FOR_DELIVERY" -> "Đang giao hàng";
            case "DELIVERED" -> "Giao hàng thành công";
            case "FAILED" -> "Giao hàng thất bại";
            default -> OrderLabels.delivery(status);
        };
    }
    private User lockCustomer(Long customerId) {
        User user=users.findByUserIdForUpdate(customerId).orElseThrow(() -> new CustomerOrderException("Tài khoản không hợp lệ."));
        em.refresh(user,LockModeType.PESSIMISTIC_WRITE); if(!"ACTIVE".equals(user.getStatus())) throw new CustomerOrderException("Tài khoản không còn được phép thao tác đơn hàng."); return user;
    }
}
