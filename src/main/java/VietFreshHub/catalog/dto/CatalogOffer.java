package VietFreshHub.catalog.dto;

import java.math.BigDecimal;

/** Giá bán và tồn khả dụng dùng chung cho catalog và giỏ hàng. */
public record CatalogOffer(BigDecimal price, int availableStock) {}
