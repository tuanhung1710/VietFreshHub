package VietFreshHub.Product.service;
import VietFreshHub.Product.exception.CatalogException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.util.*;
@Service
public class ProductImageStorageService {
    @Getter private final Path directory;
    public ProductImageStorageService(@Value("${vietfresh.product.upload-directory:uploads/product}") String path) {
        directory=Path.of(path).toAbsolutePath().normalize();
    }
    public String upload(MultipartFile file) throws IOException {
        if(file.isEmpty()||file.getSize()>1024*1024)throw new CatalogException("Chọn ảnh JPG/PNG tối đa 1 MB.");
        byte[] bytes=file.getBytes();String extension;
        try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new CatalogException("Tệp không phải ảnh JPG/PNG hợp lệ.");
            ImageReader reader=readers.next();
            try {
                reader.setInput(stream);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!List.of("jpeg","jpg","png").contains(format))throw new CatalogException("Chỉ hỗ trợ JPG/PNG.");
                if(reader.getWidth(0)>10000||reader.getHeight(0)>10000||(long)reader.getWidth(0)*reader.getHeight(0)>25000000)throw new CatalogException("Ảnh quá lớn, hãy chọn ảnh nhỏ hơn.");
                if(reader.read(0)==null)throw new CatalogException("Không đọc được ảnh.");
                extension=format.equals("png")?".png":".jpg";
            }finally{reader.dispose();}
        }
        Files.createDirectories(directory);
        String name=UUID.randomUUID()+extension;
        Files.write(directory.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
        return "/images/product-upload/"+name;
    }
}
