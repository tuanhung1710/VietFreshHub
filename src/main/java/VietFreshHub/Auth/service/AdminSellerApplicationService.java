package VietFreshHub.Auth.service;

import VietFreshHub.Auth.dto.SellerApplicationAdminDetail;
import VietFreshHub.Auth.dto.SellerApplicationAdminRow;
import VietFreshHub.Auth.entity.*;
import VietFreshHub.Auth.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminSellerApplicationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SellerApplicationRepository applicationRepository;
    private final AdminSellerApplicationQueryRepository adminQueryRepository;
    private final SellerApplicationDocumentAdminRepository documentRepository;
    private final SellerApplicationStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleGrantRepository userRoleGrantRepository;
    private final String cloudinaryCloudName;
    private final String cloudinaryDocumentResourceType;
    private final CloudinaryStorageService cloudinaryStorageService;
    public AdminSellerApplicationService(
            SellerApplicationRepository applicationRepository,
            AdminSellerApplicationQueryRepository adminQueryRepository,
            SellerApplicationDocumentAdminRepository documentRepository,
            SellerApplicationStatusHistoryRepository historyRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleGrantRepository userRoleGrantRepository,
            @Value("${cloudinary.cloud-name:}") String cloudinaryCloudName,
            @Value("${cloudinary.document-resource-type:image}") String cloudinaryDocumentResourceType, CloudinaryStorageService cloudinaryStorageService
    ) {
        this.applicationRepository = applicationRepository;
        this.adminQueryRepository = adminQueryRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleGrantRepository = userRoleGrantRepository;
        this.cloudinaryCloudName = cloudinaryCloudName;
        this.cloudinaryDocumentResourceType = cloudinaryDocumentResourceType;
        this.cloudinaryStorageService = cloudinaryStorageService;
    }

    public List<SellerApplicationAdminRow> getApplications(SellerApplicationStatus filter) {
        List<SellerApplication> applications = filter == null
                ? adminQueryRepository.findAllForAdminOrderBySubmittedAtDesc()
                : adminQueryRepository.findAllForAdminByStatusOrderBySubmittedAtDesc(filter);

        return applications.stream().map(application -> {
            User applicant = application.getUser();
            return new SellerApplicationAdminRow(
                    application.getApplicationId(),
                    application.getBusinessName(),
                    application.getTaxCode(),
                    applicant.getFullName(),
                    applicant.getEmail(),
                    applicant.getPhone(),
                    application.getStatus(),
                    statusLabel(application.getStatus()),
                    format(application.getSubmittedAt())
            );
        }).toList();
    }

    public Map<String, Long> getStatusCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        long total = 0;
        for (SellerApplicationStatus status : SellerApplicationStatus.values()) {
            long count = adminQueryRepository.countForStatus(status);
            counts.put(status.name(), count);
            total += count;
        }
        counts.put("ALL", total);
        return counts;
    }

    public SellerApplicationAdminDetail getDetail(Long applicationId) {
        SellerApplication application = adminQueryRepository.findForAdminById(applicationId)
                .orElseThrow(() -> new SellerApplicationDecisionException("Không tìm thấy hồ sơ đăng ký bán hàng."));

        List<SellerApplicationDocument> documents = documentRepository
                .findByApplication_ApplicationIdOrderByUploadedAtAsc(applicationId);
        List<SellerApplicationStatusHistory> history = historyRepository
                .findByApplication_ApplicationIdOrderByCreatedAtDesc(applicationId);

        List<SellerApplicationAdminDetail.DocumentItem> documentItems = documents.stream()
                .map(document -> new SellerApplicationAdminDetail.DocumentItem(
                        document.getDocumentId(),
                        document.getDocumentType(),
                        cloudinaryStorageService.createPrivateDownloadUrl(
                                document.getCloudinaryPublicId(),
                                document.getCloudinaryResourceType(),
                                document.getCloudinaryFormat()
                        ),
                        document.getVerificationStatus(),
                        documentStatusLabel(document.getVerificationStatus()),
                        format(document.getUploadedAt())
                )).toList();

        List<SellerApplicationAdminDetail.HistoryItem> historyItems = history.stream()
                .map(item -> new SellerApplicationAdminDetail.HistoryItem(
                        item.getOldStatus() == null ? "—" : statusLabel(item.getOldStatus()),
                        statusLabel(item.getNewStatus()),
                        item.getChangedBy() == null ? "Hệ thống" : item.getChangedBy().getFullName(),
                        item.getNote(),
                        format(item.getCreatedAt())
                )).toList();

        boolean readyForApproval = !documents.isEmpty() && documents.stream()
                .allMatch(document -> document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED);
        User reviewer = application.getReviewedBy();

        return new SellerApplicationAdminDetail(
                application.getApplicationId(),
                application.getBusinessName(),
                application.getTaxCode(),
                application.getUser().getFullName(),
                application.getUser().getEmail(),
                application.getUser().getPhone(),
                application.getStatus(),
                statusLabel(application.getStatus()),
                format(application.getSubmittedAt()),
                reviewer == null ? null : reviewer.getFullName(),
                format(application.getReviewedAt()),
                application.getRejectionReason(),
                readyForApproval,
                documentItems,
                historyItems
        );
    }

    @Transactional
    public void startReview(Long applicationId, String adminEmail, String note) {
        SellerApplication application = lockApplication(applicationId);
        requireStatus(application, SellerApplicationStatus.PENDING);
        User admin = findAdmin(adminEmail);
        application.setReviewedBy(admin);
        transition(application, SellerApplicationStatus.UNDER_REVIEW, admin, cleanNote(note));
    }

    @Transactional
    public void verifyDocument(Long applicationId, Long documentId, String adminEmail) {
        SellerApplication application = lockApplication(applicationId);
        requireReviewable(application);
        User admin = findAdmin(adminEmail);

        if (application.getStatus() == SellerApplicationStatus.PENDING) {
            application.setReviewedBy(admin);
            transition(application, SellerApplicationStatus.UNDER_REVIEW, admin,
                    "Bắt đầu thẩm định khi xác minh giấy tờ.");
        }

        SellerApplicationDocument document = documentRepository
                .findByDocumentIdAndApplication_ApplicationId(documentId, applicationId)
                .orElseThrow(() -> new SellerApplicationDecisionException("Giấy tờ không thuộc hồ sơ này."));
        if (document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new SellerApplicationDecisionException("Giấy tờ này đã được xác minh.");
        }

        document.setVerificationStatus(DocumentVerificationStatus.VERIFIED);
        document.setVerifiedAt(nowUtc());
        documentRepository.save(document);
    }
    @Transactional
    public void rejectDocument(
            Long applicationId,
            Long documentId,
            String adminEmail
    ) {
        SellerApplication application = lockApplication(applicationId);
        requireReviewable(application);

        User admin = findAdmin(adminEmail);

        SellerApplicationDocument document = documentRepository
                .findByDocumentIdAndApplication_ApplicationId(documentId, applicationId)
                .orElseThrow(() -> new SellerApplicationDecisionException(
                        "Giấy tờ không thuộc hồ sơ này."
                ));

        if (document.getVerificationStatus() == DocumentVerificationStatus.REJECTED) {
            throw new SellerApplicationDecisionException(
                    "Giấy tờ này đã bị từ chối."
            );
        }

        // Khi admin bắt đầu xử lý hồ sơ đang chờ, chuyển sang đang thẩm định.
        if (application.getStatus() == SellerApplicationStatus.PENDING) {
            application.setReviewedBy(admin);
            transition(
                    application,
                    SellerApplicationStatus.UNDER_REVIEW,
                    admin,
                    "Bắt đầu thẩm định hồ sơ."
            );
        }

        // Chỉ từ chối giấy tờ; không thay đổi trạng thái hồ sơ.
        document.setVerificationStatus(DocumentVerificationStatus.REJECTED);
        document.setVerifiedAt(null);
        documentRepository.save(document);
    }
    @Transactional
    public void approve(Long applicationId, String adminEmail, String note) {
        SellerApplication application = lockApplication(applicationId);
        requireReviewable(application);
        User admin = findAdmin(adminEmail);
        List<SellerApplicationDocument> documents = documentRepository
                .findByApplication_ApplicationIdOrderByUploadedAtAsc(applicationId);
        boolean allDocumentsVerified = !documents.isEmpty() && documents.stream()
                .allMatch(document -> document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED);
        if (!allDocumentsVerified) {
            throw new SellerApplicationDecisionException(
                    "Hãy xác minh tất cả giấy tờ trước khi phê duyệt hồ sơ.");
        }

        Role storeManagerRole = roleRepository.findByRoleName("ROLE_STORE_MANAGER")
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa có ROLE_STORE_MANAGER trong bảng roles. Hãy thêm role này trước khi duyệt."));
        userRoleGrantRepository.grantIfMissing(application.getUser().getUserId(), storeManagerRole.getRoleId());

        application.setReviewedBy(admin);
        application.setReviewedAt(nowUtc());
        application.setRejectionReason(null);
        transition(application, SellerApplicationStatus.APPROVED, admin, cleanNote(note));
    }

    @Transactional
    public void reject(Long applicationId, String adminEmail, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SellerApplicationDecisionException("Vui lòng nhập lý do từ chối hồ sơ.");
        }
        if (reason.trim().length() > 1000) {
            throw new SellerApplicationDecisionException("Lý do từ chối không được dài quá 1000 ký tự.");
        }
        SellerApplication application = lockApplication(applicationId);
        requireReviewable(application);
        User admin = findAdmin(adminEmail);

        String rejectionReason = reason.trim();
        application.setReviewedBy(admin);
        application.setReviewedAt(nowUtc());
        application.setRejectionReason(rejectionReason);
        transition(application, SellerApplicationStatus.REJECTED, admin, rejectionReason);
    }

    private SellerApplication lockApplication(Long applicationId) {
        return adminQueryRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new SellerApplicationDecisionException("Không tìm thấy hồ sơ đăng ký bán hàng."));
    }

    private User findAdmin(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản admin đang đăng nhập."));
    }

    private void requireStatus(SellerApplication application, SellerApplicationStatus required) {
        if (application.getStatus() != required) {
            throw new SellerApplicationDecisionException(
                    "Chỉ hồ sơ ở trạng thái " + statusLabel(required) + " mới thực hiện được thao tác này.");
        }
    }

    private void requireReviewable(SellerApplication application) {
        if (application.getStatus() != SellerApplicationStatus.PENDING
                && application.getStatus() != SellerApplicationStatus.UNDER_REVIEW) {
            throw new SellerApplicationDecisionException(
                    "Hồ sơ đã được xử lý và không thể thay đổi quyết định thêm lần nữa.");
        }
    }

    private void transition(
            SellerApplication application,
            SellerApplicationStatus newStatus,
            User admin,
            String note
    ) {
        SellerApplicationStatus oldStatus = application.getStatus();
        application.setStatus(newStatus);
        applicationRepository.save(application);
        historyRepository.save(makeHistory(application, oldStatus, newStatus, admin, note));
    }

    private SellerApplicationStatusHistory makeHistory(
            SellerApplication application,
            SellerApplicationStatus oldStatus,
            SellerApplicationStatus newStatus,
            User admin,
            String note
    ) {
        SellerApplicationStatusHistory history = new SellerApplicationStatusHistory();
        history.setApplication(application);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(admin);
        history.setNote(note);
        return history;
    }

    private String toCloudinaryUrl(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if (reference.startsWith("https://res.cloudinary.com/")) {
            return reference;
        }
        if (cloudinaryCloudName == null || cloudinaryCloudName.isBlank()) {
            return null;
        }
        String publicId = reference.replaceFirst("^/+", "");
        String resourceType = "raw".equalsIgnoreCase(cloudinaryDocumentResourceType) ? "raw" : "image";
        return "https://res.cloudinary.com/" + cloudinaryCloudName + "/"
                + resourceType + "/upload/" + UriUtils.encodePath(publicId, StandardCharsets.UTF_8);
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String cleaned = note.trim();
        if (cleaned.length() > 1000) {
            throw new SellerApplicationDecisionException("Ghi chú không được dài quá 1000 ký tự.");
        }
        return cleaned;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? "—" : dateTime.format(DATE_FORMAT);
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
            case PENDING -> "Chưa xác minh";
            case VERIFIED -> "Đã xác minh";
            case REJECTED -> "Không hợp lệ";
        };
    }
}
