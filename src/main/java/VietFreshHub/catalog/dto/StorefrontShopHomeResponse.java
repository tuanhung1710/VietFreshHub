package VietFreshHub.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class StorefrontShopHomeResponse {
    private long productCount;
    private List<CatalogProductResponse> recommended;
    private List<CatalogProductResponse> topProducts;
}
