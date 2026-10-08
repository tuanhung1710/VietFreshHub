package VietFreshHub.Auth.service;

import VietFreshHub.Auth.dto.CustomerSellerApplicationView;
import VietFreshHub.Auth.entity.*;
import VietFreshHub.Auth.repository.*;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CustomerSellerApplicationService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentAdminRepository documentRepository;
    private final SellerApplicationStatusHistoryRepository historyRepository;
    private final Cloudinary cloudinary;
    private final CloudinaryStorageService cloudinaryStorageService;

    public CustomerSellerApplicationService(
            SellerApplicationRepository applicationRepository,
            SellerApplicationDocumentAdminRepository documentRepository,
            SellerApplicationStatusHistoryRepository historyRepository,
            Cloudinary cloudinary,
            CloudinaryStorageService cloudinaryStorageService
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.cloudinary = cloudinary;
        this.cloudinaryStorageService = cloudinaryStorageService;
    }

    @Transactional(readOnly = true)
    public List<CustomerSellerApplicationView.Row> getMyApplications(String email) {
        return applicationRepository
                .findByUser_EmailIgnoreCaseOrderBySubmittedAtDesc(email)
                .stream()
                .map(application -> new CustomerSellerApplicationView.Row(
                        application.getApplicationId(),
                        application.getBusinessName(),
                        application.getStatus(),
                        statusLabel(application.getStatus()),
                        format(application.getSubmittedAt()),
                        application.getRejectionReason()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerSellerApplicationView.Detail getMyApplication(
            Long applicationId,
            String email
    ) {
        SellerApplication application = findOwnedApplication(applicationId, email);
        if (application.getStatus() == SellerApplicationStatus.UNDER_REVIEW) {
            throw new SellerApplicationUnderReviewException(
                    "Hồ sơ đang được admin thẩm định."
            );
        }
        List<SellerApplicationDocument> documents = documentRepository
                .findByApplication_ApplicationIdOrderByUploadedAtAsc(applicationId);

        List<CustomerSellerApplicationView.Document> documentViews = documents.stream()
                .map(document -> new CustomerSellerApplicationView.Document(
                        document.getDocumentId(),
                        document.getDocumentType(),
                        document.getVerificationStatus(),
                        documentStatusLabel(document.getVerificationStatus()),
                        format(document.getUploadedAt()),
                        cloudinaryStorageService.createPrivateDownloadUrl(
                                document.getCloudinaryPublicId(),
                                document.getCloudinaryResourceType(),
                                document.getCloudinaryFormat()
                        )
                ))
                .toList();

        List<CustomerSellerApplicationView.History> historyViews = historyRepository
                .findByApplication_ApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .map(item -> new CustomerSellerApplicationView.History(
                        item.getOldStatus() == null
                                ? "—"
                                : statusLabel(item.getOldStatus()),
                        statusLabel(item.getNewStatus()),
                        item.getChangedBy() == null
                                ? "Hệ thống"
                                : item.getChangedBy().getFullName(),
                        item.getNote(),
                        format(item.getCreatedAt())
                ))
                .toList();

        boolean hasRejectedDocuments = documents.stream()
                .anyMatch(document ->
                        document.getVerificationStatus()
                                == DocumentVerificationStatus.REJECTED
                );

        return new CustomerSellerApplicationView.Detail(
                application.getApplicationId(),
                application.getBusinessName(),
                application.getTaxCode(),
                application.getStatus(),
                statusLabel(application.getStatus()),
                format(application.getSubmittedAt()),
                application.getRejectionReason(),
                hasRejectedDocuments,
                documentViews,
                historyViews
        );
    }

    @Transactional
    public void replaceRejectedDocument(
            Long applicationId,
            Long documentId,
            String customerEmail,
            MultipartFile file
    ) {
        SellerApplication application =
                findOwnedApplication(applicationId, customerEmail);

        if (application.getStatus() != SellerApplicationStatus.REJECTED
                && application.getStatus() != SellerApplicationStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Chỉ có thể sửa giấy tờ trong hồ sơ đang thẩm định hoặc bị từ chối."
            );
        }

        SellerApplicationDocument document = documentRepository
                .findByDocumentIdAndApplication_ApplicationId(documentId, applicationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy giấy tờ thuộc hồ sơ này."
                ));

        if (document.getVerificationStatus() != DocumentVerificationStatus.REJECTED) {
            throw new IllegalStateException(
                    "Chỉ có thể thay thế giấy tờ đã bị từ chối."
            );
        }

        validateFile(file);

        String oldPublicId = document.getCloudinaryPublicId();
        String oldResourceType = document.getCloudinaryResourceType();

        try {
            String folder = "vietfresh/seller-applications/"
                    + applicationId + "/"
                    + document.getDocumentType();

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "auto",
                            "type", "authenticated",
                            "folder", folder,
                            "overwrite", false
                    )
            );

            String newPublicId = (String) result.get("public_id");
            String newResourceType = (String) result.get("resource_type");
            Object formatValue = result.get("format");
            String newFormat = formatValue == null
                    ? null
                    : formatValue.toString();

            document.setCloudinaryPublicId(newPublicId);
            document.setCloudinaryResourceType(newResourceType);
            document.setCloudinaryFormat(newFormat);
            document.setVerificationStatus(DocumentVerificationStatus.PENDING);
            document.setVerifiedAt(null);

            // Xóa file cũ sau khi DB commit; nếu DB rollback thì xóa file mới.
            registerCloudinaryCleanup(
                    oldPublicId,
                    oldResourceType,
                    newPublicId,
                    newResourceType
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể tải tài liệu mới lên Cloudinary.",
                    exception
            );
        }
    }

    @Transactional
    public void resubmit(
            Long applicationId,
            String customerEmail,
            String businessName,
            String taxCode
    ) {
        SellerApplication application =
                findOwnedApplication(applicationId, customerEmail);

        if (application.getStatus() != SellerApplicationStatus.REJECTED) {
            throw new IllegalStateException(
                    "Chỉ hồ sơ bị từ chối mới có thể gửi lại."
            );
        }

        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập tên cơ sở kinh doanh."
            );
        }

        String cleanedBusinessName = businessName.trim();
        if (cleanedBusinessName.length() > 200) {
            throw new IllegalArgumentException(
                    "Tên cơ sở không được dài quá 200 ký tự."
            );
        }

        String cleanedTaxCode = taxCode == null ? null : taxCode.trim();
        if (cleanedTaxCode != null && cleanedTaxCode.length() > 50) {
            throw new IllegalArgumentException(
                    "Mã số thuế không được dài quá 50 ký tự."
            );
        }

        List<SellerApplicationDocument> documents = documentRepository
                .findByApplication_ApplicationIdOrderByUploadedAtAsc(applicationId);

        if (documents.isEmpty()) {
            throw new IllegalStateException(
                    "Hồ sơ chưa có giấy tờ để gửi lại."
            );
        }

        boolean hasRejectedDocuments = documents.stream()
                .anyMatch(document ->
                        document.getVerificationStatus()
                                == DocumentVerificationStatus.REJECTED
                );

        if (hasRejectedDocuments) {
            throw new IllegalStateException(
                    "Hãy thay thế tất cả giấy tờ bị từ chối trước khi gửi lại hồ sơ."
            );
        }

        SellerApplicationStatus oldStatus = application.getStatus();

        application.setBusinessName(cleanedBusinessName);
        application.setTaxCode(cleanedTaxCode);
        application.setStatus(SellerApplicationStatus.PENDING);
        application.setReviewedBy(null);
        application.setReviewedAt(null);
        application.setRejectionReason(null);

        applicationRepository.save(application);

        SellerApplicationStatusHistory history =
                new SellerApplicationStatusHistory();
        history.setApplication(application);
        history.setOldStatus(oldStatus);
        history.setNewStatus(SellerApplicationStatus.PENDING);
        history.setChangedBy(application.getUser());
        history.setNote("Khách hàng đã chỉnh sửa và gửi lại hồ sơ.");
        historyRepository.save(history);
    }

    private SellerApplication findOwnedApplication(Long applicationId, String email) {
        return applicationRepository
                .findByApplicationIdAndUser_EmailIgnoreCase(applicationId, email)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ của bạn."
                ));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn tài liệu.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Tài liệu không được lớn hơn 10 MB."
            );
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !(contentType.equals("application/pdf")
                || contentType.equals("image/jpeg")
                || contentType.equals("image/png"))) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận PDF, JPG hoặc PNG."
            );
        }
    }

    private void registerCloudinaryCleanup(
            String oldPublicId,
            String oldResourceType,
            String newPublicId,
            String newResourceType
    ) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            deletePrivate(oldPublicId, oldResourceType);
                        } else {
                            deletePrivate(newPublicId, newResourceType);
                        }
                    }
                }
        );
    }

    private void deletePrivate(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type",
                            resourceType == null ? "image" : resourceType,
                            "type",
                            "authenticated"
                    )
            );
        } catch (IOException ignored) {
            // Có thể ghi log nếu muốn theo dõi lỗi dọn file Cloudinary.
        }
    }

    private String statusLabel(SellerApplicationStatus status) {
        return switch (status) {
            case PENDING -> "Chờ tiếp nhận";
            case UNDER_REVIEW -> "Đang thẩm định";
            case APPROVED -> "Đã phê duyệt";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    private String documentStatusLabel(DocumentVerificationStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xác minh";
            case VERIFIED -> "Đã xác minh";
            case REJECTED -> "Không hợp lệ";
        };
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? "—" : dateTime.format(DATE_FORMAT);
    }
    public class SellerApplicationUnderReviewException extends RuntimeException {
        public SellerApplicationUnderReviewException(String message) {
            super(message);
        }
    }
}