package VietFreshHub.Auth.dto;

import VietFreshHub.Auth.entity.DocumentVerificationStatus;
import VietFreshHub.Auth.entity.SellerApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerApplicationAdminDetail {
    private Long applicationId;
    private String businessName;
    private String taxCode;
    private String applicantName;
    private String applicantEmail;
    private String applicantPhone;
    private SellerApplicationStatus status;
    private String statusLabel;
    private String submittedAtLabel;
    private String reviewerName;
    private String reviewedAtLabel;
    private String rejectionReason;
    private boolean readyForApproval;
    private List<DocumentItem> documents;
    private List<HistoryItem> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentItem {
        private Long documentId;
        private String documentType;
        private String fileUrl;
        private DocumentVerificationStatus verificationStatus;
        private String verificationStatusLabel;
        private String uploadedAtLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private String oldStatusLabel;
        private String newStatusLabel;
        private String changedByName;
        private String note;
        private String createdAtLabel;
    }
}
