package VietFreshHub.subscription.controller;

import VietFreshHub.subscription.dto.SubscriptionPlanForm;
import VietFreshHub.subscription.entity.BillingPeriod;
import VietFreshHub.subscription.entity.PlanStatus;
import VietFreshHub.subscription.services.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/subscription-plans")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionPlanController {

    private static final String LIST_VIEW =
            "subscription/subscription-plans";

    private static final String FORM_VIEW =
            "subscription/subscription-plan-form";

    private static final String REDIRECT_LIST =
            "redirect:/admin/subscription-plans";

    private final SubscriptionPlanService subscriptionPlanService;

    @ModelAttribute("billingPeriods")
    public BillingPeriod[] billingPeriods() {
        return BillingPeriod.values();
    }

    @ModelAttribute("planStatuses")
    public PlanStatus[] planStatuses() {
        return PlanStatus.values();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(
                "plans",
                subscriptionPlanService.getAll()
        );

        return LIST_VIEW;
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("form", new SubscriptionPlanForm());

        return prepareForm(model, null);
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") SubscriptionPlanForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return prepareForm(model, null);
        }

        try {
            subscriptionPlanService.create(form);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject(
                    "plan.invalid",
                    exception.getMessage()
            );

            return prepareForm(model, null);
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "plan.conflict",
                    "Không thể lưu gói. Kiểm tra tên trùng và dữ liệu nhập."
            );

            return prepareForm(model, null);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Tạo gói dịch vụ thành công"
        );

        return REDIRECT_LIST;
    }

    @GetMapping("/{planId}/edit")
    public String editForm(
            @PathVariable("planId") Long planId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            model.addAttribute(
                    "form",
                    subscriptionPlanService.getFormById(planId)
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return REDIRECT_LIST;
        }

        return prepareForm(model, planId);
    }

    @PostMapping("/{planId}/update")
    public String update(
            @PathVariable("planId") Long planId,
            @Valid @ModelAttribute("form") SubscriptionPlanForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return prepareForm(model, planId);
        }

        try {
            subscriptionPlanService.update(planId, form);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject(
                    "plan.invalid",
                    exception.getMessage()
            );

            return prepareForm(model, planId);
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "plan.conflict",
                    "Không thể lưu gói. Kiểm tra tên trùng và dữ liệu nhập."
            );

            return prepareForm(model, planId);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Cập nhật gói dịch vụ thành công"
        );

        return REDIRECT_LIST;
    }

    @PostMapping("/{planId}/activate")
    public String activate(
            @PathVariable("planId") Long planId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            subscriptionPlanService.activate(planId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã bật gói dịch vụ"
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return REDIRECT_LIST;
    }

    @PostMapping("/{planId}/deactivate")
    public String deactivate(
            @PathVariable("planId") Long planId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            subscriptionPlanService.deactivate(planId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã tắt gói dịch vụ"
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return REDIRECT_LIST;
    }

    private String prepareForm(Model model, Long planId) {
        model.addAttribute("planId", planId);
        model.addAttribute("editing", planId != null);
        model.addAttribute(
                "pageTitle",
                planId == null ? "Tạo gói dịch vụ" : "Sửa gói dịch vụ"
        );

        return FORM_VIEW;
    }
}