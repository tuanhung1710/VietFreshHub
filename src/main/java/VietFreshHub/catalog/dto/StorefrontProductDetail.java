package VietFreshHub.catalog.dto;

import VietFreshHub.shop.entity.Shop;

import java.time.LocalDate;
import java.util.List;

/** Dữ liệu của trang chi tiết được service nạp và kiểm tra trước khi đưa vào Model. */
public record StorefrontProductDetail(
        CatalogProductResponse product,
        Shop shop,
        StorefrontProductDetails details,
        String formattedAddress,
        LocalDate joinedDate,
        long shopProductCount,
        List<CatalogProductResponse> related) {}
