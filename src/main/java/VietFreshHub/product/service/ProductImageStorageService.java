package VietFreshHub.product.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface ProductImageStorageService {

    /** Lấy thư mục lưu ảnh sản phẩm trên máy chủ. */
    Path getDirectory();

    /** Kiểm tra và lưu ảnh tải lên, trả đường dẫn truy cập ảnh. */
    String upload(MultipartFile file) throws IOException;
}
