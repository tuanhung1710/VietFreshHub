package VietFreshHub.coupon.controller;

import VietFreshHub.shop.service.ShopService;
import VietFreshHub.coupon.dto.CouponForm;
import VietFreshHub.coupon.dto.CouponResponse;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.coupon.services.CouponService;
import VietFreshHub.promotion.entity.PromotionStatus;
import VietFreshHub.promotion.services.PromotionService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/coupons")
public class CouponController {

    private static final String FORM_VIEW =
            "coupons/coupon-form";

    private static final String LIST_VIEW =
            "coupons/coupon-list";

    private static final String REDIRECT_LIST =
            "redirect:/seller/coupons";

    private final CouponService couponService;
    private final PromotionService promotionService;
    private final ShopService shopService;

    @GetMapping
    public String list(Authentication authentication, Model model) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute(
                "coupons",
                couponService.getByShop(shopId)
        );

        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showCreateForm(
            Authentication authentication,
            Model model
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        model.addAttribute("couponForm", new CouponForm());
        prepareForm(model, shopId, null);

        return FORM_VIEW;
    }

    @PostMapping
    public String create(
            Authentication authentication,
            @Valid @ModelAttribute("couponForm") CouponForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        if (bindingResult.hasErrors()) {
            prepareForm(model, shopId, null);
            return FORM_VIEW;
        }

        try {
            couponService.create(shopId, form);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            bindingResult.reject("coupon.invalid", ex.getMessage());
        } catch (ConstraintViolationException ex) {
            addValidationErrors(bindingResult, ex);
        } catch (DataIntegrityViolationException ex) {
            bindingResult.reject(
                    "coupon.conflict",
                    "Không thể lưu coupon. Mã có thể đã tồn tại "
                            + "hoặc dữ liệu liên quan đã thay đổi."
            );
        }

        if (bindingResult.hasErrors()) {
            prepareForm(model, shopId, null);
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Tạo coupon thành công. Coupon hiện đang tắt."
        );

        return REDIRECT_LIST;
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable("id") Long couponId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        CouponResponse coupon = couponService.getById(
                couponId,
                shopId
        );

        if (coupon.status() != CouponStatus.INACTIVE) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chỉ được sửa coupon đang ở trạng thái tắt."
            );

            return REDIRECT_LIST;
        }

        CouponForm form = new CouponForm();
        form.setPromotionId(coupon.promotionId());
        form.setCode(coupon.code());
        form.setUsageLimit(coupon.usageLimit());
        form.setPerUserLimit(coupon.perUserLimit());
        form.setMinimumOrderValue(coupon.minimumOrderValue());
        form.setStartAt(coupon.startAt());
        form.setEndAt(coupon.endAt());

        model.addAttribute("couponForm", form);
        prepareForm(model, shopId, couponId);

        return FORM_VIEW;
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") Long couponId,
            Authentication authentication,
            @Valid @ModelAttribute("couponForm") CouponForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        CouponResponse coupon = couponService.getById(
                couponId,
                shopId
        );

        if (coupon.status() != CouponStatus.INACTIVE) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chỉ được sửa coupon đang ở trạng thái tắt."
            );

            return REDIRECT_LIST;
        }

        if (bindingResult.hasErrors()) {
            prepareForm(model, shopId, couponId);
            return FORM_VIEW;
        }

        try {
            couponService.update(couponId, shopId, form);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            bindingResult.reject("coupon.invalid", ex.getMessage());
        } catch (ConstraintViolationException ex) {
            addValidationErrors(bindingResult, ex);
        } catch (DataIntegrityViolationException ex) {
            bindingResult.reject(
                    "coupon.conflict",
                    "Không thể cập nhật coupon. Mã có thể đã tồn tại "
                            + "hoặc dữ liệu liên quan đã thay đổi."
            );
        }

        if (bindingResult.hasErrors()) {
            prepareForm(model, shopId, couponId);
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Cập nhật coupon thành công."
        );

        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/activate")
    public String activate(
            @PathVariable("id") Long couponId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        return changeStatus(
                () -> couponService.activate(couponId, shopId),
                "Đã bật coupon.",
                redirectAttributes
        );
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(
            @PathVariable("id") Long couponId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        return changeStatus(
                () -> couponService.deactivate(couponId, shopId),
                "Đã tắt coupon.",
                redirectAttributes
        );
    }

    @PostMapping("/{id}/disable")
    public String disable(
            @PathVariable("id") Long couponId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        Long shopId = shopService.getManagedShopId(authentication);

        return changeStatus(
                () -> couponService.disable(couponId, shopId),
                "Đã vô hiệu hóa coupon. Không thể bật lại mã này.",
                redirectAttributes
        );
    }

    private void prepareForm(
            Model model,
            Long shopId,
            Long couponId
    ) {
        LocalDateTime now = LocalDateTime.now(
                ZoneId.of("Asia/Ho_Chi_Minh")
        );

        var selectablePromotions = promotionService.getByShop(shopId)
                .stream()
                .filter(promotion ->
                        promotion.status() == PromotionStatus.ACTIVE
                                || promotion.status() == PromotionStatus.INACTIVE
                )
                .filter(promotion -> promotion.endAt().isAfter(now))
                .toList();

        model.addAttribute("promotions", selectablePromotions);
        model.addAttribute("couponId", couponId);
    }

    private void addValidationErrors(
            BindingResult bindingResult,
            ConstraintViolationException exception
    ) {
        exception.getConstraintViolations().forEach(violation ->
                bindingResult.reject(
                        "coupon.validation",
                        violation.getMessage()
                )
        );
    }

    private String changeStatus(
            Runnable action,
            String successMessage,
            RedirectAttributes redirectAttributes
    ) {
        try {
            action.run();

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    successMessage
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return REDIRECT_LIST;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleInvalidRequest(
            IllegalArgumentException exception,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                exception.getMessage()
        );

        return REDIRECT_LIST;
    }
}