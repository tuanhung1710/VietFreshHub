package VietFreshHub.Auth.dto;

import VietFreshHub.Auth.entity.DocumentVerificationStatus;
import VietFreshHub.Auth.entity.SellerApplicationStatus;

import java.util.List;

public record SellerApplicationAdminDetail(
        Long applicationId,
        String businessName,
        String taxCode,
        String applicantName,
        String applicantEmail,
        String applicantPhone,
        SellerApplicationStatus status,
        String statusLabel,
        String submittedAtLabel,
        String reviewerName,
        String reviewedAtLabel,
        String rejectionReason,
        boolean readyForApproval,
        List<DocumentItem> documents,
        List<HistoryItem> history
) {
    public record DocumentItem(
            Long documentId,
            String documentType,
            String fileUrl,
            DocumentVerificationStatus verificationStatus,
            String verificationStatusLabel,
            String uploadedAtLabel
    ) {
    }

    public record HistoryItem(
            String oldStatusLabel,
            String newStatusLabel,
            String changedByName,
            String note,
            String createdAtLabel
    ) {
    }
}
