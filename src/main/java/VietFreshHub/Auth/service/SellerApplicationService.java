package VietFreshHub.Auth.service;

import VietFreshHub.Auth.dto.SellerApplicationForm;
import VietFreshHub.Auth.entity.DocumentVerificationStatus;
import VietFreshHub.Auth.entity.SellerApplication;
import VietFreshHub.Auth.entity.SellerApplicationDocument;
import VietFreshHub.Auth.entity.SellerApplicationStatus;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.SellerApplicationDocumentRepository;
import VietFreshHub.Auth.repository.SellerApplicationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class SellerApplicationService {

    private static final Logger LOG =
            Logger.getLogger(SellerApplicationService.class.getName());

    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentRepository documentRepository;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final Executor cloudinaryUploadExecutor;

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
        List<UploadTask> tasks = createUploadTasks(form);

        SellerApplication newApplication = new SellerApplication();
        newApplication.setUser(user);
        newApplication.setBusinessName(form.getBusinessName().trim());
        newApplication.setTaxCode(
                form.getTaxCode() == null || form.getTaxCode().isBlank()
                        ? null
                        : form.getTaxCode().trim()
        );
        newApplication.setStatus(SellerApplicationStatus.PENDING);


        final SellerApplication application =
                applicationRepository.save(newApplication);

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

        try {
            CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            ).join();
        } catch (CompletionException uploadError) {
            LOG.log(
                    Level.SEVERE,
                    "Upload Cloudinary thất bại, applicationId="
                            + application.getApplicationId(),
                    uploadError
            );
            List<CompletedUpload> uploadedSuccessfully =
                    getSuccessfulUploads(futures);

            rollback(application, uploadedSuccessfully);

            Throwable cause = uploadError.getCause() != null
                    ? uploadError.getCause()
                    : uploadError;

            if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;
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

        try {
            // Lưu danh sách document sau khi mọi file Cloudinary đều thành công.
            documentRepository.saveAll(documents);
            return application.getApplicationId();
        } catch (RuntimeException databaseError) {
            rollback(application, uploaded);

            throw new IOException(
                    "Không lưu được thông tin tài liệu. Vui lòng gửi lại hồ sơ.",
                    databaseError
            );
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
                LOG.log(
                        Level.WARNING,
                        "Không xóa được file Cloudinary sau khi submit lỗi: "
                                + item.asset().publicId(),
                        cleanupError
                );
            }
        }

        try {
            applicationRepository.deleteById(
                    application.getApplicationId()
            );
        } catch (RuntimeException cleanupError) {
            LOG.log(
                    Level.WARNING,
                    "Không xóa được hồ sơ tạm sau khi upload lỗi",
                    cleanupError
            );
        }
    }
}