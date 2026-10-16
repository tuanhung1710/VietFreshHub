package VietFreshHub.product.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface ProductImageStorageService {

    Path getDirectory();

    String upload(MultipartFile file) throws IOException;
}
