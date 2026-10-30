package VietFreshHub.refund.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.refund.dto.CustomerRefundState;
import VietFreshHub.refund.dto.RefundInformationForm;
import VietFreshHub.refund.service.RefundRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRefundController {

    private final RefundRequestService refunds;
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderQueryService orders;

    @InitBinder("form")
    public void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("bankName", "bankAccountNumber", "accountHolderName");
    }

    @RequestMapping(value = "/{orderId}/refund-information", method = {RequestMethod.GET, RequestMethod.POST})
    public String legacyRefund(@PathVariable Long orderId, Authentication authentication, RedirectAttributes flash) {
        refunds.getCustomerState(orderId, authentication);
        flash.addFlashAttribute("errorMessage", "Vui lòng chọn đơn cửa hàng để xem hoặc gửi thông tin hoàn tiền.");
        return "redirect:/customer/orders/" + orderId;
    }

    @GetMapping("/shops/{shopOrderId}/refund-information")
    public String form(@PathVariable Long shopOrderId,
                       Authentication authentication, Model model) {
        CustomerRefundState state = refunds.getCustomerShopState(shopOrderId, authentication);
        if (state.submittedAt() != null) {
            return "redirect:" + detailPath(shopOrderId);
        }
        requireEligible(state);
        model.addAttribute("form", new RefundInformationForm());
        return render(state, shopOrderId, authentication, model);
    }

    @PostMapping("/shops/{shopOrderId}/refund-information")
    public String submit(@PathVariable Long shopOrderId, Authentication authentication,
                         @Valid @ModelAttribute("form") RefundInformationForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        CustomerRefundState state = refunds.getCustomerShopState(shopOrderId, authentication);
        if (state.submittedAt() != null) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi thông tin hoàn tiền.");
            return "redirect:" + detailPath(shopOrderId);
        }
        requireEligible(state);
        if (bindingResult.hasErrors()) {
            return render(state, shopOrderId, authentication, model);
        }
        try {
            refunds.submitShop(shopOrderId, authentication, form);
        } catch (DataIntegrityViolationException exception) {
            if (refunds.getCustomerShopState(shopOrderId, authentication).submittedAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chưa lưu được thông tin hoàn tiền. Vui lòng tải lại đơn hàng.");
            }
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi thông tin hoàn tiền.");
        return "redirect:" + detailPath(shopOrderId);
    }

    private String render(CustomerRefundState state, Long shopOrderId, Authentication authentication, Model model) {
        model.addAttribute("refund", state);
        String detailPath = detailPath(shopOrderId);
        model.addAttribute("orderDetailPath", detailPath);
        model.addAttribute("refundAction", detailPath + "/refund-information");
        model.addAttribute("cartBadgeTotal", orders.getCartBadgeTotal(currentCustomer.requireCustomerId(authentication)));
        return "customer/refund-information";
    }

    private void requireEligible(CustomerRefundState state) {
        if (!state.eligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, state.unavailableReason());
        }
    }

    private String detailPath(Long shopOrderId) {
        return "/customer/orders/shops/" + shopOrderId;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() {
        var view = new ModelAndView("customer/order-not-found");
        view.setStatus(HttpStatus.NOT_FOUND);
        return view;
    }
}
