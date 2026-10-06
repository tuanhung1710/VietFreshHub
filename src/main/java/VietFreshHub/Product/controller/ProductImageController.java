package VietFreshHub.Product.controller;
import VietFreshHub.Product.service.ProductImageStorageService;
import VietFreshHub.Shop.service.ShopService;
import VietFreshHub.Product.exception.CatalogException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.Map;
@Controller @RequiredArgsConstructor
public class ProductImageController {
    private final ShopService shops;
    private final ProductImageStorageService storage;
    @PostMapping("/seller/products/images") @ResponseBody
    public ResponseEntity<Map<String,String>> upload(Authentication a,@RequestParam MultipartFile file) throws IOException {
        shops.getManagedShopId(a);
        try{return ResponseEntity.ok(Map.of("url",storage.upload(file)));}
        catch(CatalogException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    }
}
