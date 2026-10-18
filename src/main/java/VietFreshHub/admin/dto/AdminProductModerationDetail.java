package VietFreshHub.admin.dto;

import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductImage;
import VietFreshHub.product.entity.ProductVariant;

import java.util.List;

public record AdminProductModerationDetail(
        Product product,
        String shopName,
        List<ProductVariant> variants,
        List<ProductImage> images
) {}
