package VietFreshHub.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ProductImageWebConfig implements WebMvcConfigurer {

    private final Path legacyUploadDirectory;

    public ProductImageWebConfig(
            @Value("${vietfresh.product.upload-directory:uploads/product}")
                    String uploadDirectory) {
        legacyUploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = legacyUploadDirectory.toUri().toString();
        registry.addResourceHandler("/images/product-upload/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
