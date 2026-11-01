package VietFreshHub.checkout.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.checkout.entity.CheckoutStockMovement;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.checkout.entity.CheckoutInventoryBatch;
import VietFreshHub.checkout.entity.CheckoutStockAllocation;
import VietFreshHub.order.entity.OrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class InventoryReservationService {
    public record Reservation(Long orderItemId,Long variantId,int quantity) {}
    public enum ReservationState { NONE, COMPLETE, INCONSISTENT }

    private final EntityManager em;
    @Transactional(propagation=Propagation.MANDATORY)
    public void reserve(List<Reservation> lines,Long customerId) {
        User actor=em.getReference(User.class,customerId);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for(Reservation line:lines.stream().sorted(Comparator.comparing(Reservation::variantId)
                .thenComparing(Reservation::orderItemId)).toList()) {
            if(line.orderItemId()==null || line.variantId()==null || line.quantity()<1) {
                throw new CheckoutException("Yêu cầu giữ kho không hợp lệ.");
            }
            List<CheckoutInventoryBatch> batches=em.createQuery("select b from CheckoutInventoryBatch b where b.variant.variantId=:variantId "
                            +"and b.status='ACTIVE' and (b.expiryDate is null or b.expiryDate>=:today) order by b.batchId",CheckoutInventoryBatch.class)
                    .setParameter("variantId",line.variantId()).setParameter("today",today)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            long available=batches.stream().mapToLong(b -> (long)b.getQuantityOnHand()-b.getReservedQuantity()).sum();
            if(available<line.quantity()) throw new CheckoutException("Một sản phẩm không còn đủ hàng. Vui lòng kiểm tra lại giỏ.");
            int remaining=line.quantity();
            // Lock by primary key first; then allocate the earliest-expiring sellable batches.
            for(CheckoutInventoryBatch batch:batches.stream().sorted(Comparator.comparing(CheckoutInventoryBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CheckoutInventoryBatch::getBatchId)).toList()) {
                int reserved=Math.min(remaining,batch.getQuantityOnHand()-batch.getReservedQuantity());
                if(reserved<=0) continue;
                batch.setReservedQuantity(batch.getReservedQuantity()+reserved);
                CheckoutStockAllocation allocation = new CheckoutStockAllocation();
                allocation.setOrderItem(em.getReference(OrderItem.class,line.orderItemId()));
                allocation.setBatch(batch); allocation.setQuantity(reserved); em.persist(allocation);
                CheckoutStockMovement movement=new CheckoutStockMovement();
                movement.setVariant(batch.getVariant()); movement.setBatch(batch); movement.setTransactionType("RESERVE");
                movement.setQuantity(reserved); movement.setReferenceType("ORDER_ITEM"); movement.setReferenceId(line.orderItemId());
                movement.setCreatedBy(actor); movement.setNote("Giữ kho khi đơn chờ thanh toán hoặc xử lý."); em.persist(movement);
                remaining-=reserved; if(remaining==0) break;
            }
        }
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public boolean canReserve(List<Reservation> lines) {
        for (Reservation line : lines.stream().sorted(Comparator.comparing(Reservation::variantId)
                .thenComparing(Reservation::orderItemId)).toList()) {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            List<CheckoutInventoryBatch> batches = em.createQuery("select b from CheckoutInventoryBatch b where b.variant.variantId=:variantId "
                            + "and b.status='ACTIVE' and (b.expiryDate is null or b.expiryDate>=:today) order by b.batchId", CheckoutInventoryBatch.class)
                    .setParameter("variantId", line.variantId()).setParameter("today", today)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            long available = batches.stream().mapToLong(b -> (long) b.getQuantityOnHand() - b.getReservedQuantity()).sum();
            if (available < line.quantity()) return false;
        }
        return true;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public ReservationState reservationState(List<Reservation> lines) {
        if (lines.isEmpty()) return ReservationState.INCONSISTENT;
        Map<Long, Long> expected = new HashMap<>();
        for (Reservation line : lines) expected.put(line.orderItemId(), (long) line.quantity());
        List<Long> itemIds = lines.stream().map(Reservation::orderItemId).toList();
        List<CheckoutStockAllocation> allocations = em.createQuery("select a from CheckoutStockAllocation a "
                        + "where a.orderItem.orderItemId in :ids and a.status='RESERVED' order by a.allocationId", CheckoutStockAllocation.class)
                .setParameter("ids", itemIds).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        Map<Long, Long> active = new HashMap<>();
        for (CheckoutStockAllocation allocation : allocations) {
            Long itemId = allocation.getOrderItem().getOrderItemId();
            if (allocation.getQuantity() == null || allocation.getQuantity() < 1 || !expected.containsKey(itemId)) {
                return ReservationState.INCONSISTENT;
            }
            active.merge(itemId, allocation.getQuantity().longValue(), Long::sum);
        }
        boolean any = false;
        for (Reservation line : lines) {
            long count = active.getOrDefault(line.orderItemId(), 0L);
            if (count != 0) any = true;
            if (count != 0 && count != line.quantity()) return ReservationState.INCONSISTENT;
        }
        if (!any) return ReservationState.NONE;
        return active.size() == lines.size() ? ReservationState.COMPLETE : ReservationState.INCONSISTENT;
    }
}
