package VietFreshHub.review.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewReportForm(@NotNull ReviewReportReason reason,
                               @Size(max = 1000) String description) {
    @AssertTrue(message = "Vui lòng nhập mô tả cho lý do khác.")
    public boolean isOtherReasonDescribed() {
        return reason != ReviewReportReason.OTHER || (description != null && !description.isBlank());
    }
}
