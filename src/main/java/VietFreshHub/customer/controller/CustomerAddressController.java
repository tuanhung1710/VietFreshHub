package VietFreshHub.customer.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.customer.dto.CustomerAddressRequest;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import VietFreshHub.vietmap.service.VietMapService;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/customer/addresses")
public class CustomerAddressController {

    private final CustomerAddressService customerAddressService;
    private final CurrentCustomerService currentCustomer;
    private final VietMapService vietMapService;
    private final CartService carts;

    public CustomerAddressController(CustomerAddressService customerAddressService,
                                     CurrentCustomerService currentCustomer, VietMapService vietMapService,
                                     CartService carts) {
        this.customerAddressService = customerAddressService;
        this.currentCustomer = currentCustomer;
        this.vietMapService = vietMapService;
        this.carts = carts;
    }

    @InitBinder("addressForm")
    public void bindAddressForm(WebDataBinder binder) {
        binder.setAllowedFields("addressId", "recipientName", "phone", "default", "refId", "searchText");
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) Long edit,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (edit != null) {
            try {
                customerAddressService.getAddress(authentication.getName(), edit);
                return "redirect:/customer/addresses/" + edit + "/edit";
            } catch (IllegalArgumentException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
                return "redirect:/customer/addresses";
            }
        }
        model.addAttribute("addresses", customerAddressService.getAddresses(authentication.getName()));
        return "customer/addresses";
    }

    @GetMapping("/new")
    public String newAddress(Authentication authentication, Model model) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CustomerAddressRequest addressForm = new CustomerAddressRequest();
        model.addAttribute("addressForm", addressForm);
        prepareAddressForm(addressForm, customerId, model);
        return "customer/address-form";
    }

    @GetMapping("/{addressId}/edit")
    public String edit(
            @PathVariable Long addressId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        try {
            CustomerAddressRequest addressForm = customerAddressService.getAddress(authentication.getName(), addressId);
            model.addAttribute("addressForm", addressForm);
            model.addAttribute("currentAddress", addressForm);
            prepareAddressForm(addressForm, customerId, model);
            return "customer/address-form";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/customer/addresses";
        }
    }

    @PostMapping
    public String save(
            @Valid @ModelAttribute("addressForm") CustomerAddressRequest addressForm,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        if (addressForm.getAddressId() != null) {
            try {
                model.addAttribute("currentAddress", customerAddressService.getAddress(authentication.getName(), addressForm.getAddressId()));
            } catch (IllegalArgumentException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
                return "redirect:/customer/addresses";
            }
        }
        if (!bindingResult.hasErrors()) {
            try {
                VietMapPlaceResponse place = null;
                if (customerAddressService.requiresLocationVerification(authentication.getName(), addressForm)) {
                    if (addressForm.getRefId() == null || addressForm.getRefId().isBlank()) {
                        throw new IllegalArgumentException("Vui lòng chọn địa chỉ từ danh sách gợi ý.");
                    }
                    place = vietMapService.place(addressForm.getRefId());
                    customerAddressService.validateDeliveryLocation(place);
                }
                customerAddressService.saveAddress(authentication.getName(), addressForm, place);
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu địa chỉ.");
                return "redirect:/customer/addresses";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("address.notFound", exception.getMessage());
            } catch (ResponseStatusException exception) {
                bindingResult.rejectValue("refId", "address.provider", "Không thể xác minh địa chỉ. Vui lòng thử lại.");
            } catch (DataAccessException | TransactionException exception) {
                bindingResult.reject("address.saveFailed", "Không thể lưu địa chỉ. Vui lòng thử lại sau.");
            }
        }

        prepareAddressForm(addressForm, customerId, model);
        return "customer/address-form";
    }

    @PostMapping("/{addressId}/default")
    public String setDefault(
            @PathVariable Long addressId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            customerAddressService.setDefaultAddress(authentication.getName(), addressId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thay đổi địa chỉ mặc định giao hàng");
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

    private void prepareAddressForm(CustomerAddressRequest addressForm, Long customerId, Model model) {
        boolean editing = addressForm.getAddressId() != null;
        model.addAttribute("checkoutAddressForm", false);
        model.addAttribute("addressFormTitle", editing ? "Sửa địa chỉ giao hàng" : "Thêm địa chỉ giao hàng");
        model.addAttribute("addressSubmitLabel", editing ? "Lưu địa chỉ" : "Thêm địa chỉ");
        model.addAttribute("addressFormAction", "/customer/addresses");
        model.addAttribute("cartBadgeTotal", carts.getActiveCart(customerId).getTotalItems());
    }
}
