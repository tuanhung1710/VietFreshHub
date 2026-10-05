package VietFreshHub.Product.service.impl;

import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Shop.service.ShopService;
import VietFreshHub.Product.dto.*;
import VietFreshHub.Product.dto.ProductResponse.VariantResponse;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.enums.*;
import VietFreshHub.Product.repository.*;
import VietFreshHub.Product.service.ProductService;
import VietFreshHub.Inventory.dto.BatchResponse;
import VietFreshHub.Inventory.entity.*;
import VietFreshHub.Inventory.enums.*;
import VietFreshHub.Inventory.repository.*;
import VietFreshHub.Inventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import static VietFreshHub.Inventory.service.InventoryRules.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ProductServiceImpl implements ProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryRepository categories;
    private final ProductCategoryRepository productCategories;
    private final ProductImageRepository images;
    private final PriceHistoryRepository prices;
    private final InventoryBatchRepository batches;
    private final BatchMarkdownRepository markdowns;
    private final InventoryTransactionRepository transactions;
    private final StockAllocationRepository allocations;
    private final InventoryJournalService journal;
    private final InventoryLockService locks;
    private final InventoryBatchCodeService batchCodes;
    private final ShopService shopService;
    private final AuthService authService;
    private final UserRepository users;
    public static final List<String> UNITS=List.of("kg","hộp","túi","quả","chùm");

    @Override
    public Page<ProductResponse> search(Authentication auth,String q,Long category,String status,boolean archived,String stock,int page,int size) {
        return search(auth,q,category,status,archived,stock,"","",null,null,page,size);
    }
    @Override
    public Page<ProductResponse> search(Authentication auth,String q,Long category,String status,boolean archived,String stock,
        String business,String approval,LocalDate createdFrom,LocalDate createdTo,int page,int size) {
        Long shop=shopService.getManagedShopId(auth);
        String statusFilter=status==null?"":status.trim(),approvalFilter=approval==null?"":approval.trim();
        String stockFilter=stock==null?"":stock.trim(),businessFilter=business==null?"":business.trim();
        ProductStatus productStatus=Arrays.stream(ProductStatus.values()).filter(s->s.name().equals(statusFilter)).findFirst().orElse(null);
        ApprovalStatus statusApproval=Arrays.stream(ApprovalStatus.values()).filter(s->s.name().equals(statusFilter)).findFirst().orElse(null);
        ApprovalStatus approvalStatus=Arrays.stream(ApprovalStatus.values()).filter(s->s.name().equals(approvalFilter)).findFirst().orElse(null);
        require(statusFilter.isEmpty()||productStatus!=null||statusApproval!=null,"Bộ lọc trạng thái không hợp lệ.");
        require(List.of("","low","near","expired","archived").contains(stockFilter),"Bộ lọc kho không hợp lệ.");
        require(List.of("","published","unpublished").contains(businessFilter),"Bộ lọc kinh doanh không hợp lệ.");
        require(approvalFilter.isEmpty()||approvalStatus!=null,"Bộ lọc kiểm duyệt không hợp lệ.");
        require(createdFrom==null||createdTo==null||!createdFrom.isAfter(createdTo),"Ngày bắt đầu không được sau ngày kết thúc.");
        LocalDateTime start=createdFrom==null?null:createdFrom.atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end=createdTo==null?null:createdTo.plusDays(1).atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        List<Long> activeCategories=businessFilter.isEmpty()?List.of():categories().stream().map(Category::getCategoryId).toList();
        int limit=List.of(5,10,20).contains(size)?size:10;
        Page<Product> result=products.search(shop,q==null?"":q.trim(),category,productStatus,statusApproval,
            archived||"archived".equals(stockFilter)||productStatus==ProductStatus.DELETED,stockFilter,
            businessFilter,approvalStatus,start,end,activeCategories,today(),today().plusDays(7),PageRequest.of(Math.max(0,page-1),limit,Sort.by(Sort.Direction.DESC,"productId")));
        List<Long> ids=result.getContent().stream().map(Product::getProductId).toList();
        List<ProductVariant> vs=ids.isEmpty()?List.of():variants.findByProductIdInOrderByVariantId(ids);
        List<Long> vids=vs.stream().map(ProductVariant::getVariantId).toList();
        List<InventoryBatch> bs=vids.isEmpty()?List.of():batches.findByVariantIdInOrderByExpiryDateAscBatchIdAsc(vids);
        return result.map(p->response(p,vs.stream().filter(v->v.getProductId().equals(p.getProductId())).toList(),bs,false));
    }
    @Override public CatalogSummary catalogSummary(Authentication auth) {
        Long shop=shopService.getManagedShopId(auth);
        return new CatalogSummary(products.countByShopIdAndStatusNot(shop,ProductStatus.DELETED),variants.countVisibleInShop(shop));
    }
    @Override public ProductResponse get(Authentication auth,Long id) {
        Product p=owned(auth,id,false);
        List<ProductVariant> vs=variants.findByProductIdOrderByVariantId(id);
        List<Long> ids=vs.stream().map(ProductVariant::getVariantId).toList();
        return response(p,vs,ids.isEmpty()?List.of():batches.findByVariantIdInOrderByExpiryDateAscBatchIdAsc(ids),true);
    }
    private ProductResponse response(Product p,List<ProductVariant> vs,List<InventoryBatch> bs,boolean details) {
        LocalDate date=today();
        List<Long> bids=bs.stream().map(InventoryBatch::getBatchId).toList();
        List<BatchMarkdown> offers=bids.isEmpty()?List.of():markdowns.findByBatchIdInAndStatusOrderByMarkdownIdDesc(bids,MarkdownStatus.ACTIVE);
        List<VariantResponse> rows=new ArrayList<>();
        for(ProductVariant v:vs) {
            List<BatchResponse> br=new ArrayList<>();
            for(InventoryBatch b:bs) if(b.getVariantId().equals(v.getVariantId())) {
                BatchMarkdown m=offers.stream().filter(o->o.getBatchId().equals(b.getBatchId()) && !o.getStartDate().isAfter(date) && !o.getEndDate().isBefore(date)
                    && available(b,date)>0 && b.getExpiryDate()!=null && !b.getExpiryDate().isAfter(date.plusDays(7)) && o.getPrice().compareTo(v.getPrice())<0).findFirst().orElse(null);
                br.add(new BatchResponse(b,available(b,date),expired(b,date),b.getExpiryDate()!=null&&!expired(b,date)&&!b.getExpiryDate().isAfter(date.plusDays(7)),m,details?allocations.findByBatchIdOrderByAllocationIdDesc(b.getBatchId()):List.of(),p.getName(),v.getSku(),v.getUnit()));
            }
            long physical=br.stream().mapToLong(b->b.batch().getQuantityOnHand()).sum();
            long reserved=br.stream().mapToLong(b->b.batch().getReservedQuantity()).sum();
            long free=br.stream().mapToLong(BatchResponse::available).sum();
            boolean sellable=p.getStatus()==ProductStatus.ACTIVE && p.getApprovalStatus()==ApprovalStatus.APPROVED && v.getStatus()==VariantStatus.ACTIVE && categoryActive(p.getProductId());
            BigDecimal offer=sellable?br.stream().filter(b->b.markdown()!=null).map(b->b.markdown().getPrice()).min(BigDecimal::compareTo).orElse(null):null;
            long offerQuantity=offer==null?0:br.stream().filter(b->b.markdown()!=null&&b.markdown().getPrice().compareTo(offer)==0).mapToLong(BatchResponse::available).sum();
            rows.add(new VariantResponse(v,physical,reserved,free,physical-reserved-free,br,offer,offerQuantity));
        }
        List<String> categoryNames=productCategories.findByIdProductId(p.getProductId()).stream().map(pc->categories.findById(pc.getId().getCategoryId()).map(Category::getName).orElse("Danh mục không còn tồn tại")).toList();
        String category=String.join(", ",categoryNames);
        String image=images.findByProductIdOrderBySortOrderAscImageIdAsc(p.getProductId()).stream().filter(i->i.getVariantId()==null).map(ProductImage::getImageUrl).findFirst().orElse(null);
        String approvedByName=p.getApprovedBy()==null?null:users.findById(p.getApprovedBy()).map(u->u.getFullName()).orElse(null);
        boolean published=p.getStatus()==ProductStatus.ACTIVE&&p.getApprovalStatus()==ApprovalStatus.APPROVED&&categoryActive(p.getProductId())
            && vs.stream().anyMatch(v->v.getStatus()==VariantStatus.ACTIVE||v.getStatus()==VariantStatus.OUT_OF_STOCK)
            && vs.stream().filter(v->v.getStatus()!=VariantStatus.DELETED).allMatch(v->v.getPrice()!=null&&v.getPrice().compareTo(new BigDecimal("10000"))>=0&&v.getPrice().stripTrailingZeros().scale()<=0);
        return new ProductResponse(p,image,category,rows,categoryNames,approvedByName,published);
    }
    @Override public List<Category> categories() {
        return categories.findAllByOrderByNameAsc().stream().filter(c->activeChain(c.getCategoryId())).toList();
    }
    private boolean activeChain(Long id) {
        Set<Long> seen=new HashSet<>();
        while(id!=null) {
            if(!seen.add(id)||seen.size()>100) return false;
            Category c=categories.findById(id).orElse(null);
            if(c==null||c.getStatus()!=CategoryStatus.ACTIVE) return false;
            id=c.getParentCategoryId();
        }
        return !seen.isEmpty();
    }
    private boolean categoryActive(Long product) {
        return productCategories.findByIdProductId(product).stream().anyMatch(c->activeChain(c.getId().getCategoryId()));
    }
    private Product owned(Authentication auth,Long id,boolean lock) {
        Long shop=shopService.getManagedShopId(auth);
        Product p=(lock?products.lockOwned(id,shop):products.findById(id).filter(x->x.getShopId().equals(shop)))
            .orElseThrow(()->new AccessDeniedException("Sản phẩm không thuộc cửa hàng của bạn."));
        return lock?locks.refresh(p):p;
    }
    private ProductVariant variantOf(Long product,Long id) {
        return variants.lockById(id).map(locks::refresh).filter(v->v.getProductId().equals(product))
            .orElseThrow(()->new AccessDeniedException("SKU không thuộc sản phẩm này."));
    }
    private void writable(Product p) { require(p.getStatus()!=ProductStatus.DELETED,"Sản phẩm đã lưu trữ; chỉ xem lịch sử và xử lý tồn kho."); }
    private void meta(ProductRequest f) {
        require(f.getName()!=null&&!f.getName().isBlank()&&f.getName().trim().length()<=250,"Nhập tên sản phẩm tối đa 250 ký tự.");
        require(f.getCategoryId()!=null&&activeChain(f.getCategoryId()),"Chọn danh mục đang hoạt động cùng toàn bộ danh mục cha.");
        imageUrl(f.getImageUrl());
        if(f.isRequiresPreparationCheck()) require(f.getPreparationNote()!=null&&!f.getPreparationNote().isBlank(),"Nhập hướng dẫn kiểm tra trước khi chuẩn bị hàng.");
    }
    private void imageUrl(String url) {
        require(url!=null&&!url.isBlank()&&url.length()<=1000&&(url.startsWith("https://")||url.startsWith("http://")||url.startsWith("/images/")),"Ảnh phải là URL http/https hoặc ảnh đã tải lên; không dùng ảnh base64.");
    }
    private void variantFields(VariantRequest f,ProductVariant old) {
        require(f.getSku()!=null&&f.getSku().matches("[A-Za-z0-9_-]{1,100}"),"Mã SKU chỉ gồm chữ Latin, số, - hoặc _.");
        if(old==null||!old.getSku().equalsIgnoreCase(f.getSku())) require(!variants.existsBySkuIgnoreCase(f.getSku()),"Mã SKU đã tồn tại.");
        require(f.getVariantName()!=null&&!f.getVariantName().isBlank()&&f.getVariantName().length()<=200,"Nhập tên SKU.");
        price(f.getPrice()); imageUrl(f.getThumbnailUrl());
        require(UNITS.contains(f.getUnit())||(old!=null&&Objects.equals(old.getUnit(),f.getUnit())),"Đơn vị bán không hợp lệ.");
        require(f.getLowStockThreshold()!=null&&f.getLowStockThreshold()>=0,"Ngưỡng tồn phải từ 0.");
        require(f.getCompareAtPrice()==null||f.getCompareAtPrice().compareTo(f.getPrice())>=0,"Giá so sánh không được thấp hơn giá bán.");
        require(f.getWeight()==null||(f.getWeight().compareTo(new BigDecimal("0.001"))>=0&&f.getWeight().stripTrailingZeros().scale()<=3),"Khối lượng phải từ 0,001 kg và tối đa 3 số lẻ.");
        if(old!=null&&!Objects.equals(old.getUnit(),f.getUnit())) {
            require(!transactions.existsByVariantId(old.getVariantId())&&variants.orderReferenceCount(old.getVariantId())==0&&batches.findByVariantIdOrderByExpiryDateAscBatchIdAsc(old.getVariantId()).stream().noneMatch(b->b.getQuantityOnHand()>0||b.getReservedQuantity()>0),"Không đổi đơn vị của SKU đã có hàng/lịch sử; hãy tạo SKU mới.");
        }
    }
    // SKU is generated once on creation. Display-name edits do not change its identity.
    private String generateSku(String productName,String variantName,Set<String> usedCodes) {
        String text=(productName==null?"":productName)+"-"+(variantName==null?"":variantName);
        String stem=Normalizer.normalize(text.toUpperCase(Locale.ROOT).replace('Đ','D'),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^A-Z0-9]+","-").replaceAll("^-|-$","");
        if(stem.isBlank())stem="SKU";
        if(stem.length()>60)stem=stem.substring(0,60).replaceAll("-$","");
        String code;
        do {
            code=stem+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        } while(usedCodes.contains(code.toLowerCase(Locale.ROOT))||variants.existsBySkuIgnoreCase(code));
        return code;
    }
    private void apply(Product p,ProductRequest f) {
        p.setName(f.getName().trim()); p.setDescription(f.getDescription()); p.setRequiresPreparationCheck(f.isRequiresPreparationCheck());
        p.setPreparationNote(f.isRequiresPreparationCheck()?f.getPreparationNote().trim():null); p.setUpdatedAt(now());
    }
    private void apply(ProductVariant v,VariantRequest f) {
        v.setSku(f.getSku()); v.setVariantName(f.getVariantName().trim()); v.setThumbnailUrl(f.getThumbnailUrl().trim());
        v.setPrice(f.getPrice()); v.setCompareAtPrice(f.getCompareAtPrice()); v.setWeight(f.getWeight()); v.setUnit(f.getUnit()); v.setLowStockThreshold(f.getLowStockThreshold()); v.setUpdatedAt(now());
    }
    private void image(Long product,Long variant,String url) {
        List<ProductImage> old=images.findByProductIdOrderBySortOrderAscImageIdAsc(product);
        ProductImage i=old.stream().filter(x->Objects.equals(x.getVariantId(),variant)&&Boolean.TRUE.equals(x.getPrimary())).findFirst().orElse(new ProductImage());
        i.setProductId(product); i.setVariantId(variant); i.setImageUrl(url.trim()); i.setPrimary(true); i.setSortOrder(0);
        if(i.getCreatedAt()==null)i.setCreatedAt(now());
        images.save(i);
    }
    private void priceHistory(ProductVariant v,Long actor) {
        LocalDateTime effective=now();
        for(PriceHistory h:prices.findByVariantIdAndEffectiveToIsNull(v.getVariantId())) {
            if(!effective.isAfter(h.getEffectiveFrom())) effective=h.getEffectiveFrom().plusSeconds(1);
            h.setEffectiveTo(effective); prices.save(h);
        }
        PriceHistory h=new PriceHistory(); h.setVariantId(v.getVariantId()); h.setPrice(v.getPrice()); h.setEffectiveFrom(effective); h.setChangedBy(actor); prices.save(h);
    }
    @Override @Transactional
    public Long create(Authentication auth,ProductRequest f) {
        Long shop=shopService.getManagedShopId(auth),actor=authService.getCurrentUserId(auth);
        meta(f); require(f.getVariants()!=null&&!f.getVariants().isEmpty()&&f.getVariants().size()<=20,"Thêm ít nhất 1 và tối đa 20 SKU.");
        Set<String> codes=new HashSet<>();
        for(VariantRequest v:f.getVariants()) if(v.getSku()!=null&&!v.getSku().isBlank())
            require(codes.add(v.getSku().toLowerCase(Locale.ROOT)),"Không lặp mã SKU trong cùng form.");
        for(VariantRequest v:f.getVariants()) {
            if(v.getSku()==null||v.getSku().isBlank()) {
                v.setSku(generateSku(f.getName(),v.getVariantName(),codes));
                codes.add(v.getSku().toLowerCase(Locale.ROOT));
            }
            variantFields(v,null);initial(v);
        }
        Product p=new Product(); p.setShopId(shop); apply(p,f);
        String root=Normalizer.normalize(f.getName().toLowerCase(Locale.ROOT).replace('đ','d'),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
        if(root.isBlank())root="san-pham";
        if(root.length()>270)root=root.substring(0,270);
        String slug=root; int suffix=2; while(products.existsByShopIdAndSlug(shop,slug))slug=root+"-"+suffix++;
        p.setSlug(slug); p.setStatus(ProductStatus.INACTIVE); p.setApprovalStatus(ApprovalStatus.PENDING); p.setCreatedAt(now());
        products.saveAndFlush(p); productCategories.save(new ProductCategory(p.getProductId(),f.getCategoryId()));
        image(p.getProductId(),null,f.getImageUrl());
        for(VariantRequest v:f.getVariants()) createVariant(p,v,actor);
        journal.audit(actor,"CREATE","PRODUCT",p.getProductId(),"Tạo sản phẩm chờ duyệt.");
        return p.getProductId();
    }
    private int initial(VariantRequest f) {
        if(f.isNoInitialStock()) { require(f.getInitialQuantity()==null||f.getInitialQuantity()==0,"Đã chọn chưa nhập hàng thì số lượng phải là 0."); return 0; }
        require(f.getInitialQuantity()!=null&&f.getInitialQuantity()>0,"Nhập số lượng dương hoặc chọn Chưa nhập hàng.");
        require(f.getBatchCode()==null||f.getBatchCode().isBlank()||f.getBatchCode().matches("[A-Za-z0-9_-]{1,100}"),"Mã lô chỉ gồm chữ Latin, số, - hoặc _.");
        dates(f.getReceivedDate(),f.getExpiryDate(),today());
        require(f.getCostPrice()==null||f.getCostPrice().signum()>=0,"Giá vốn không được âm.");
        return f.getInitialQuantity();
    }
    private void createVariant(Product p,VariantRequest f,Long actor) {
        int qty=initial(f);
        ProductVariant v=new ProductVariant(); v.setProductId(p.getProductId());apply(v,f);
        v.setStatus(qty>0?VariantStatus.ACTIVE:VariantStatus.OUT_OF_STOCK);v.setCreatedAt(now());variants.saveAndFlush(v); image(p.getProductId(),v.getVariantId(),f.getThumbnailUrl());priceHistory(v,actor);
        if(qty>0) {
            if(f.getBatchCode()==null||f.getBatchCode().isBlank())f.setBatchCode(batchCodes.generateCode(v.getVariantId(),f.getReceivedDate()));
            InventoryBatch b=new InventoryBatch(); b.setVariantId(v.getVariantId());b.setBatchCode(f.getBatchCode());b.setQuantityOnHand(qty);b.setReservedQuantity(0);b.setCostPrice(f.getCostPrice());
            b.setReceivedAt(f.getReceivedDate().atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());b.setExpiryDate(f.getExpiryDate());b.setStatus(BatchStatus.ACTIVE);b.setCreatedAt(now());b.setUpdatedAt(now());batches.saveAndFlush(b);
            journal.stock(v.getVariantId(),b.getBatchId(),StockTransactionType.RESTOCK,qty,actor,"Tồn ban đầu của SKU.","BATCH",b.getBatchId());
            journal.audit(actor,"RESTOCK","BATCH",b.getBatchId(),"Nhập tồn ban đầu: "+qty+" "+v.getUnit());
        }
    }
    @Override @Transactional public void update(Authentication auth,Long id,ProductRequest f) {
        Product p=owned(auth,id,true);writable(p);meta(f);String why=reason(f.getReason());apply(p,f);
        p.setStatus(ProductStatus.INACTIVE);p.setApprovalStatus(ApprovalStatus.PENDING);p.setApprovedAt(null);p.setApprovedBy(null);
        products.saveAndFlush(p); // Stop publication before changing the category (SQL trigger).
        productCategories.deleteByIdProductId(id); productCategories.flush();productCategories.save(new ProductCategory(id,f.getCategoryId()));image(id,null,f.getImageUrl());
        journal.audit(authService.getCurrentUserId(auth),"UPDATE","PRODUCT",id,why);
    }
    @Override @Transactional public void changeStatus(Authentication auth,Long id,ActionRequest f) {
        Product p=owned(auth,id,true);writable(p);String why=reason(f.getReason());
        require(f.getStatus()!=null,"Trạng thái không hợp lệ.");
        List<ProductVariant> vs=variants.findByProductIdOrderByVariantId(id);
        if(f.getStatus()==ProductStatus.ACTIVE) {
            require(p.getApprovalStatus()==ApprovalStatus.APPROVED,"Sản phẩm phải được Admin duyệt trước khi mở bán.");
            require(categoryActive(id),"Sản phẩm thiếu danh mục đang hoạt động.");
            require(vs.stream().anyMatch(v->v.getStatus()==VariantStatus.ACTIVE||v.getStatus()==VariantStatus.OUT_OF_STOCK),"Sản phẩm cần ít nhất một SKU mở bán.");
        }
        if(f.getStatus()==ProductStatus.DELETED) for(ProductVariant v:vs) require(batches.findByVariantIdOrderByExpiryDateAscBatchIdAsc(v.getVariantId()).stream().noneMatch(b->b.getReservedQuantity()>0),"Không lưu trữ sản phẩm đang giữ hàng cho đơn.");
        ProductStatus before=p.getStatus();p.setStatus(f.getStatus());p.setUpdatedAt(now());products.saveAndFlush(p);
        if(f.getStatus()==ProductStatus.DELETED) for(ProductVariant v:vs) {
            v.setStatus(VariantStatus.DELETED);v.setUpdatedAt(now());variants.save(v);
        }
        journal.audit(authService.getCurrentUserId(auth),"STATUS","PRODUCT",id,before+" → "+f.getStatus()+": "+why);
    }
    @Override @Transactional public void addVariant(Authentication auth,Long product,VariantRequest f) {
        Product p=owned(auth,product,true);writable(p);
        if(f.getSku()==null||f.getSku().isBlank())f.setSku(generateSku(p.getName(),f.getVariantName(),Set.of()));
        variantFields(f,null);initial(f);
        String why=reason(f.getReason());Long actor=authService.getCurrentUserId(auth);
        requestReapproval(p);createVariant(p,f,actor);
        journal.audit(actor,"ADD_SKU","PRODUCT",product,why+"; thêm cấu trúc SKU, gửi duyệt lại nếu đã duyệt.");
    }
    @Override @Transactional public void updateVariant(Authentication auth,Long product,Long id,VariantRequest f) {
        Product p=owned(auth,product,true);writable(p);ProductVariant v=variantOf(product,id);
        require(v.getStatus()!=VariantStatus.DELETED,"SKU đã lưu trữ.");
        f.setSku(v.getSku()); // Keep the stored code even when a request sends an empty or altered value.
        variantFields(f,v);String why=reason(f.getReason());
        boolean structureChanged=!Objects.equals(v.getVariantName(),f.getVariantName().trim())
            ||!Objects.equals(v.getUnit(),f.getUnit())||!sameWeight(v.getWeight(),f.getWeight());
        if(structureChanged)requestReapproval(p);
        BigDecimal previous=v.getPrice();apply(v,f);variants.save(v);image(product,id,f.getThumbnailUrl());
        Long actor=authService.getCurrentUserId(auth);if(previous.compareTo(v.getPrice())!=0)priceHistory(v,actor);
        journal.audit(actor,"UPDATE","VARIANT",id,why+"; giá "+previous+" → "+v.getPrice()+(structureChanged?"; thay cấu trúc, gửi duyệt lại nếu đã duyệt.":""));
    }
    private boolean sameWeight(BigDecimal a,BigDecimal b) {
        return a==null?b==null:b!=null&&a.compareTo(b)==0;
    }
    private void requestReapproval(Product p) {
        if(p.getApprovalStatus()!=ApprovalStatus.APPROVED)return;
        p.setStatus(ProductStatus.INACTIVE);p.setApprovalStatus(ApprovalStatus.PENDING);p.setApprovedBy(null);p.setApprovedAt(null);p.setUpdatedAt(now());
        products.saveAndFlush(p); // Stop publication before SKU changes; preserves SQL trigger invariants.
    }
    @Override @Transactional public void changeVariantStatus(Authentication auth,Long product,Long id,VariantActionRequest f) {
        Product p=owned(auth,product,true);writable(p);ProductVariant v=variantOf(product,id);
        require(v.getStatus()!=VariantStatus.DELETED,"SKU đã lưu trữ.");
        require(f.getStatus()==VariantStatus.ACTIVE||f.getStatus()==VariantStatus.INACTIVE||f.getStatus()==VariantStatus.DELETED,"Trạng thái SKU không hợp lệ.");String why=reason(f.getReason());
        if(f.getStatus()==VariantStatus.DELETED)require(batches.findByVariantIdOrderByExpiryDateAscBatchIdAsc(id).stream().noneMatch(b->b.getReservedQuantity()>0),"SKU đang giữ cho đơn; không thể lưu trữ.");
        if(f.getStatus()!=VariantStatus.ACTIVE&&p.getStatus()==ProductStatus.ACTIVE)require(variants.findByProductIdOrderByVariantId(product).stream().anyMatch(x->!x.getVariantId().equals(id)&&(x.getStatus()==VariantStatus.ACTIVE||x.getStatus()==VariantStatus.OUT_OF_STOCK)),"Hãy ngừng bán sản phẩm trước khi đóng SKU cuối cùng.");
        VariantStatus before=v.getStatus();v.setStatus(f.getStatus());v.setUpdatedAt(now());variants.save(v);
        journal.audit(authService.getCurrentUserId(auth),"STATUS","VARIANT",id,before+" → "+f.getStatus()+": "+why);
    }
    @Override public ProductRequest editForm(Authentication auth,Long id) {
        ProductResponse r=get(auth,id);Product p=r.product();ProductRequest f=new ProductRequest();
        f.setName(p.getName());f.setDescription(p.getDescription());f.setImageUrl(r.imageUrl());f.setRequiresPreparationCheck(Boolean.TRUE.equals(p.getRequiresPreparationCheck()));f.setPreparationNote(p.getPreparationNote());
        f.setCategoryId(productCategories.findByIdProductId(id).stream().map(c->c.getId().getCategoryId()).findFirst().orElse(null));return f;
    }
    @Override public Page<PriceHistory> priceHistory(Authentication auth,Long product,Long id,int page) {
        owned(auth,product,false);
        variants.findById(id).filter(v->v.getProductId().equals(product)).orElseThrow(()->new AccessDeniedException("SKU không thuộc sản phẩm."));
        return prices.findByVariantIdOrderByEffectiveFromDescPriceHistoryIdDesc(id,PageRequest.of(Math.max(0,page-1),10));
    }
    @Override public VariantRequest variantForm(Authentication auth,Long product,Long id) {
        ProductVariant v=get(auth,product).variants().stream().map(VariantResponse::variant).filter(x->x.getVariantId().equals(id)).findFirst().orElseThrow(()->new AccessDeniedException("SKU không thuộc sản phẩm."));
        VariantRequest f=new VariantRequest();f.setSku(v.getSku());f.setVariantName(v.getVariantName());f.setThumbnailUrl(v.getThumbnailUrl());f.setPrice(v.getPrice());f.setCompareAtPrice(v.getCompareAtPrice());f.setWeight(v.getWeight());f.setUnit(v.getUnit());f.setLowStockThreshold(v.getLowStockThreshold());f.setNoInitialStock(true);return f;
    }
}
