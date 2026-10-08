package VietFreshHub.Checkout.adapter;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Checkout.entity.CheckoutStockMovement;
import VietFreshHub.Checkout.exception.CheckoutException;
import VietFreshHub.Checkout.port.InventoryReservationPort;
import VietFreshHub.Inventory.entity.InventoryBatch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Component @RequiredArgsConstructor
public class JpaInventoryReservationAdapter implements InventoryReservationPort {
    private final EntityManager em;
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void reserve(List<Reservation> lines,Long customerId) {
        User actor=em.getReference(User.class,customerId);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for(Reservation line:lines.stream().sorted(Comparator.comparing(Reservation::variantId)
                .thenComparing(Reservation::orderItemId)).toList()) {
            if(line.orderItemId()==null || line.variantId()==null || line.quantity()<1) {
                throw new CheckoutException("Yêu cầu giữ kho không hợp lệ.");
            }
            List<InventoryBatch> batches=em.createQuery("select b from InventoryBatch b where b.variant.variantId=:variantId "
                            +"and b.expiryDate is not null and b.expiryDate>=:today order by b.batchId",InventoryBatch.class)
                    .setParameter("variantId",line.variantId()).setParameter("today",today)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            long available=batches.stream().mapToLong(b -> (long)b.getQuantityOnHand()-b.getReservedQuantity()).sum();
            if(available<line.quantity()) throw new CheckoutException("Một sản phẩm không còn đủ hàng. Vui lòng kiểm tra lại giỏ.");
            int remaining=line.quantity();
            // Lock by primary key first; then allocate the earliest-expiring sellable batches.
            for(InventoryBatch batch:batches.stream().sorted(Comparator.comparing(InventoryBatch::getExpiryDate)
                    .thenComparing(InventoryBatch::getBatchId)).toList()) {
                int reserved=Math.min(remaining,batch.getQuantityOnHand()-batch.getReservedQuantity());
                if(reserved<=0) continue;
                batch.setReservedQuantity(batch.getReservedQuantity()+reserved);
                CheckoutStockMovement movement=new CheckoutStockMovement();
                movement.setVariant(batch.getVariant()); movement.setBatch(batch); movement.setTransactionType("RESERVE");
                movement.setQuantity(reserved); movement.setReferenceType("ORDER_ITEM"); movement.setReferenceId(line.orderItemId());
                movement.setCreatedBy(actor); movement.setNote("Giữ kho khi đặt đơn COD."); em.persist(movement);
                remaining-=reserved; if(remaining==0) break;
            }
        }
    }
}
