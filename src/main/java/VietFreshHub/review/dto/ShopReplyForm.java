package VietFreshHub.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShopReplyForm(@NotBlank @Size(max = 3000) String content) {}
