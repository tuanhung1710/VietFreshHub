package VietFreshHub.admin.controller;

import VietFreshHub.admin.dto.AdminShippingFeeRequest;
import VietFreshHub.admin.service.AdminShippingFeeService;
import VietFreshHub.exception.CheckoutException;
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
@RequestMapping("/admin/shipping-fees")
@PreAuthorize("hasRole('ADMIN')")
public class AdminShippingFeeController {

    private final AdminShippingFeeService shippingFeeService;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("form", shippingFeeService.getForm());
        return "admin/shipping-fees";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("form") AdminShippingFeeRequest form,
                         BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "admin/shipping-fees";
        }
        try {
            shippingFeeService.update(form);
        } catch (IllegalArgumentException | IllegalStateException | CheckoutException exception) {
            bindingResult.reject("shippingFee", exception.getMessage());
            return "admin/shipping-fees";
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject("shippingFee", "Không thể lưu phí vận chuyển. Vui lòng kiểm tra lại thông số.");
            return "admin/shipping-fees";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật phí vận chuyển.");
        return "redirect:/admin/shipping-fees";
    }
}
