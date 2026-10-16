package VietFreshHub.loyalty.controller;

import VietFreshHub.loyalty.dto.LoyaltyEarningRuleForm;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionRuleForm;
import VietFreshHub.loyalty.services.LoyaltyRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/loyalty-rules")
@PreAuthorize("hasRole('ADMIN')")
public class LoyaltyRuleController {

    private static final String EARNING_VIEW =
            "loyalty/loyalty-earning-rule-form";

    private static final String REDEMPTION_VIEW =
            "loyalty/loyalty-redemption-rule-form";

    private static final String REDIRECT_EARNING =
            "redirect:/admin/loyalty-rules/earning";

    private static final String REDIRECT_REDEMPTION =
            "redirect:/admin/loyalty-rules/redemption";

    private final LoyaltyRuleService loyaltyRuleService;

    @GetMapping
    public String index() {
        return REDIRECT_EARNING;
    }

    @GetMapping("/earning")
    public String earningForm(Model model) {
        model.addAttribute(
                "form",
                loyaltyRuleService.getEarningForm()
        );

        return EARNING_VIEW;
    }

    @PostMapping("/earning")
    public String updateEarningRules(
            @Valid @ModelAttribute("form") LoyaltyEarningRuleForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return EARNING_VIEW;
        }

        try {
            loyaltyRuleService.updateEarningRules(form);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject(
                    "loyalty.earning.invalid",
                    exception.getMessage()
            );

            return EARNING_VIEW;
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "loyalty.earning.conflict",
                    "Không thể lưu cấu hình tích điểm. "
                            + "Vui lòng kiểm tra dữ liệu nhập."
            );

            return EARNING_VIEW;
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Cập nhật quy tắc tích điểm thành công"
        );

        return REDIRECT_EARNING;
    }

    @GetMapping("/redemption")
    public String redemptionForm(Model model) {
        model.addAttribute(
                "form",
                loyaltyRuleService.getRedemptionForm()
        );

        return REDEMPTION_VIEW;
    }

    @PostMapping("/redemption")
    public String updateRedemptionRules(
            @Valid @ModelAttribute("form")
            LoyaltyRedemptionRuleForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return REDEMPTION_VIEW;
        }

        try {
            loyaltyRuleService.updateRedemptionRules(form);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject(
                    "loyalty.redemption.invalid",
                    exception.getMessage()
            );

            return REDEMPTION_VIEW;
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "loyalty.redemption.conflict",
                    "Không thể lưu cấu hình đổi điểm. "
                            + "Vui lòng kiểm tra dữ liệu nhập."
            );

            return REDEMPTION_VIEW;
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Cập nhật quy tắc đổi điểm thành công"
        );

        return REDIRECT_REDEMPTION;
    }
}