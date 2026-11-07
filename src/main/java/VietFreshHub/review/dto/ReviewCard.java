package VietFreshHub.review.dto;

import java.time.LocalDateTime;
import VietFreshHub.review.entity.ReviewStatus;

public record ReviewCard(Long reviewId, Long reviewerId, String reviewerName, String targetName,
                         Byte rating, String content, ReviewStatus status, LocalDateTime createdAt,
                         String shopName, String shopReply, LocalDateTime shopRepliedAt) {}
