package VietFreshHub.product.service;

import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.entity.ProductVariant;

import java.util.List;
import java.util.Map;

public interface ProductAvailabilityService {

    List<Long> activeCategoryIds();

    Map<Long, ProductManagementState> states(Long shopId, List<Long> productIds);

    boolean canSellVariant(Long shopId, ProductVariant variant);
}
