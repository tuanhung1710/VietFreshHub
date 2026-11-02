package VietFreshHub.product.service.impl;

import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.service.ProductImageStorageService;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

@Service
@RequiredArgsConstructor
public class ProductImageStorageServiceImpl implements ProductImageStorageService {

    private final Cloudinary cloudinary;

    /** Kiểm tra ảnh JPG hoặc PNG tối đa 1 MB, tải lên Cloudinary và trả URL HTTPS. */
    @Override
    public String upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 1024 * 1024) {
            throw new CatalogException("Chọn ảnh JPG hoặc PNG có dung lượng tối đa 1 MB.");
        }

        // Kiểm tra dữ liệu thật trong bộ nhớ trước khi gửi tới Cloudinary.
        byte[] bytes = file.getBytes();
        validateImage(bytes);
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "resource_type", "image",
                    "type", "upload",
                    "public_id", "vietfresh/products/" + UUID.randomUUID(),
                    "overwrite", false
            ));
            if (result == null || !(result.get("secure_url") instanceof String url)
                    || !url.startsWith("https://") || url.length() > 1000) {
                throw new IOException("Cloudinary không trả về URL ảnh HTTPS hợp lệ.");
            }
            return url;
        } catch (RuntimeException exception) {
            throw new IOException("Không thể tải ảnh lên Cloudinary.", exception);
        }
    }

    /**
     * Nhận diện định dạng từ nội dung tệp, kiểm tra kích thước và khả năng đọc trước khi lưu ảnh.
     */
    private void validateImage(byte[] bytes) {
        try (ImageInputStream stream =
                new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            // ImageIO nhận diện định dạng từ dữ liệu thật của tệp.
            if (!readers.hasNext()) {
                throw new CatalogException("Tệp đã chọn không phải ảnh JPG hoặc PNG hợp lệ.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("jpeg", "jpg", "png").contains(format)) {
                    throw new CatalogException("Chỉ hỗ trợ ảnh JPG hoặc PNG.");
                }
                // Kiểm tra số điểm ảnh trước khi giải mã toàn bộ để tránh tệp nhỏ nhưng kích thước ảnh quá lớn.
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                if (reader.read(0) == null) {
                    throw new CatalogException("Không thể đọc ảnh đã chọn. Hãy chọn ảnh khác.");
                }
            } finally {
                // Giải phóng bộ giải mã cả khi kiểm tra định dạng hoặc đọc ảnh thất bại.
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new CatalogException("Không thể đọc ảnh đã chọn. Hãy chọn ảnh JPG hoặc PNG hợp lệ.");
        }
    }

    /** Giới hạn chiều ảnh và tổng số điểm ảnh để tránh xử lý ảnh quá lớn. */
    private void validateDimensions(int width, int height) {
        long pixelCount = (long) width * height;
        if (width > 10000 || height > 10000 || pixelCount > 25000000) {
            throw new CatalogException("Ảnh quá lớn để xử lý. Hãy chọn ảnh nhỏ hơn.");
        }
    }
}
