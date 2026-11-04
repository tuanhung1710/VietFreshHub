package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.List;

/** Chuyển các liên kết đã lưu trước đây sang route MVC hiện tại. */
@Controller
public class StorefrontLegacyController {

    @GetMapping(value = {"/home", "/customer/home"}, params = "view=catalog")
    public String catalog(@ModelAttribute CatalogBrowseFilter filters) {
        return "redirect:" + productListUrl("/products", filters, false);
    }

    @GetMapping(value = "/shops/{shopId}", params = "tab=all")
    public String shopProducts(@PathVariable Long shopId, @ModelAttribute CatalogBrowseFilter filters) {
        return "redirect:" + productListUrl("/shops/" + shopId + "/products", filters, true);
    }

    private String productListUrl(String path, CatalogBrowseFilter filters, boolean shopPage) {
        var url = UriComponentsBuilder.fromPath(path)
                .queryParam("q", filters.getKeyword()).queryParam("category", filters.getCategoryId())
                .queryParam("page", filters.getPage()).queryParam("size", filters.getPageSize())
                .queryParam("sort", filters.getSort()).queryParam("minRating", filters.getMinRating());
        if (!shopPage) {
            List<Long> shopIds = filters.getShopId() == null ? filters.getShopIds() : List.of(filters.getShopId());
            if (!shopIds.isEmpty()) url.queryParam("shopIds", shopIds);
        }
        if (!filters.getPlaces().isEmpty()) url.queryParam("places", filters.getPlaces());
        if (filters.getMinPrice() != null) url.queryParam("minPrice", filters.getMinPrice());
        if (filters.getMaxPrice() != null) url.queryParam("maxPrice", filters.getMaxPrice());
        return url.build().encode().toUriString();
    }
}
