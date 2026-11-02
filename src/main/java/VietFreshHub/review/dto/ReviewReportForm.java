package VietFreshHub.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReportForm(@NotBlank @Size(max = 100) String reason,
                               @Size(max = 1000) String description) {}
