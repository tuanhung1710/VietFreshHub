package VietFreshHub.product.service.impl;

import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.service.ProductImageStorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
public class ProductImageStorageServiceImpl implements ProductImageStorageService {

    private final Path directory;

    /** Chuẩn hóa thư mục tải ảnh trong cấu hình thành đường dẫn tuyệt đối. */
    public ProductImageStorageServiceImpl(
            @Value("${vietfresh.product.upload-directory:uploads/product}")
                    String uploadDirectory) {
        directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    /** Trả về thư mục lưu ảnh để cấu hình đường dẫn phục vụ ảnh qua HTTP. */
    @Override
    public Path getDirectory() {
        return directory;
    }

    /** Kiểm tra ảnh JPG hoặc PNG tối đa 1 MB, lưu tên ngẫu nhiên và trả về đường dẫn truy cập. */
    @Override
    public String upload(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 1024 * 1024) {
            throw new CatalogException("Chọn ảnh JPG hoặc PNG có dung lượng tối đa 1 MB.");
        }

        // Xác minh nội dung ảnh trước khi ghi; không dựa vào tên tệp hay phần mở rộng do trình duyệt gửi.
        byte[] bytes = file.getBytes();
        String extension = validateImage(bytes);
        // Tên ngẫu nhiên và CREATE_NEW giúp tránh ghi đè ảnh đã tồn tại.
        String filename = UUID.randomUUID() + extension;
        Files.createDirectories(directory);
        Files.write(directory.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
        return "/images/product-upload/" + filename;
    }

    /**
     * Nhận diện định dạng từ nội dung tệp, kiểm tra kích thước và khả năng đọc trước khi lưu ảnh.
     */
    private String validateImage(byte[] bytes) throws IOException {
        try (ImageInputStream stream =
                ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
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
                if (format.equals("png")) {
                    return ".png";
                }
                return ".jpg";
            } finally {
                // Giải phóng bộ giải mã cả khi kiểm tra định dạng hoặc đọc ảnh thất bại.
                reader.dispose();
            }
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
