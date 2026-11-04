package VietFreshHub.catalog.dto;

import VietFreshHub.common.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

/** Phản hồi JSON tương thích API Home; view sử dụng Page trực tiếp. */
@Getter
@AllArgsConstructor
public class StorefrontHomeResponse {
    private PageResponse<StorefrontProduct> products;
    private List<StorefrontProduct> topProducts;
}
