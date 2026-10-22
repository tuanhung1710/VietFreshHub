package VietFreshHub.admin.dto;

import java.time.LocalDateTime;

public interface AdminProductModerationRow {
    Long getProductId();
    String getName();
    String getSlug();
    String getDescription();
    String getShopName();
    String getStatus();
    String getApprovalStatus();
    Boolean getModerationBlocked();
    String getModerationReason();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    String getImageUrl();
    Long getVariantCount();
}
