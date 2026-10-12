package VietFreshHub.customer.controller;

import VietFreshHub.customer.dto.CustomerAddressRequest;
import VietFreshHub.customer.service.CustomerAddressService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/addresses")
public class CustomerAddressController {

    private final CustomerAddressService customerAddressService;

    public CustomerAddressController(CustomerAddressService customerAddressService) {
        this.customerAddressService = customerAddressService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) Long edit,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        model.addAttribute("addresses", customerAddressService.getAddresses(authentication.getName()));
        if (edit == null) {
            model.addAttribute("addressForm", new CustomerAddressRequest());
        } else {
            try {
                model.addAttribute("addressForm", customerAddressService.getAddress(authentication.getName(), edit));
            } catch (IllegalArgumentException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
                return "redirect:/customer/addresses";
            }
        }
        return "customer/addresses";
    }

    @PostMapping
    public String save(
            @Valid @ModelAttribute("addressForm") CustomerAddressRequest addressForm,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                customerAddressService.saveAddress(authentication.getName(), addressForm);
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu địa chỉ.");
                return "redirect:/customer/addresses";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("address.notFound", exception.getMessage());
            }
        }

        model.addAttribute("addresses", customerAddressService.getAddresses(authentication.getName()));
        model.addAttribute("addressForm", addressForm);
        return "customer/addresses";
    }

    @PostMapping("/{addressId}/default")
    public String setDefault(
            @PathVariable Long addressId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            customerAddressService.setDefaultAddress(authentication.getName(), addressId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đặt địa chỉ mặc định.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/customer/addresses";
    }

    @PostMapping("/{addressId}/delete")
    public String delete(
            @PathVariable Long addressId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            customerAddressService.deleteAddress(authentication.getName(), addressId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa địa chỉ.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/customer/addresses";
    }
}
