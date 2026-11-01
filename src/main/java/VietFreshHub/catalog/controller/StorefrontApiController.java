package VietFreshHub.catalog.controller;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.StorefrontProduct;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.common.PageResponse;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/storefront/api")
public class StorefrontApiController {
    private final StorefrontService storefront;

    @GetMapping("/options")
    public StorefrontService.Options options() { return storefront.options(); }

    @GetMapping("/home")
    public StorefrontService.Home home(@Valid @ModelAttribute CatalogBrowseFilter filter) { return storefront.home(filter); }

    @GetMapping("/products")
    public PageResponse<StorefrontProduct> products(@Valid @ModelAttribute CatalogBrowseFilter filter) { return storefront.products(filter); }

    @GetMapping("/products/{id}")
    public StorefrontProduct product(@PathVariable Long id, @RequestParam(required = false) Long variantId) {
        return storefront.product(id, variantId);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(ResourceNotFoundException exception) { return Map.of("message", exception.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException exception) { return Map.of("message", exception.getMessage()); }
}
