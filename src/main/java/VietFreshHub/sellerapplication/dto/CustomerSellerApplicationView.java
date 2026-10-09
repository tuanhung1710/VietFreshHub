package VietFreshHub.sellerapplication.dto;

import VietFreshHub.sellerapplication.entity.DocumentVerificationStatus;
import VietFreshHub.sellerapplication.entity.SellerApplicationStatus;
import java.util.List;

public final class CustomerSellerApplicationView {

    private CustomerSellerApplicationView() {
    }

    public record Row(
            Long applicationId,
            String businessName,
            SellerApplicationStatus status,
            String statusLabel,
            String submittedAtLabel,
            String rejectionReason
    ) {
    }

    public record Detail(
            Long applicationId,
            String businessName,
            String taxCode,
            SellerApplicationStatus status,
            String statusLabel,
            String submittedAtLabel,
            String rejectionReason,
            boolean hasRejectedDocuments,
            List<Document> documents,
            List<History> history
    ) {
    }

    public record Document(
            Long documentId,
            String documentType,
            DocumentVerificationStatus status,
            String statusLabel,
            String uploadedAtLabel,
            String fileUrl
    ) {
    }

    public record History(
            String oldStatusLabel,
            String newStatusLabel,
            String changedByName,
            String note,
            String createdAtLabel
    ) {
    }
}
