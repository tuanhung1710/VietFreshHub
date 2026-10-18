package VietFreshHub.product.config;

import VietFreshHub.product.service.ProductImageStorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ProductImageWebConfig implements WebMvcConfigurer {

    private final ProductImageStorageService productImageStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation =
                productImageStorageService.getDirectory().toUri().toString() + "/";
        registry.addResourceHandler("/images/product-upload/**")
                .addResourceLocations(resourceLocation);
    }
}
