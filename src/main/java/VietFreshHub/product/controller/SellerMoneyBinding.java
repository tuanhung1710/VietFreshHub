package VietFreshHub.product.controller;

import VietFreshHub.common.VndAmountFormatter;
import VietFreshHub.inventory.controller.InventoryController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice(assignableTypes = {ProductController.class, InventoryController.class})
@RequiredArgsConstructor
public class SellerMoneyBinding {
    private final VndAmountFormatter amounts;

    @InitBinder
    void bindAmounts(WebDataBinder binder) {
        binder.addCustomFormatter(amounts, "price", "compareAtPrice", "costPrice",
                "variants.price", "variants.compareAtPrice", "variants.costPrice");
    }
}
