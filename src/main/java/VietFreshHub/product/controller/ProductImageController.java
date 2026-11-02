package VietFreshHub.product.controller;

import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.service.ProductImageStorageService;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ProductImageController {

    private final ShopService shopService;
    private final ProductImageStorageService productImageStorageService;

    /**
     * Kiểm tra quyền quản lý shop rồi tải ảnh lên Cloudinary; trả lỗi dữ liệu 400 hoặc lỗi upload 502.
     */
    @PostMapping("/seller/products/images")
    @ResponseBody
    public ResponseEntity<Map<String, String>> upload(
            Authentication authentication, @RequestParam("file") MultipartFile file) {
        shopService.getManagedShopId(authentication);
        try {
            String imageUrl = productImageStorageService.upload(file);
            return ResponseEntity.ok(Map.of("url", imageUrl));
        } catch (CatalogException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Không thể tải ảnh lên Cloudinary. Vui lòng thử lại."));
        }
    }
}
