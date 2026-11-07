package VietFreshHub.inventory.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.common.Pagination;
import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.AuditResponse;
import VietFreshHub.inventory.entity.AuditEvent;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.repository.AuditEventRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.shop.entity.ShopMember;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Đọc lịch sử hoạt động và tải các nhãn liên quan trong phạm vi cửa hàng. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryActivityHistoryService {
    private final AuditEventRepository auditEventRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final UserRepository userRepository;
    private final ShopMemberRepository shopMemberRepository;
    private final ShopService shopService;

    public Page<AuditResponse> search(
            Authentication authentication, ActivityHistoryFilter filters, int page, int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        filters.normalize();
        HistoryQuery query = prepareQuery(shopId, filters);
        Page<AuditEvent> events = findEvents(query, pageRequest(page, size, "auditId"));
        HistoryDetails details = loadDetails(shopId, events);
        return events.map(event -> toResponse(event, details));
    }

    private HistoryQuery prepareQuery(Long shopId, ActivityHistoryFilter filters) {
        String text = filters.getQ();
        // LIKE phải tìm các ký tự !, %, _ như văn bản do người dùng nhập.
        String pattern =
                "%"
                        + text.toLowerCase(Locale.ROOT)
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                        + "%";
        LocalDateTime from =
                filters.getFrom() == null
                        ? LocalDateTime.of(1900, 1, 1, 0, 0)
                        : InventoryRules.startOfDayUtc(filters.getFrom());
        LocalDateTime until =
                filters.getTo() == null
                        ? LocalDateTime.of(9998, 1, 1, 0, 0)
                        : InventoryRules.startOfDayUtc(filters.getTo().plusDays(1));
        return new HistoryQuery(
                shopId,
                filters.getAction(),
                filters.getEntity(),
                text.isEmpty(),
                pattern,
                BusinessCodes.parse("PRD", text),
                BusinessCodes.parse("LOT", text),
                from,
                until);
    }

    private Page<AuditEvent> findEvents(HistoryQuery query, PageRequest pageRequest) {
        Page<AuditEvent> events = searchPage(query, pageRequest);
        boolean pageIsOutOfRange = events.getNumber() >= Math.max(1, events.getTotalPages());
        if (pageIsOutOfRange) {
            int lastPageIndex = Math.max(0, events.getTotalPages() - 1);
            return searchPage(query, pageRequest.withPage(lastPageIndex));
        }
        return events;
    }

    private Page<AuditEvent> searchPage(HistoryQuery query, PageRequest pageRequest) {
        return auditEventRepository.searchHistory(
                query.shopId(),
                query.action(),
                query.entity(),
                query.emptyText(),
                query.pattern(),
                query.productCode(),
                query.batchCode(),
                query.from(),
                query.until(),
                pageRequest);
    }

    private HistoryDetails loadDetails(Long shopId, Page<AuditEvent> events) {
        RelatedIds ids = collectRelatedIds(events);
        // Tải lô -> SKU -> sản phẩm để sự kiện ở cấp lô cũng có đủ tên sản phẩm.
        Map<Long, InventoryBatch> batches = loadBatches(ids.batchIds(), ids.variantIds());
        Map<Long, ProductVariant> variants = loadVariants(ids.variantIds(), ids.productIds());
        Map<Long, Product> products = loadProducts(shopId, ids.productIds());
        Map<Long, String> actorLabels = loadActorLabels(shopId, ids.actorIds());
        return new HistoryDetails(products, variants, batches, actorLabels);
    }

    private RelatedIds collectRelatedIds(Page<AuditEvent> events) {
        Set<Long> productIds = new HashSet<>();
        Set<Long> variantIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        Set<Long> actorIds = new HashSet<>();
        for (AuditEvent event : events) {
            if (event.getActorId() != null) {
                actorIds.add(event.getActorId());
            }
            if (event.getEntityId() == null) {
                continue;
            }
            if ("PRODUCT".equals(event.getEntityType())) {
                productIds.add(event.getEntityId());
            } else if ("VARIANT".equals(event.getEntityType())) {
                variantIds.add(event.getEntityId());
            } else if ("BATCH".equals(event.getEntityType())) {
                batchIds.add(event.getEntityId());
            }
        }
        return new RelatedIds(productIds, variantIds, batchIds, actorIds);
    }

    private Map<Long, InventoryBatch> loadBatches(Set<Long> batchIds, Set<Long> variantIds) {
        Map<Long, InventoryBatch> batches = new HashMap<>();
        if (!batchIds.isEmpty()) {
            for (InventoryBatch batch : inventoryBatchRepository.findAllById(batchIds)) {
                batches.put(batch.getBatchId(), batch);
                variantIds.add(batch.getVariantId());
            }
        }
        return batches;
    }

    private Map<Long, ProductVariant> loadVariants(Set<Long> variantIds, Set<Long> productIds) {
        Map<Long, ProductVariant> variants = new HashMap<>();
        if (!variantIds.isEmpty()) {
            for (ProductVariant variant : productVariantRepository.findAllById(variantIds)) {
                variants.put(variant.getVariantId(), variant);
                productIds.add(variant.getProductId());
            }
        }
        return variants;
    }

    private Map<Long, Product> loadProducts(Long shopId, Set<Long> productIds) {
        Map<Long, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product product : productRepository.findAllById(productIds)) {
                if (shopId.equals(product.getShopId())) {
                    products.put(product.getProductId(), product);
                }
            }
        }
        return products;
    }

    private Map<Long, String> loadActorLabels(Long shopId, Set<Long> actorIds) {
        Map<Long, String> labels = new HashMap<>();
        if (actorIds.isEmpty()) {
            return labels;
        }
        for (User user : userRepository.findAllById(actorIds)) {
            if (hasText(user.getFullName())) {
                labels.put(user.getUserId(), user.getFullName().trim());
            }
        }
        Set<Long> actorsWithoutNames = new HashSet<>(actorIds);
        actorsWithoutNames.removeAll(labels.keySet());
        if (!actorsWithoutNames.isEmpty()) {
            for (ShopMember member :
                    shopMemberRepository.findByShopIdAndUserIdIn(shopId, actorsWithoutNames)) {
                String roleLabel = shopRoleLabel(member.getMemberRole());
                if (roleLabel != null) {
                    labels.putIfAbsent(member.getUserId(), roleLabel);
                }
            }
        }
        return labels;
    }

    private AuditResponse toResponse(AuditEvent event, HistoryDetails details) {
        String actorLabel =
                event.getActorId() == null
                        ? null
                        : details.actorLabels().getOrDefault(event.getActorId(), "Người dùng");
        return new AuditResponse(
                event,
                auditActionLabel(event),
                auditEntityLabel(event, details.products(), details.variants(), details.batches()),
                actorLabel);
    }

    /**
     * Tạo nhãn đối tượng trong lịch sử từ sản phẩm, phân loại hoặc lô; dùng nhãn thay thế khi thiếu dữ liệu.
     */
    private String auditEntityLabel(
            AuditEvent event,
            Map<Long, Product> products,
            Map<Long, ProductVariant> variants,
            Map<Long, InventoryBatch> batches) {
        // entityId trỏ tới loại bảng ghi trong entityType; chọn đúng dữ liệu trước khi ghép nhãn.
        if ("PRODUCT".equals(event.getEntityType())) {
            return auditProductName(products.get(event.getEntityId()));
        }
        if ("VARIANT".equals(event.getEntityType())) {
            ProductVariant variant = variants.get(event.getEntityId());
            if (variant == null) {
                return "Phân loại";
            }
            String label = auditProductName(products.get(variant.getProductId()));
            if (hasText(variant.getVariantName())) {
                label += " · " + variant.getVariantName().trim();
            }
            // Tránh lặp nhãn nếu dữ liệu cũ dùng chính SKU làm tên phân loại.
            if (hasText(variant.getSku()) && !variant.getSku().equals(variant.getVariantName())) {
                label += " · " + variant.getSku().trim();
            }
            return label;
        }
        if ("BATCH".equals(event.getEntityType())) {
            // Vẫn giữ mã LOT để nhận biết sự kiện khi không còn tải được thông tin SKU liên quan.
            InventoryBatch batch = batches.get(event.getEntityId());
            ProductVariant variant = batch == null ? null : variants.get(batch.getVariantId());
            String label =
                    variant == null
                            ? "Lô hàng"
                            : auditProductName(products.get(variant.getProductId()));
            if (event.getEntityId() != null) {
                label += " · Lô " + BusinessCodes.format("LOT", event.getEntityId());
            }
            if (variant != null && hasText(variant.getSku())) {
                label += " · " + variant.getSku().trim();
            }
            return label;
        }
        return "Nội dung liên quan";
    }

    /** Lấy tên sản phẩm đã bỏ khoảng trắng thừa hoặc dùng nhãn chung khi không có tên. */
    private String auditProductName(Product product) {
        return product != null && hasText(product.getName())
                ? product.getName().trim()
                : "Sản phẩm";
    }

    /** Đổi vai trò chủ shop hoặc quản lý sang nhãn hiển thị khi chưa có tên người thao tác. */
    private String shopRoleLabel(String role) {
        if ("OWNER".equals(role)) {
            return "Chủ cửa hàng";
        }
        if ("MANAGER".equals(role)) {
            return "Quản lý cửa hàng";
        }
        return null;
    }

    /** Kiểm tra chuỗi có nội dung ngoài khoảng trắng. */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Đổi mã hành động thành mô tả tiếng Việt, phân biệt thao tác trên sản phẩm và phân loại. */
    private String auditActionLabel(AuditEvent event) {
        String action = event.getAction();
        if (action == null) {
            return "Hoạt động khác";
        }
        // UPDATE và STATUS dùng chung mã cho sản phẩm/SKU nên cần xét thêm entityType khi đặt nhãn.
        switch (action) {
            case "CREATE":
                return "Tạo sản phẩm";
            case "UPDATE":
                return "VARIANT".equals(event.getEntityType())
                        ? "Chỉnh sửa phân loại"
                        : "Chỉnh sửa sản phẩm";
            case "PRICE":
                return "Thay đổi giá";
            case "REMINDER":
                return "Thay đổi mốc nhắc hạn sử dụng";
            case "STATUS":
                return "VARIANT".equals(event.getEntityType())
                        ? "Thay đổi trạng thái phân loại"
                        : "Thay đổi trạng thái sản phẩm";
            case "ADD_SKU":
                return "Thêm phân loại";
            case "RESTOCK":
                return "Nhập kho";
            case "EDIT":
                return "Chỉnh sửa lô";
            case "ADJUSTMENT":
                return "Điều chỉnh tồn kho";
            case "DISPOSE":
                return "Ghi nhận hủy hàng hết hạn";
            case "BLOCK":
                return "Khóa lô";
            case "UNBLOCK":
                return "Mở khóa lô";
            case "RESERVE":
                return "Giữ hàng cho đơn";
            case "RELEASE":
                return "Giải phóng hàng giữ";
            case "SALE":
                return "Xuất bán";
            default:
                return "Hoạt động khác";
        }
    }

    private PageRequest pageRequest(int page, int size, String idProperty) {
        return Pagination.request(page, size)
                .withSort(Sort.by(Sort.Direction.DESC, "createdAt", idProperty));
    }

    private record HistoryQuery(
            Long shopId,
            String action,
            String entity,
            boolean emptyText,
            String pattern,
            Long productCode,
            Long batchCode,
            LocalDateTime from,
            LocalDateTime until) {}

    private record RelatedIds(
            Set<Long> productIds, Set<Long> variantIds, Set<Long> batchIds, Set<Long> actorIds) {}

    private record HistoryDetails(
            Map<Long, Product> products,
            Map<Long, ProductVariant> variants,
            Map<Long, InventoryBatch> batches,
            Map<Long, String> actorLabels) {}
}
