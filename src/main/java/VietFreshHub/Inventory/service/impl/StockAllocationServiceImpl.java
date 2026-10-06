package VietFreshHub.Inventory.service.impl;
import VietFreshHub.Inventory.service.*;
import VietFreshHub.Inventory.repository.*;
import VietFreshHub.Inventory.entity.*;
import VietFreshHub.Inventory.enums.*;
import VietFreshHub.Product.repository.*;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.enums.*;
import VietFreshHub.Order.repository.ShopOrderRepository;
import VietFreshHub.Order.entity.ShopOrder;
import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.math.BigDecimal;
import static VietFreshHub.Inventory.service.InventoryRules.*;

@Service @RequiredArgsConstructor @Transactional
public class StockAllocationServiceImpl implements StockAllocationService {
    private final InventoryOrderLineRepository lines;
    private final ShopOrderRepository orders;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final InventoryBatchRepository batches;
    private final StockAllocationRepository allocations;
    private final BatchMarkdownRepository markdowns;
    private final ProductCategoryRepository links;
    private final CategoryRepository categories;
    private final ShopService shops;
    private final AuthService auth;
    private final InventoryJournalService journal;
    private final InventoryLockService locks;
    private InventoryOrderLine own(Authentication a,Long id) {
        Long shop=shops.getManagedShopId(a);
        InventoryOrderLine snapshot=lines.findById(id).orElseThrow(()->new AccessDeniedException("Không có quyền truy cập dòng đơn."));
        ShopOrder order=locks.refresh(orders.findForUpdateByShopOrderIdAndShopId(snapshot.getShopOrderId(),shop).orElseThrow(()->new AccessDeniedException("Dòng đơn thuộc cửa hàng khác.")));
        InventoryOrderLine l=locks.refresh(lines.lockById(id).orElseThrow());
        require(l.getShopOrderId().equals(order.getShopOrderId()),"Dòng đơn đã chuyển đơn; tải lại và thử lại.");
        ProductVariant v=variants.findById(l.getVariantId()).orElseThrow();
        locks.refresh(products.lockOwned(v.getProductId(),shop).orElseThrow(()->new AccessDeniedException("SKU của đơn không thuộc cửa hàng.")));
        locks.refresh(variants.lockById(l.getVariantId()).orElseThrow());
        return l;
    }
    private boolean activeCategory(Long product) {
        return links.findByIdProductId(product).stream().anyMatch(link->{
            Long id=link.getId().getCategoryId();Set<Long> seen=new HashSet<>();
            while(id!=null){if(!seen.add(id)||seen.size()>100)return false;Category c=categories.findById(id).orElse(null);if(c==null||c.getStatus()!=CategoryStatus.ACTIVE)return false;id=c.getParentCategoryId();}
            return !seen.isEmpty();
        });
    }
    @Override public List<Long> reserve(Authentication a,Long id) {
        InventoryOrderLine line=own(a,id);
        require(List.of("PENDING","CONFIRMED","PREPARING").contains(orders.findById(line.getShopOrderId()).orElseThrow().getStatus()),"Không giữ thêm kho cho đơn đã giao/hủy.");
        List<StockAllocation> existing=allocations.findByOrderItemIdOrderByAllocationId(id);
        if(!existing.isEmpty()){
            require(existing.stream().allMatch(x->x.getStatus()==AllocationStatus.RESERVED)&&existing.stream().mapToLong(StockAllocation::getQuantity).sum()==line.getQuantity(),"Dòng đơn đã được bán/trả giữ hoặc thay số lượng; không giữ lại tự động.");
            return existing.stream().map(StockAllocation::getAllocationId).toList();
        }
        ProductVariant v=variants.findById(line.getVariantId()).orElseThrow();Product p=products.findById(v.getProductId()).orElseThrow();
        require(p.getStatus()==ProductStatus.ACTIVE&&p.getApprovalStatus()==ApprovalStatus.APPROVED&&v.getStatus()==VariantStatus.ACTIVE&&activeCategory(p.getProductId()),"Sản phẩm/SKU không còn được bán.");
        List<InventoryBatch> candidates=batches.findByVariantIdOrderByExpiryDateAscBatchIdAsc(v.getVariantId()).stream().map(b->locks.refresh(batches.lockById(b.getBatchId()).orElseThrow())).filter(b->available(b,today())>0).sorted(Comparator.comparing(InventoryBatch::getExpiryDate,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(InventoryBatch::getBatchId)).toList();
        List<InventoryBatch> lockedCandidates=candidates;
        List<Long> batchIds=candidates.stream().map(InventoryBatch::getBatchId).toList();
        List<BatchMarkdown> offers=batchIds.isEmpty()?List.of():markdowns.findByBatchIdInAndStatusOrderByMarkdownIdDesc(batchIds,MarkdownStatus.ACTIVE);
        BigDecimal minimum=offers.stream().filter(m->!m.getStartDate().isAfter(today())&&!m.getEndDate().isBefore(today())&&m.getPrice().compareTo(v.getPrice())<0&&lockedCandidates.stream().anyMatch(b->b.getBatchId().equals(m.getBatchId())&&b.getExpiryDate()!=null&&!b.getExpiryDate().isAfter(today().plusDays(7)))).map(BatchMarkdown::getPrice).min(BigDecimal::compareTo).orElse(v.getPrice());
        require(line.getUnitPrice().compareTo(minimum)==0,"Giá đặt hàng đã thay đổi; checkout cần tính lại giá tại server.");
        if(minimum.compareTo(v.getPrice())<0)candidates=candidates.stream().filter(b->offers.stream().anyMatch(m->m.getBatchId().equals(b.getBatchId())&&m.getPrice().compareTo(minimum)==0&&!m.getStartDate().isAfter(today())&&!m.getEndDate().isBefore(today())&&b.getExpiryDate()!=null&&!b.getExpiryDate().isAfter(today().plusDays(7)))).toList();
        require(line.getQuantity()!=null&&line.getQuantity()>0,"Số lượng dòng đơn phải dương.");
        require(candidates.stream().mapToLong(b->available(b,today())).sum()>=line.getQuantity(),"Không đủ tồn bán được tại mức giá đặt hàng.");
        int needed=line.getQuantity();Long actor=auth.getCurrentUserId(a);List<Long> result=new ArrayList<>();
        for(InventoryBatch b:candidates) {
            int qty=Math.min(needed,available(b,today()));if(qty==0)continue;
            b.setReservedQuantity(b.getReservedQuantity()+qty);b.setUpdatedAt(now());batches.save(b);
            StockAllocation allocation=new StockAllocation();allocation.setOrderItemId(id);allocation.setBatchId(b.getBatchId());allocation.setQuantity(qty);allocation.setStatus(AllocationStatus.RESERVED);allocations.saveAndFlush(allocation);result.add(allocation.getAllocationId());
            journal.stock(v.getVariantId(),b.getBatchId(),StockTransactionType.RESERVE,qty,actor,"Giữ kho theo FEFO.","ORDER_ITEM",id);journal.audit(actor,"RESERVE","BATCH",b.getBatchId(),"Dòng đơn #"+id+" giữ "+qty);
            needed-=qty;if(needed==0)break;
        }
        return result;
    }
    @Override public void release(Authentication a,Long id,String value) {
        own(a,id);String why=reason(value);Long actor=auth.getCurrentUserId(a);
        for(StockAllocation x:allocations.findByOrderItemIdOrderByAllocationId(id)) {
            if(x.getStatus()!=AllocationStatus.RESERVED)continue;
            InventoryBatch b=locks.refresh(batches.lockById(x.getBatchId()).orElseThrow());require(b.getReservedQuantity()>=x.getQuantity(),"Lượng giữ đơn của lô không khớp phân bổ.");
            b.setReservedQuantity(b.getReservedQuantity()-x.getQuantity());b.setUpdatedAt(now());batches.save(b);x.setStatus(AllocationStatus.RELEASED);allocations.save(x);
            journal.stock(b.getVariantId(),b.getBatchId(),StockTransactionType.RELEASE,-x.getQuantity(),actor,why,"ORDER_ITEM",id);journal.audit(actor,"RELEASE","BATCH",b.getBatchId(),why+"; trả giữ dòng đơn #"+id+": "+x.getQuantity());
        }
    }
    @Override public void sell(Authentication a,Long id) {
        InventoryOrderLine line=own(a,id);Long actor=auth.getCurrentUserId(a);List<StockAllocation> rows=allocations.findByOrderItemIdOrderByAllocationId(id);
        require(List.of("READY_FOR_DELIVERY","OUT_FOR_DELIVERY","COMPLETED").contains(orders.findById(line.getShopOrderId()).orElseThrow().getStatus()),"Chỉ ghi xuất bán khi đơn đã sẵn sàng giao, đang giao hoặc hoàn tất.");
        require(!rows.isEmpty()&&rows.stream().noneMatch(x->x.getStatus()==AllocationStatus.RELEASED)&&rows.stream().mapToLong(StockAllocation::getQuantity).sum()==line.getQuantity(),"Dòng đơn phải được giữ đủ kho trước khi ghi bán.");
        if(rows.stream().allMatch(x->x.getStatus()==AllocationStatus.SOLD))return;
        require(rows.stream().allMatch(x->x.getStatus()==AllocationStatus.RESERVED),"Phân bổ đang ở trạng thái không nhất quán.");
        for(StockAllocation x:rows) {
            InventoryBatch b=locks.refresh(batches.lockById(x.getBatchId()).orElseThrow());
            require(!expired(b,today())&&b.getStatus()==BatchStatus.ACTIVE,"Lô giữ cho đơn đã hết hạn/bị khóa; cần xử lý đơn.");
            require(b.getReservedQuantity()>=x.getQuantity()&&b.getQuantityOnHand()>=x.getQuantity(),"Tồn lô không khớp phân bổ.");
            b.setQuantityOnHand(b.getQuantityOnHand()-x.getQuantity());b.setReservedQuantity(b.getReservedQuantity()-x.getQuantity());b.setUpdatedAt(now());batches.save(b);x.setStatus(AllocationStatus.SOLD);allocations.save(x);
            journal.stock(b.getVariantId(),b.getBatchId(),StockTransactionType.SALE,-x.getQuantity(),actor,"Xuất bán lượng đã giữ.","ORDER_ITEM",id);journal.audit(actor,"SALE","BATCH",b.getBatchId(),"Xuất dòng đơn #"+id+": "+x.getQuantity());
        }
    }
}
