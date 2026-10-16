package VietFreshHub.promotion.controller;

import VietFreshHub.shop.service.ShopService;
import VietFreshHub.promotion.dto.PromotionForm;
import VietFreshHub.promotion.dto.PromotionResponse;
import VietFreshHub.promotion.entity.DiscountType;
import VietFreshHub.promotion.entity.PromotionStatus;
import VietFreshHub.promotion.services.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final ShopService shopService;

    @GetMapping
    public String list(Authentication authentication, Model model) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute(
                "promotions",
                promotionService.getByShop(shopId)
        );

        return "promotions/promotion-list";
    }

    @ModelAttribute("discountTypes")
    public DiscountType[] discountTypes() {
        return DiscountType.values();
    }

    @GetMapping("/new")
    public String showCreateForm(Authentication authentication, Model model) {
        shopService.getManagedShopId(authentication);

        model.addAttribute("promotionForm", new PromotionForm());
        return "promotions/promotion-form";
    }

    @PostMapping
    public String create(
            Authentication authentication,
            @Valid @ModelAttribute("promotionForm") PromotionForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        if (bindingResult.hasErrors()) {
            return "promotions/promotion-form";
        }

        promotionService.create(shopId, form);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Tạo chương trình khuyến mãi thành công."
        );

        return "redirect:/seller/promotions";
    }
    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable("id") Long promotionId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        PromotionResponse promotion =
                promotionService.getById(promotionId, shopId);

        if (promotion.status() != PromotionStatus.INACTIVE) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chỉ được sửa chương trình đang tắt."
            );
            return "redirect:/seller/promotions";
        }

        PromotionForm form = new PromotionForm();
        form.setName(promotion.name());
        form.setDescription(promotion.description());
        form.setDiscountType(promotion.discountType());
        form.setDiscountValue(promotion.discountValue());
        form.setMaxDiscount(promotion.maxDiscount());
        form.setMinimumOrderValue(promotion.minimumOrderValue());
        form.setStartAt(promotion.startAt());
        form.setEndAt(promotion.endAt());

        model.addAttribute("promotionForm", form);
        model.addAttribute("promotionId", promotionId);

        return "promotions/promotion-form";
    }
    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") Long promotionId,
            Authentication authentication,
            @Valid @ModelAttribute("promotionForm") PromotionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        PromotionResponse promotion =
                promotionService.getById(promotionId, shopId);

        if (promotion.status() != PromotionStatus.INACTIVE) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chỉ được sửa chương trình đang tắt."
            );
            return "redirect:/seller/promotions";
        }

        model.addAttribute("promotionId", promotionId);

        if (bindingResult.hasErrors()) {
            return "promotions/promotion-form";
        }

        try {
            promotionService.update(promotionId, shopId, form);
        } catch (IllegalStateException exception) {
            bindingResult.reject(
                    "promotion.update.invalid",
                    "Không thể sửa chương trình ở trạng thái hiện tại. "
                            + "Vui lòng quay lại danh sách để kiểm tra."
            );
            return "promotions/promotion-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Cập nhật chương trình khuyến mãi thành công."
        );

        return "redirect:/seller/promotions";
    }
    @PostMapping("/{id}/activate")
    public String activate(
            @PathVariable("id") Long promotionId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        try {
            promotionService.activate(promotionId, shopId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã bật chương trình khuyến mãi."
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/seller/promotions";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(
            @PathVariable("id") Long promotionId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        try {
            promotionService.deactivate(promotionId, shopId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã tắt chương trình khuyến mãi."
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/seller/promotions";
    }
}