package VietFreshHub.review.dto;

import VietFreshHub.review.entity.ReviewReportStatus;
import java.time.LocalDateTime;

public record ReviewReportCard(Long reportId, Long reviewId, String reporterName,
                               String targetName, Byte rating, String content,
                               String reason, String description,
                               ReviewReportStatus status, LocalDateTime createdAt) {}
