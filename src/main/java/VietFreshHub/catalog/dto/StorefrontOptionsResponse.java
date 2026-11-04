package VietFreshHub.catalog.dto;

import VietFreshHub.catalog.repository.StorefrontShopRepository.ShopOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

/** Dữ liệu lựa chọn của API storefront cũ; các trang MVC nhận model riêng. */
@Getter
@AllArgsConstructor
public class StorefrontOptionsResponse {
    private List<StorefrontCategoryResponse> categories;
    private List<ShopOption> shops;
    private List<Integer> pageSizes;
    private int defaultPageSize;
}
