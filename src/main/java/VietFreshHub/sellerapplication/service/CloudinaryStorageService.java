package VietFreshHub.sellerapplication.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private final Cloudinary cloudinary;

    public record UploadedAsset(
            String publicId,
            String resourceType,
            String format
    ) {}

    public UploadedAsset uploadPrivate(
            MultipartFile file,
            Long applicationId,
            String documentType,
            boolean imagesOnly
    ) throws IOException {
        long startedAt = System.nanoTime();
        try {
            validateUpload(file, imagesOnly);

            String folder = "vietfresh/seller-applications/"
                    + applicationId + "/" + documentType;

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "auto",
                            "type", "authenticated",
                            "folder", folder,
                            "overwrite", false
                    )
            );

            String publicId = (String) result.get("public_id");
            String resourceType = (String) result.get("resource_type");
            String format = (String) result.get("format");

            if (publicId == null || resourceType == null || format == null) {
                throw new IOException("Cloudinary không trả về đủ thông tin file");
            }

            log.info(
                    "Cloudinary upload completed: applicationId={}, documentType={}, sizeBytes={}, durationMs={}",
                    applicationId,
                    documentType,
                    file.getSize(),
                    elapsedMillis(startedAt)
            );

            return new UploadedAsset(publicId, resourceType, format);
        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "Cloudinary upload failed: applicationId={}, documentType={}, sizeBytes={}, durationMs={}",
                    applicationId,
                    documentType,
                    file == null ? 0 : file.getSize(),
                    elapsedMillis(startedAt)
            );
            throw exception;
        }
    }

    public void validateUpload(MultipartFile file, boolean imagesOnly) {
        validate(file, imagesOnly);
    }
    public String createPrivateDownloadUrl(
            String publicId,
            String resourceType,
            String format
    ) {
        try {
            long expiresAt = Instant.now().plusSeconds(600).getEpochSecond();

            return cloudinary.privateDownload(
                    publicId,
                    format,
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "type", "authenticated",
                            "expires_at", expiresAt
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không tạo được link xem giấy tờ trên Cloudinary", e
            );
        }
    }
    public void deletePrivate(UploadedAsset asset) throws IOException {
        cloudinary.uploader().destroy(
                asset.publicId(),
                ObjectUtils.asMap(
                        "resource_type", asset.resourceType(),
                        "type", "authenticated"
                )
        );
    }

    private void validate(MultipartFile file, boolean imagesOnly) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa chọn file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Mỗi giấy tờ không được vượt quá 5 MB"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận PDF, JPG hoặc PNG"
            );
        }

        if (imagesOnly && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Tài liệu này chỉ chấp nhận JPG hoặc PNG"
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
