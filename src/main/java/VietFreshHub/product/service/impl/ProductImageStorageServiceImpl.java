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

    public ProductImageStorageServiceImpl(
            @Value("${vietfresh.product.upload-directory:uploads/product}")
                    String uploadDirectory) {
        directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public Path getDirectory() {
        return directory;
    }

    @Override
    public String upload(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 1024 * 1024) {
            throw new CatalogException("Chọn ảnh JPG hoặc PNG có dung lượng tối đa 1 MB.");
        }

        byte[] bytes = file.getBytes();
        String extension = validateImage(bytes);
        String filename = UUID.randomUUID() + extension;
        Files.createDirectories(directory);
        Files.write(directory.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
        return "/images/product-upload/" + filename;
    }

    private String validateImage(byte[] bytes) throws IOException {
        try (ImageInputStream stream =
                ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
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
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                if (reader.read(0) == null) {
                    throw new CatalogException("Không thể đọc ảnh đã chọn. Hãy chọn ảnh khác.");
                }
                if (format.equals("png")) {
                    return ".png";
                }
                return ".jpg";
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(int width, int height) {
        long pixelCount = (long) width * height;
        if (width > 10000 || height > 10000 || pixelCount > 25000000) {
            throw new CatalogException("Ảnh quá lớn để xử lý. Hãy chọn ảnh nhỏ hơn.");
        }
    }
}
