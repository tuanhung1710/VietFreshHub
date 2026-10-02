package VietFreshHub.Auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private final Cloudinary cloudinary;

    public record UploadedAsset(String publicId, String resourceType) {}

    public UploadedAsset uploadPrivate(
            MultipartFile file,
            Long applicationId,
            String documentType,
            boolean imagesOnly
    ) throws IOException {
        validate(file, imagesOnly);

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

        if (publicId == null || resourceType == null) {
            throw new IOException("Cloudinary không trả về thông tin file hợp lệ");
        }

        return new UploadedAsset(publicId, resourceType);
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
                    "Mỗi file không được vượt quá 10 MB"
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
}