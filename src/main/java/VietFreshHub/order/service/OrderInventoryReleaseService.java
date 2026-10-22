package VietFreshHub.order.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.checkout.entity.CheckoutStockMovement;
import VietFreshHub.checkout.entity.CheckoutInventoryBatch;
import VietFreshHub.checkout.entity.CheckoutStockAllocation;
import VietFreshHub.exception.CustomerOrderException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class OrderInventoryReleaseService {
    public record Line(Long orderItemId,Long variantId,int quantity) {}

    private final EntityManager em;
    @Transactional(propagation=Propagation.MANDATORY)
    public void release(List<Line> lines,Long actorId) {
        release(lines, actorId, "Giải phóng kho khi khách hàng hủy đơn.");
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void releaseReturnedGoods(List<Line> lines, Long actorId) {
        release(lines, actorId, "Giải phóng kho sau khi cửa hàng xác nhận nhận lại hàng giao thất bại.");
    }

    private void release(List<Line> lines, Long actorId, String note) {
        if(lines.isEmpty()) throw invalid();
        var ids=lines.stream().map(Line::orderItemId).toList();
        var allocations=em.createQuery("select a from CheckoutStockAllocation a where a.orderItem.orderItemId in :ids order by a.allocationId",CheckoutStockAllocation.class)
                .setParameter("ids",ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        Map<Long,Line> byItem=new HashMap<>(); lines.forEach(line -> byItem.put(line.orderItemId(),line));
        Map<Long,Long> allocatedByItem=new HashMap<>();
        record Key(Long itemId,Long batchId) {}
        Map<Key,Long> held=new HashMap<>();
        for(var allocation:allocations) {
            Line line=byItem.get(allocation.getOrderItem().getOrderItemId());
            if(allocation.getStatus()==CheckoutStockAllocation.Status.SOLD)
                throw new CustomerOrderException("Đơn đã xuất kho, hiện không thể hủy tự động.");
            if(allocation.getQuantity()==null || allocation.getQuantity()<1 ||
                    !Objects.equals(line.variantId(),allocation.getBatch().getVariant().getVariantId())) throw invalid();
            allocatedByItem.merge(line.orderItemId(),allocation.getQuantity().longValue(),Long::sum);
            held.merge(new Key(line.orderItemId(),allocation.getBatch().getBatchId()),
                    allocation.getStatus()==CheckoutStockAllocation.Status.RESERVED ? allocation.getQuantity().longValue() : 0L,Long::sum);
        }
        for(Line line:lines) if(allocatedByItem.getOrDefault(line.orderItemId(),0L)!=line.quantity()) throw invalid();
        // Allocation is the source of truth; audit must agree before any stock is released.
        var movements=em.createQuery("select m from CheckoutStockMovement m where m.referenceType='ORDER_ITEM' "
                        +"and m.referenceId in :ids and m.transactionType in ('RESERVE','RELEASE','SALE')",CheckoutStockMovement.class)
                .setParameter("ids",ids).getResultList();
        Map<Key,Long> audit=new HashMap<>();
        for(var movement:movements) {
            if("SALE".equals(movement.getTransactionType())) throw new CustomerOrderException("Đơn đã xuất kho, hiện không thể hủy tự động.");
            if(movement.getBatch()==null || movement.getQuantity()==null || movement.getQuantity()==0 ||
                    !Objects.equals(movement.getVariant().getVariantId(),byItem.get(movement.getReferenceId()).variantId())) throw invalid();
            if("RESERVE".equals(movement.getTransactionType()) && movement.getQuantity()<0) throw invalid();
            long quantity="RESERVE".equals(movement.getTransactionType()) ? movement.getQuantity() : -Math.abs((long)movement.getQuantity());
            audit.merge(new Key(movement.getReferenceId(),movement.getBatch().getBatchId()),quantity,Long::sum);
        }
        Set<Key> keys=new HashSet<>(held.keySet()); keys.addAll(audit.keySet());
        for(Key key:keys) if(!Objects.equals(held.getOrDefault(key,0L),audit.getOrDefault(key,0L))) throw invalid();
        Map<Long,CheckoutInventoryBatch> locked=new TreeMap<>();
        allocations.stream().map(a -> a.getBatch().getBatchId()).distinct().sorted().forEach(id -> {
            CheckoutInventoryBatch batch=em.find(CheckoutInventoryBatch.class,id,LockModeType.PESSIMISTIC_WRITE);
            if(batch==null) throw invalid();
            em.refresh(batch,LockModeType.PESSIMISTIC_WRITE); locked.put(id,batch);
        });
        Map<Long,Long> releasedByBatch=new HashMap<>();
        held.forEach((key,quantity) -> releasedByBatch.merge(key.batchId(),quantity,Long::sum));
        for(var entry:releasedByBatch.entrySet()) if(entry.getValue()>locked.get(entry.getKey()).getReservedQuantity()) throw invalid();
        for(var allocation:allocations) {
            if(allocation.getStatus()!=CheckoutStockAllocation.Status.RESERVED) continue;
            CheckoutInventoryBatch batch=locked.get(allocation.getBatch().getBatchId());
            if(!Objects.equals(batch.getVariant().getVariantId(),byItem.get(allocation.getOrderItem().getOrderItemId()).variantId())) throw invalid();
            batch.setReservedQuantity(batch.getReservedQuantity()-allocation.getQuantity());
            allocation.setStatus(CheckoutStockAllocation.Status.RELEASED);
            CheckoutStockMovement released=new CheckoutStockMovement(); released.setVariant(batch.getVariant()); released.setBatch(batch);
            released.setTransactionType("RELEASE"); released.setQuantity(-allocation.getQuantity()); released.setReferenceType("ORDER_ITEM");
            released.setReferenceId(allocation.getOrderItem().getOrderItemId()); released.setCreatedBy(em.getReference(User.class,actorId));
            released.setNote(note); em.persist(released);
        }
    }
    private CustomerOrderException invalid() {
        return new CustomerOrderException("Phân bổ hoặc nhật ký giữ kho không khớp. Đơn chưa được hủy để tránh ảnh hưởng đơn khác.");
    }
}
