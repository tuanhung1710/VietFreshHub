package VietFreshHub.sellerapplication.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

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

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    public record UploadedAsset(
            String publicId,
            String resourceType,
            String format
    ) {}

    public record UploadedAvatar(String secureUrl, String publicId) {}

    public UploadedAvatar uploadPublicAvatar(MultipartFile file) {
        validateAvatar(file);
        String generatedPublicId = "vietfresh/avatars/" + UUID.randomUUID();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "type", "upload",
                            "public_id", generatedPublicId,
                            "overwrite", false
                    )
            );
            String secureUrl = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            if (secureUrl == null || publicId == null) {
                throw new IOException("Cloudinary không trả về đủ thông tin ảnh đại diện");
            }
            return new UploadedAvatar(secureUrl, publicId);
        } catch (IOException | RuntimeException exception) {
            log.warn("Cloudinary avatar upload failed: sizeBytes={}", file.getSize());
            throw new IllegalStateException("Không thể tải ảnh đại diện lên. Vui lòng thử lại.", exception);
        }
    }

    public void deleteUploadedAvatar(String publicId) {
        if (publicId == null || !publicId.startsWith("vietfresh/avatars/")) {
            return;
        }
        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "type", "upload",
                            "invalidate", true
                    )
            );
        } catch (Exception exception) {
            log.warn("Could not remove an unused Cloudinary avatar asset.");
        }
    }

    public void deleteManagedAvatarByUrl(String avatarUrl) {
        String publicId = extractManagedAvatarPublicId(avatarUrl);
        if (publicId != null) {
            deleteUploadedAvatar(publicId);
        }
    }

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

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa chọn ảnh đại diện.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Ảnh đại diện không được vượt quá 5 MB.");
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("Ảnh đại diện chỉ chấp nhận định dạng JPG hoặc PNG.");
        }
        try (InputStream inputStream = file.getInputStream()) {
            var image = ImageIO.read(inputStream);
            if (image == null || (long) image.getWidth() * image.getHeight() > 40_000_000L) {
                throw new IllegalArgumentException("File tải lên không phải ảnh JPG/PNG hợp lệ.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc ảnh đại diện.", exception);
        }
    }

    private String extractManagedAvatarPublicId(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank() || cloudName == null || cloudName.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(avatarUrl);
            String expectedPrefix = "/" + cloudName + "/image/upload/";
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    || !uri.getPath().startsWith(expectedPrefix)) {
                return null;
            }

            String assetPath = uri.getPath().substring(expectedPrefix.length());
            if (assetPath.matches("v\\d+/.*")) {
                assetPath = assetPath.substring(assetPath.indexOf('/') + 1);
            }
            int extensionIndex = assetPath.lastIndexOf('.');
            if (extensionIndex >= 0) {
                assetPath = assetPath.substring(0, extensionIndex);
            }
            return assetPath.startsWith("vietfresh/avatars/") ? assetPath : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
