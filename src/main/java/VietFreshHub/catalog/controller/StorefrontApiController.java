package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.StorefrontProduct;
import VietFreshHub.catalog.dto.StorefrontOptionsResponse;
import VietFreshHub.catalog.dto.StorefrontHomeResponse;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.common.PageResponse;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/storefront/api")
public class StorefrontApiController {
    private final StorefrontService storefrontService;

    @GetMapping("/options")
    public StorefrontOptionsResponse options() {
        return storefrontService.options();
    }

    @GetMapping("/home")
    public StorefrontHomeResponse home(@Valid @ModelAttribute CatalogBrowseFilter filter) {
        return storefrontService.home(filter);
    }

    @GetMapping("/products")
    public PageResponse<StorefrontProduct> products(@Valid @ModelAttribute CatalogBrowseFilter filter) {
        return storefrontService.products(filter);
    }

    @GetMapping("/products/{id}")
    public StorefrontProduct product(@PathVariable Long id, @RequestParam(required = false) Long variantId) {
        return storefrontService.product(id, variantId);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(ResourceNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }
}
