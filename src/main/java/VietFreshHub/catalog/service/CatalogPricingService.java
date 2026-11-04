package VietFreshHub.catalog.service;

import VietFreshHub.catalog.dto.CatalogOffer;

import java.util.List;
import java.util.Map;

/** Dùng cùng giá và tồn khả dụng theo lô cho catalog và giỏ hàng. */
public interface CatalogPricingService {
    Map<Long, CatalogOffer> offers(List<Long> variantIds);
}
