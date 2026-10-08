package VietFreshHub.Order.adapter;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Checkout.entity.CheckoutStockMovement;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Order.exception.CustomerOrderException;
import VietFreshHub.Order.port.OrderInventoryReleasePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class JpaOrderInventoryReleaseAdapter implements OrderInventoryReleasePort {
    private final EntityManager em;
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void release(List<Line> lines,Long actorId) {
        if(lines.isEmpty()) throw new CustomerOrderException("Đơn hàng không có sản phẩm để đối soát kho.");
        var ids=lines.stream().map(Line::orderItemId).toList();
        var movements=em.createQuery("select m from CheckoutStockMovement m where m.referenceType='ORDER_ITEM' "
                        +"and m.referenceId in :ids and m.transactionType in ('RESERVE','RELEASE','SALE') order by m.transactionId",CheckoutStockMovement.class)
                .setParameter("ids",ids).getResultList();
        record Allocation(Long itemId,Long batchId) {}
        Map<Allocation,Long> outstanding=new HashMap<>();
        Map<Long,Long> reservedByItem=new HashMap<>();
        Map<Long,Long> variantsByItem=new HashMap<>(); lines.forEach(line -> variantsByItem.put(line.orderItemId(),line.variantId()));
        for(var movement:movements) {
            if("SALE".equals(movement.getTransactionType())) throw new CustomerOrderException("Đơn đã xuất kho, hiện không thể hủy tự động.");
            if(movement.getBatch()==null || movement.getQuantity()<=0
                    || !Objects.equals(movement.getVariant().getVariantId(),variantsByItem.get(movement.getReferenceId())))
                throw new CustomerOrderException("Thông tin giữ kho chưa đủ để hủy an toàn. Vui lòng liên hệ hỗ trợ.");
            Allocation key=new Allocation(movement.getReferenceId(),movement.getBatch().getBatchId());
            boolean reserve="RESERVE".equals(movement.getTransactionType());
            outstanding.merge(key,(reserve?1L:-1L)*movement.getQuantity(),Long::sum);
            if(reserve) reservedByItem.merge(movement.getReferenceId(),movement.getQuantity().longValue(),Long::sum);
        }
        for(Line line:lines) if(reservedByItem.getOrDefault(line.orderItemId(),0L)!=line.quantity())
            throw new CustomerOrderException("Không tìm thấy đủ lượng giữ kho của đơn. Đơn chưa được hủy.");
        if(outstanding.values().stream().anyMatch(q -> q<0)) throw new CustomerOrderException("Nhật ký kho không nhất quán. Đơn chưa được hủy.");
        Map<Long,InventoryBatch> locked=new TreeMap<>();
        outstanding.keySet().stream().map(Allocation::batchId).distinct().sorted().forEach(id -> {
            InventoryBatch batch=em.find(InventoryBatch.class,id,LockModeType.PESSIMISTIC_WRITE);
            if(batch==null) throw new CustomerOrderException("Lô hàng của đơn không còn tồn tại.");
            em.refresh(batch,LockModeType.PESSIMISTIC_WRITE); locked.put(id,batch);
        });
        for(var entry:outstanding.entrySet().stream().sorted(Comparator.comparing((Map.Entry<Allocation,Long> e)->e.getKey().batchId())
                .thenComparing(e -> e.getKey().itemId())).toList()) {
            if(entry.getValue()==0) continue;
            InventoryBatch batch=locked.get(entry.getKey().batchId());
            long quantity=entry.getValue();
            if(!Objects.equals(batch.getVariant().getVariantId(),variantsByItem.get(entry.getKey().itemId())) || quantity>batch.getReservedQuantity())
                throw new CustomerOrderException("Lượng giữ kho không khớp. Đơn chưa được hủy để tránh ảnh hưởng đơn khác.");
            batch.setReservedQuantity(batch.getReservedQuantity()-(int)quantity);
            CheckoutStockMovement released=new CheckoutStockMovement(); released.setVariant(batch.getVariant()); released.setBatch(batch);
            released.setTransactionType("RELEASE"); released.setQuantity((int)quantity); released.setReferenceType("ORDER_ITEM");
            released.setReferenceId(entry.getKey().itemId()); released.setCreatedBy(em.getReference(User.class,actorId));
            released.setNote("Giải phóng kho khi khách hàng hủy đơn."); em.persist(released);
        }
    }
}
