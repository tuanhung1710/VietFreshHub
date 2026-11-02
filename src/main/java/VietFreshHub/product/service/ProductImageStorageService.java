package VietFreshHub.product.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProductImageStorageService {

    /** Kiểm tra và tải ảnh lên Cloudinary, trả URL HTTPS để lưu vào sản phẩm. */
    String upload(MultipartFile file) throws IOException;
}
