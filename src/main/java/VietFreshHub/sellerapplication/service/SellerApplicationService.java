package VietFreshHub.sellerapplication.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.sellerapplication.dto.SellerApplicationForm;
import VietFreshHub.sellerapplication.entity.DocumentVerificationStatus;
import VietFreshHub.sellerapplication.entity.SellerApplication;
import VietFreshHub.sellerapplication.entity.SellerApplicationDocument;
import VietFreshHub.sellerapplication.entity.SellerApplicationStatus;
import VietFreshHub.sellerapplication.repository.SellerApplicationDocumentRepository;
import VietFreshHub.sellerapplication.repository.SellerApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SellerApplicationService {

    public static final String IDENTITY_CARD = "IDENTITY_CARD";
    public static final String BUSINESS_LICENSE = "BUSINESS_LICENSE";

    private static final Duration STAGED_UPLOAD_TTL = Duration.ofHours(12);

    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentRepository documentRepository;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final Executor cloudinaryUploadExecutor;
    private final ConcurrentMap<String, PendingUpload> stagedUploads =
            new ConcurrentHashMap<>();

    public record StagedDocument(
            String uploadToken,
            String previewUrl,
            String fileName,
            String contentType
    ) {}

    private record PendingUpload(
            Long userId,
            String documentType,
            CloudinaryStorageService.UploadedAsset asset,
            String fileName,
            String contentType,
            Instant expiresAt
    ) {}

    public SellerApplicationService(
            SellerApplicationRepository applicationRepository,
            SellerApplicationDocumentRepository documentRepository,
            CloudinaryStorageService cloudinaryStorageService,
            @Qualifier("cloudinaryUploadExecutor") Executor cloudinaryUploadExecutor
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.cloudinaryStorageService = cloudinaryStorageService;
        this.cloudinaryUploadExecutor = cloudinaryUploadExecutor;
    }

    private record UploadTask(
            String documentType,
            MultipartFile file,
            boolean imagesOnly
    ) {}

    private record CompletedUpload(
            String documentType,
            CloudinaryStorageService.UploadedAsset asset
    ) {}

    public Long submit(User user, SellerApplicationForm form) throws IOException {
        long submitStartedAt = System.nanoTime();
        List<UploadTask> tasks = createUploadTasks(form);

        // Reject invalid documents before creating a temporary application row.
        tasks.forEach(task -> cloudinaryStorageService.validateUpload(
                task.file(), task.imagesOnly()
        ));

        SellerApplication newApplication = new SellerApplication();
        newApplication.setUser(user);
        newApplication.setBusinessName(form.getBusinessName().trim());
        newApplication.setTaxCode(
                form.getTaxCode() == null || form.getTaxCode().isBlank()
                        ? null
                        : form.getTaxCode().trim()
        );
        newApplication.setStatus(SellerApplicationStatus.PENDING);

        long applicationSaveStartedAt = System.nanoTime();
        final SellerApplication application = applicationRepository.save(newApplication);
        log.info(
                "Seller application row saved: applicationId={}, durationMs={}",
                application.getApplicationId(),
                elapsedMillis(applicationSaveStartedAt)
        );

        List<CompletableFuture<CompletedUpload>> futures = new ArrayList<>();

        for (UploadTask task : tasks) {
            SellerApplication savedApplication = application;

            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    CloudinaryStorageService.UploadedAsset asset =
                            cloudinaryStorageService.uploadPrivate(
                                    task.file(),
                                    savedApplication.getApplicationId(),
                                    task.documentType(),
                                    task.imagesOnly()
                            );

                    return new CompletedUpload(task.documentType(), asset);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, cloudinaryUploadExecutor));
        }

        long cloudinaryWaitStartedAt = System.nanoTime();
        try {
            CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            ).join();
            log.info(
                    "Seller application Cloudinary uploads completed: applicationId={}, durationMs={}",
                    application.getApplicationId(),
                    elapsedMillis(cloudinaryWaitStartedAt)
            );
        } catch (CompletionException uploadError) {
            log.warn(
                    "Seller application Cloudinary uploads failed: applicationId={}, durationMs={}",
                    application.getApplicationId(),
                    elapsedMillis(cloudinaryWaitStartedAt)
            );
            List<CompletedUpload> uploadedSuccessfully =
                    getSuccessfulUploads(futures);

            rollback(application, uploadedSuccessfully);

            Throwable cause = uploadError.getCause() != null
                    ? uploadError.getCause()
                    : uploadError;

            if (cause instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }

            throw new IOException(
                    "Không thể tải tài liệu lên Cloudinary. Vui lòng thử lại.",
                    cause
            );
        }

        List<CompletedUpload> uploaded =
                futures.stream().map(CompletableFuture::join).toList();

        List<SellerApplicationDocument> documents = uploaded.stream()
                .map(item -> {
                    SellerApplicationDocument document =
                            new SellerApplicationDocument();

                    document.setApplication(application);
                    document.setDocumentType(item.documentType());
                    document.setCloudinaryPublicId(item.asset().publicId());
                    document.setCloudinaryResourceType(item.asset().resourceType());
                    document.setCloudinaryFormat(item.asset().format());
                    document.setVerificationStatus(
                            DocumentVerificationStatus.PENDING
                    );

                    return document;
                })
                .toList();

        long documentSaveStartedAt = System.nanoTime();
        try {
            // Lưu danh sách document sau khi mọi file Cloudinary đều thành công.
            documentRepository.saveAll(documents);
            log.info(
                    "Seller application documents saved: applicationId={}, durationMs={}, totalDurationMs={}",
                    application.getApplicationId(),
                    elapsedMillis(documentSaveStartedAt),
                    elapsedMillis(submitStartedAt)
            );
            return application.getApplicationId();
        } catch (RuntimeException databaseError) {
            log.warn(
                    "Seller application documents could not be saved: applicationId={}, durationMs={}, totalDurationMs={}",
                    application.getApplicationId(),
                    elapsedMillis(documentSaveStartedAt),
                    elapsedMillis(submitStartedAt)
            );
            rollback(application, uploaded);

            throw new IOException(
                    "Không lưu được thông tin tài liệu. Vui lòng gửi lại hồ sơ.",
                    databaseError
            );
        }
    }

    public StagedDocument stageDocument(
            User user,
            MultipartFile file,
            String documentType,
            String previousUploadToken
    ) throws IOException {
        validateDocumentType(documentType);
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Không xác định được tài khoản đăng ký.");
        }

        boolean imagesOnly = IDENTITY_CARD.equals(documentType);
        CloudinaryStorageService.UploadedAsset asset =
                cloudinaryStorageService.uploadPrivateStaged(
                        file,
                        user.getUserId(),
                        documentType,
                        imagesOnly
                );

        String previewUrl;
        try {
            previewUrl = cloudinaryStorageService.createPrivatePreviewUrl(
                    asset.publicId(),
                    asset.resourceType(),
                    asset.format()
            );
        } catch (RuntimeException exception) {
            deleteStagedAsset(asset);
            throw exception;
        }

        String uploadToken = UUID.randomUUID().toString();
        String fileName = safeFileName(file);
        PendingUpload pendingUpload = new PendingUpload(
                user.getUserId(),
                documentType,
                asset,
                fileName,
                file.getContentType(),
                Instant.now().plus(STAGED_UPLOAD_TTL)
        );
        stagedUploads.put(uploadToken, pendingUpload);

        if (previousUploadToken != null && !previousUploadToken.isBlank()) {
            discardStagedDocument(user.getUserId(), documentType, previousUploadToken);
        }

        return new StagedDocument(
                uploadToken,
                previewUrl,
                fileName,
                pendingUpload.contentType()
        );
    }

    public StagedDocument getStagedDocument(
            User user,
            String uploadToken,
            String expectedDocumentType
    ) {
        if (user == null || user.getUserId() == null) {
            return null;
        }

        PendingUpload pendingUpload = findPendingUpload(
                user.getUserId(),
                uploadToken,
                expectedDocumentType
        );
        if (pendingUpload == null) {
            return null;
        }

        String previewUrl = null;
        try {
            previewUrl = cloudinaryStorageService.createPrivatePreviewUrl(
                    pendingUpload.asset().publicId(),
                    pendingUpload.asset().resourceType(),
                    pendingUpload.asset().format()
            );
        } catch (RuntimeException exception) {
            log.warn("Không tạo được link xem trước tài liệu tạm trên Cloudinary.");
        }

        return new StagedDocument(
                uploadToken,
                previewUrl,
                pendingUpload.fileName(),
                pendingUpload.contentType()
        );
    }

    public void discardStagedDocument(
            Long userId,
            String documentType,
            String uploadToken
    ) {
        if (userId == null || uploadToken == null || uploadToken.isBlank()) {
            return;
        }

        PendingUpload pendingUpload = findPendingUpload(
                userId,
                uploadToken,
                documentType
        );
        if (pendingUpload != null
                && stagedUploads.remove(uploadToken, pendingUpload)) {
            deleteStagedAsset(pendingUpload.asset());
        }
    }

    public Long submitStaged(
            User user,
            SellerApplicationForm form,
            String identityCardUploadToken,
            String businessLicenseUploadToken
    ) throws IOException {
        PendingUpload identityCard = requirePendingUpload(
                user,
                identityCardUploadToken,
                IDENTITY_CARD,
                "Vui lòng tải giấy tờ tùy thân lên lại."
        );
        PendingUpload businessLicense = requirePendingUpload(
                user,
                businessLicenseUploadToken,
                BUSINESS_LICENSE,
                "Vui lòng tải giấy phép hoặc chứng nhận lên lại."
        );

        SellerApplication application = new SellerApplication();
        application.setUser(user);
        application.setBusinessName(form.getBusinessName().trim());
        application.setTaxCode(
                form.getTaxCode() == null || form.getTaxCode().isBlank()
                        ? null
                        : form.getTaxCode().trim()
        );
        application.setStatus(SellerApplicationStatus.PENDING);
        application = applicationRepository.save(application);

        List<SellerApplicationDocument> documents = List.of(
                createDocument(application, IDENTITY_CARD, identityCard.asset()),
                createDocument(application, BUSINESS_LICENSE, businessLicense.asset())
        );

        try {
            documentRepository.saveAll(documents);
        } catch (RuntimeException databaseError) {
            rollback(application, List.of());
            throw new IOException(
                    "Không lưu được thông tin tài liệu. Vui lòng gửi lại hồ sơ.",
                    databaseError
            );
        }

        stagedUploads.remove(identityCardUploadToken, identityCard);
        stagedUploads.remove(businessLicenseUploadToken, businessLicense);
        return application.getApplicationId();
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void cleanupExpiredStagedUploads() {
        Instant now = Instant.now();
        stagedUploads.forEach((uploadToken, pendingUpload) -> {
            if (!pendingUpload.expiresAt().isAfter(now)
                    && stagedUploads.remove(uploadToken, pendingUpload)) {
                deleteStagedAsset(pendingUpload.asset());
            }
        });
    }

    private PendingUpload requirePendingUpload(
            User user,
            String uploadToken,
            String expectedDocumentType,
            String errorMessage
    ) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Không xác định được tài khoản đăng ký.");
        }
        PendingUpload pendingUpload = findPendingUpload(
                user.getUserId(),
                uploadToken,
                expectedDocumentType
        );
        if (pendingUpload == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return pendingUpload;
    }

    private PendingUpload findPendingUpload(
            Long userId,
            String uploadToken,
            String expectedDocumentType
    ) {
        if (uploadToken == null || uploadToken.isBlank()) {
            return null;
        }
        PendingUpload pendingUpload = stagedUploads.get(uploadToken);
        if (pendingUpload == null) {
            return null;
        }
        if (!pendingUpload.expiresAt().isAfter(Instant.now())) {
            if (stagedUploads.remove(uploadToken, pendingUpload)) {
                deleteStagedAsset(pendingUpload.asset());
            }
            return null;
        }
        if (!pendingUpload.userId().equals(userId)
                || !pendingUpload.documentType().equals(expectedDocumentType)) {
            return null;
        }
        return pendingUpload;
    }

    private void validateDocumentType(String documentType) {
        if (!IDENTITY_CARD.equals(documentType)
                && !BUSINESS_LICENSE.equals(documentType)) {
            throw new IllegalArgumentException("Loại tài liệu không hợp lệ.");
        }
    }

    private SellerApplicationDocument createDocument(
            SellerApplication application,
            String documentType,
            CloudinaryStorageService.UploadedAsset asset
    ) {
        SellerApplicationDocument document = new SellerApplicationDocument();
        document.setApplication(application);
        document.setDocumentType(documentType);
        document.setCloudinaryPublicId(asset.publicId());
        document.setCloudinaryResourceType(asset.resourceType());
        document.setCloudinaryFormat(asset.format());
        document.setVerificationStatus(DocumentVerificationStatus.PENDING);
        return document;
    }

    private String safeFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            return "Tài liệu";
        }
        int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(separator + 1);
    }

    private void deleteStagedAsset(CloudinaryStorageService.UploadedAsset asset) {
        try {
            cloudinaryStorageService.deletePrivate(asset);
        } catch (IOException | RuntimeException cleanupError) {
            log.warn("Không xóa được tài liệu tạm trên Cloudinary.", cleanupError);
        }
    }

    private List<UploadTask> createUploadTasks(SellerApplicationForm form) {
        if (form.getIdentityCard() == null || form.getIdentityCard().isEmpty()) {
            throw new IllegalArgumentException(
                    "Vui lòng tải giấy tờ tùy thân"
            );
        }

        if (form.getBusinessLicense() == null
                || form.getBusinessLicense().isEmpty()) {
            throw new IllegalArgumentException(
                    "Vui lòng tải giấy phép hoặc chứng nhận"
            );
        }

        return List.of(
                new UploadTask(
                        "IDENTITY_CARD",
                        form.getIdentityCard(),
                        true
                ),
                new UploadTask(
                        "BUSINESS_LICENSE",
                        form.getBusinessLicense(),
                        false
                )
        );
    }

    private List<CompletedUpload> getSuccessfulUploads(
            List<CompletableFuture<CompletedUpload>> futures
    ) {
        return futures.stream()
                .filter(future ->
                        future.isDone()
                                && !future.isCompletedExceptionally()
                                && !future.isCancelled()
                )
                .map(CompletableFuture::join)
                .toList();
    }

    private void rollback(
            SellerApplication application,
            List<CompletedUpload> uploaded
    ) {
        for (CompletedUpload item : uploaded) {
            try {
                cloudinaryStorageService.deletePrivate(item.asset());
            } catch (Exception cleanupError) {
                log.warn("Không xóa được file Cloudinary sau khi submit lỗi", cleanupError);
            }
        }

        try {
            applicationRepository.deleteById(
                    application.getApplicationId()
            );
        } catch (RuntimeException cleanupError) {
            log.warn("Không xóa được hồ sơ tạm sau khi upload lỗi", cleanupError);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
