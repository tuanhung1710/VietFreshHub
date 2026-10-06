package VietFreshHub.Auth.dto;

import VietFreshHub.Auth.entity.SellerApplicationStatus;

public record SellerApplicationAdminRow(
        Long applicationId,
        String businessName,
        String taxCode,
        String applicantName,
        String applicantEmail,
        String applicantPhone,
        SellerApplicationStatus status,
        String statusLabel,
        String submittedAtLabel
) {
}
