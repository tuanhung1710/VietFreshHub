package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.StorefrontCatalogFilter;
import VietFreshHub.catalog.service.StorefrontService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class StorefrontCatalogController {

    private final StorefrontService storefrontService;

    /** Hiển thị danh sách sản phẩm và bộ lọc trên toàn sàn. */
    @GetMapping
    public String list(
            @Valid @ModelAttribute("filters") StorefrontCatalogFilter filters,
            BindingResult errors,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model)
            throws BindException {
        if (errors.hasErrors()) {
            throw new BindException(errors);
        }
        model.addAttribute("rows", storefrontService.searchProducts(filters, page, size));
        model.addAttribute("shops", storefrontService.shops());
        model.addAttribute("shippingPlaces", storefrontService.shippingPlaces());
        model.addAttribute("catalogTitle", storefrontService.categoryName(filters.getCategory()));
        model.addAttribute("q", filters.getQ());
        model.addAttribute("category", filters.getCategory());
        return "customer/catalog";
    }
}
