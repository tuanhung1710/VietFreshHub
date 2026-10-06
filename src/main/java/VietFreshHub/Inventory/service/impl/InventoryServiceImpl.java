package VietFreshHub.Inventory.service.impl;
import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Shop.service.ShopService;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.enums.*;
import VietFreshHub.Product.exception.CatalogException;
import VietFreshHub.Product.repository.*;
import VietFreshHub.Inventory.entity.*;
import VietFreshHub.Inventory.enums.*;
import VietFreshHub.Inventory.repository.*;
import VietFreshHub.Inventory.dto.*;
import VietFreshHub.Inventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
import static VietFreshHub.Inventory.service.InventoryRules.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InventoryServiceImpl implements InventoryService {
    private final InventoryBatchRepository batches;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final InventoryTransactionRepository transactions;
    private final BatchMarkdownRepository markdowns;
    private final AuditEventRepository audits;
    private final StockAllocationRepository allocations;
    private final ProductCategoryRepository productCategories;
    private final CategoryRepository categories;
    private final ShopService shops;
    private final AuthService auth;
    private final InventoryJournalService journal;
    private final InventoryLockService locks;
    private final InventoryBatchCodeService batchCodes;
    private ProductVariant ownVariant(Authentication a,Long id,boolean lock) {
        Long shop=shops.getManagedShopId(a);
        ProductVariant snapshot=variants.findById(id).orElseThrow(()->new AccessDeniedException("Không có quyền truy cập SKU."));
        Product p=(lock?products.lockOwned(snapshot.getProductId(),shop):products.findById(snapshot.getProductId()).filter(x->x.getShopId().equals(shop))).orElseThrow(()->new AccessDeniedException("SKU không thuộc cửa hàng."));
        if(lock)locks.refresh(p);
        return lock?locks.refresh(variants.lockById(id).orElseThrow()):snapshot;
    }
    private InventoryBatch ownBatch(Authentication a,Long id,boolean lock) {
        InventoryBatch snapshot=batches.findById(id).orElseThrow(()->new AccessDeniedException("Không có quyền truy cập lô."));
        ownVariant(a,snapshot.getVariantId(),lock);
        return lock?locks.refresh(batches.lockById(id).orElseThrow()):snapshot;
    }
    private void code(BatchRequest f,Long variant,Long existing) {
        require(f.getBatchCode()!=null&&f.getBatchCode().matches("[A-Za-z0-9_-]{1,100}"),"Mã lô gồm chữ Latin, số, - hoặc _.");
        boolean same=existing!=null&&batches.findById(existing).map(b->b.getBatchCode().equalsIgnoreCase(f.getBatchCode())).orElse(false);
        require(same||!batches.existsByVariantIdAndBatchCodeIgnoreCase(variant,f.getBatchCode()),"Mã lô đã tồn tại trong SKU.");
        require(f.getCostPrice()==null||f.getCostPrice().signum()>=0,"Giá vốn không được âm.");
    }
    @Override @Transactional public void restock(Authentication a,Long id,BatchRequest f) {
        ProductVariant v=ownVariant(a,id,true);Product p=products.findById(v.getProductId()).orElseThrow();
        require(p.getStatus()!=ProductStatus.DELETED&&v.getStatus()!=VariantStatus.DELETED,"Không nhập thêm hàng cho sản phẩm/SKU lưu trữ.");
        dates(f.getReceivedDate(),f.getExpiryDate(),today());require(f.getQuantity()!=null&&f.getQuantity()>0,"Số lượng nhập phải dương.");String why=reason(f.getReason());
        if(f.getBatchCode()==null||f.getBatchCode().isBlank())f.setBatchCode(batchCodes.generateCode(id,f.getReceivedDate()));
        code(f,id,null);
        InventoryBatch b=new InventoryBatch();b.setVariantId(id);b.setBatchCode(f.getBatchCode());b.setQuantityOnHand(f.getQuantity());b.setReservedQuantity(0);b.setStatus(BatchStatus.ACTIVE);b.setCostPrice(f.getCostPrice());b.setExpiryDate(f.getExpiryDate());
        b.setReceivedAt(f.getReceivedDate().atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());b.setCreatedAt(now());b.setUpdatedAt(now());batches.saveAndFlush(b);
        if(v.getStatus()==VariantStatus.OUT_OF_STOCK){v.setStatus(VariantStatus.ACTIVE);v.setUpdatedAt(now());variants.save(v);}
        Long actor=auth.getCurrentUserId(a);journal.stock(id,b.getBatchId(),StockTransactionType.RESTOCK,f.getQuantity(),actor,why,"BATCH",b.getBatchId());journal.audit(actor,"RESTOCK","BATCH",b.getBatchId(),why+"; 0 → "+f.getQuantity());
    }
    @Override public BatchRequest batchForm(Authentication a,Long id) {
        InventoryBatch b=ownBatch(a,id,false);BatchRequest f=new BatchRequest();f.setBatchCode(b.getBatchCode());f.setQuantity(1);f.setCostPrice(b.getCostPrice());f.setExpiryDate(b.getExpiryDate());f.setReceivedDate(b.getReceivedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(SHOP_ZONE).toLocalDate());return f;
    }
    @Override @Transactional public void updateBatch(Authentication a,Long id,BatchRequest f) {
        InventoryBatch b=ownBatch(a,id,true);f.setBatchCode(b.getBatchCode());
        code(f,b.getVariantId(),id);String why=reason(f.getReason());
        require(f.getReceivedDate()!=null&&!f.getReceivedDate().isAfter(today()),"Ngày nhập không ở tương lai.");
        if(!Objects.equals(f.getExpiryDate(),b.getExpiryDate())) {
            require(!expired(b,today()),"Không thay hạn dùng của lô đã hết hạn.");dates(f.getReceivedDate(),f.getExpiryDate(),today());
        }
        require(f.getExpiryDate()!=null&&!f.getExpiryDate().isBefore(f.getReceivedDate()),"Hạn dùng không được trước ngày nhập.");
        String before=b.getBatchCode()+" / HSD "+b.getExpiryDate()+" / vốn "+b.getCostPrice();
        b.setBatchCode(f.getBatchCode());b.setExpiryDate(f.getExpiryDate());b.setCostPrice(f.getCostPrice());b.setReceivedAt(f.getReceivedDate().atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());b.setUpdatedAt(now());batches.save(b);
        journal.audit(auth.getCurrentUserId(a),"EDIT","BATCH",id,why+"; "+before+" → "+b.getBatchCode()+" / HSD "+b.getExpiryDate()+" / vốn "+b.getCostPrice());
    }
    @Override @Transactional public void adjust(Authentication a,Long id,StockChangeRequest f) {
        InventoryBatch b=ownBatch(a,id,true);String why=reason(f.getReason());require(f.getQuantity()!=null,"Nhập lượng điều chỉnh.");int before=b.getQuantityOnHand();int next=adjustedQuantity(b,f.getQuantity(),today());
        b.setQuantityOnHand(next);b.setUpdatedAt(now());batches.save(b);
        Long actor=auth.getCurrentUserId(a);journal.stock(b.getVariantId(),id,StockTransactionType.ADJUSTMENT,f.getQuantity(),actor,why,"BATCH",id);journal.audit(actor,"ADJUSTMENT","BATCH",id,why+"; "+before+" → "+next);
    }
    @Override @Transactional public void dispose(Authentication a,Long id,StockChangeRequest f) {
        InventoryBatch b=ownBatch(a,id,true);String why=reason(f.getReason());require(f.getQuantity()!=null,"Nhập lượng hủy.");int before=b.getQuantityOnHand();int next=disposedQuantity(b,f.getQuantity(),today());
        b.setQuantityOnHand(next);b.setUpdatedAt(now());batches.save(b);
        Long actor=auth.getCurrentUserId(a);journal.stock(b.getVariantId(),id,StockTransactionType.EXPIRED,-f.getQuantity(),actor,why,"BATCH",id);journal.audit(actor,"DISPOSE","BATCH",id,why+"; "+before+" → "+next+"; giữ đơn "+b.getReservedQuantity());
    }
    @Override @Transactional public void block(Authentication a,Long id,boolean blocked,String reason) {
        InventoryBatch b=ownBatch(a,id,true);String why=reason(reason);
        b.setStatus(blocked?BatchStatus.BLOCKED:BatchStatus.ACTIVE);b.setUpdatedAt(now());batches.save(b);
        journal.audit(auth.getCurrentUserId(a),blocked?"BLOCK":"UNBLOCK","BATCH",id,why+"; giữ nguyên lượng đã giữ đơn: "+b.getReservedQuantity());
    }
    private boolean categoryActive(Long product) {
        return productCategories.findByIdProductId(product).stream().anyMatch(pc->{
            Long id=pc.getId().getCategoryId();Set<Long> seen=new HashSet<>();
            while(id!=null) { if(!seen.add(id)||seen.size()>100)return false;Category c=categories.findById(id).orElse(null);if(c==null||c.getStatus()!=CategoryStatus.ACTIVE)return false;id=c.getParentCategoryId(); } return !seen.isEmpty();
        });
    }
    @Override @Transactional public void markdown(Authentication a,Long id,MarkdownRequest f) {
        InventoryBatch b=ownBatch(a,id,true);ProductVariant v=variants.findById(b.getVariantId()).orElseThrow();Product p=products.findById(v.getProductId()).orElseThrow();
        require(p.getStatus()==ProductStatus.ACTIVE&&p.getApprovalStatus()==ApprovalStatus.APPROVED&&v.getStatus()==VariantStatus.ACTIVE&&categoryActive(p.getProductId()),"Chỉ giảm giá SKU thuộc sản phẩm đã duyệt và đang bán.");
        InventoryRules.markdown(b,v.getPrice(),f.getPrice(),f.getEndDate(),today());String why=reason(f.getReason());Long actor=auth.getCurrentUserId(a);
        cancelExisting(id);BatchMarkdown m=new BatchMarkdown();m.setBatchId(id);m.setPrice(f.getPrice());m.setStartDate(today());m.setEndDate(f.getEndDate());m.setStatus(MarkdownStatus.ACTIVE);m.setNote(why);m.setCreatedBy(actor);m.setCreatedAt(now());markdowns.save(m);
        journal.audit(actor,"MARKDOWN","BATCH",id,why+"; giá SKU "+v.getPrice()+"; giá lô "+f.getPrice()+" đến "+f.getEndDate());
    }
    private void cancelExisting(Long batch) {
        for(BatchMarkdown m:markdowns.findByBatchIdAndStatus(batch,MarkdownStatus.ACTIVE)){m.setStatus(MarkdownStatus.CANCELLED);markdowns.save(m);}
    }
    @Override @Transactional public void cancelMarkdown(Authentication a,Long id,String value) {
        ownBatch(a,id,true);String why=reason(value);cancelExisting(id);journal.audit(auth.getCurrentUserId(a),"CANCEL_MARKDOWN","BATCH",id,why);
    }
    @Override public Page<JournalResponse> journal(Authentication a,Long variant,String type,String q,LocalDate from,LocalDate to,int page,int size) {
        Long shop=shops.getManagedShopId(a);if(variant!=null)ownVariant(a,variant,false);
        StockTransactionType transactionType=stockType(type);
        require(from==null||to==null||!from.isAfter(to),"Khoảng ngày không hợp lệ.");
        LocalDateTime start=from==null?LocalDateTime.of(1900,1,1,0,0):from.atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end=to==null?LocalDateTime.of(9998,1,1,0,0):to.plusDays(1).atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        Page<InventoryTransaction> result=transactions.search(shop,variant,transactionType,q==null?"":q.trim(),start,end,PageRequest.of(Math.max(0,page-1),List.of(5,10,20).contains(size)?size:5,Sort.by(Sort.Direction.DESC,"createdAt","transactionId")));
        return result.map(t->{ProductVariant v=variants.findById(t.getVariantId()).orElseThrow();Product p=products.findById(v.getProductId()).orElseThrow();String code=t.getBatchId()==null?"—":batches.findById(t.getBatchId()).map(InventoryBatch::getBatchCode).orElse("—");return new JournalResponse(t,p.getName(),v.getSku(),code);});
    }
    private StockTransactionType stockType(String value) {
        if(value==null||value.isBlank())return null;
        try { return StockTransactionType.valueOf(value.trim()); }
        catch(IllegalArgumentException e) { throw new CatalogException("Loại giao dịch không hợp lệ."); }
    }
    @Override public Page<AuditEvent> audit(Authentication a,int page,int size) {
        return audits.history(shops.getManagedShopId(a),PageRequest.of(Math.max(0,page-1),List.of(5,10,20).contains(size)?size:10,Sort.by(Sort.Direction.DESC,"createdAt","auditId")));
    }
    @Override public BatchResponse batch(Authentication a,Long id) {
        InventoryBatch b=ownBatch(a,id,false);BatchMarkdown m=markdowns.findByBatchIdAndStatus(id,MarkdownStatus.ACTIVE).stream().filter(x->!x.getEndDate().isBefore(today())&&!x.getStartDate().isAfter(today())).findFirst().orElse(null);
        ProductVariant v=variants.findById(b.getVariantId()).orElseThrow();Product p=products.findById(v.getProductId()).orElseThrow();
        return new BatchResponse(b,available(b,today()),expired(b,today()),b.getExpiryDate()!=null&&!expired(b,today())&&!b.getExpiryDate().isAfter(today().plusDays(7)),m,allocations.findByBatchIdOrderByAllocationIdDesc(id),p.getName(),v.getSku(),v.getUnit());
    }
}
