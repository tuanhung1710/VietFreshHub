package VietFreshHub.Product.config;
import VietFreshHub.Product.service.ProductImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration @RequiredArgsConstructor
public class ProductImageWebConfig implements WebMvcConfigurer {
    private final ProductImageStorageService storage;
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/product-upload/**").addResourceLocations(storage.getDirectory().toUri().toString()+"/");
    }
}
