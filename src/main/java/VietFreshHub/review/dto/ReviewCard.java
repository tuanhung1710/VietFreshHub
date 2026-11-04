package VietFreshHub.review.dto;

import java.time.LocalDateTime;
import VietFreshHub.review.entity.ReviewStatus;

public record ReviewCard(Long reviewId, String reviewerName, String targetName,
                         Byte rating, String content, ReviewStatus status, LocalDateTime createdAt) {}
