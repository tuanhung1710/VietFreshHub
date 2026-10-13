package VietFreshHub.sellerapplication.dto;

import VietFreshHub.sellerapplication.entity.SellerApplicationStatus;

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
