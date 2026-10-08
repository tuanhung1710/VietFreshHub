package VietFreshHub.Checkout.controller;

import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.dto.CartItemDto;
import VietFreshHub.Checkout.dto.CheckoutRequest;
import VietFreshHub.Checkout.dto.CheckoutAddressRequest;
import VietFreshHub.Checkout.service.CheckoutAddressService;
import VietFreshHub.Checkout.exception.CheckoutException;
import VietFreshHub.Checkout.service.CheckoutDraftStore;
import VietFreshHub.Checkout.service.CheckoutService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Slf4j @Controller @RequiredArgsConstructor
@RequestMapping("/customer/checkout")
public class CheckoutController {
    private final CurrentCustomerService currentCustomer;
    private final CheckoutService checkout;
    private final CheckoutDraftStore drafts;
    private final CheckoutAddressService checkoutAddresses;
    private final VietFreshHub.Cart.service.CartService carts;
    @GetMapping
    public String show(Authentication authentication,HttpSession session,Model model,
                       @RequestParam(required=false) List<Long> itemIds,
                       @RequestParam(defaultValue="false") boolean selectionSubmitted,
                       @RequestParam(required=false) Long addressId) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var form=new CheckoutRequest();
        var preview=itemIds==null && !selectionSubmitted ? checkout.preview(customerId)
                : checkout.preview(customerId,itemIds==null ? List.of() : itemIds);
        if(addressId!=null && preview.addresses().stream().anyMatch(a -> addressId.equals(a.addressId()))) form.setAddressId(addressId);
        else if(!preview.addresses().isEmpty()) form.setAddressId(preview.addresses().getFirst().addressId());
        populate(customerId,form,preview,session,model);
        return "customer/checkout";
    }
    @PostMapping
    public String confirm(@Valid @ModelAttribute("form") CheckoutRequest form,BindingResult errors,
                          Authentication authentication,HttpSession session,Model model,HttpServletResponse response) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=drafts.find(session,form.getCheckoutToken(),customerId);
        if(draft==null) errors.reject("session","Phiên đặt hàng không hợp lệ. Vui lòng xem lại đơn trước khi xác nhận.");
        if(!errors.hasErrors()) {
            try {
                var order=checkout.confirm(customerId,draft,form);
                return "redirect:/customer/orders/"+order.orderId()+"/confirmation";
            } catch(CheckoutException ex) {
                errors.reject("checkout",ex.getMessage());
            } catch(TransientDataAccessException ex) {
                log.warn("Checkout needs retry for customerId={}, errorType={}",customerId,ex.getClass().getSimpleName());
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                errors.reject("checkout","Đơn hàng chưa được tạo. Vui lòng kiểm tra lại và thử xác nhận lần nữa.");
            }
        }
        populate(customerId,form,draft==null ? checkout.preview(customerId) : checkout.preview(customerId,draft.selectedItemIds()),session,model);
        return "customer/checkout";
    }
    @PostMapping("/address")
    public String addAddress(@Valid @ModelAttribute("addressForm") CheckoutAddressRequest addressForm,BindingResult errors,
                             Authentication authentication,HttpSession session,Model model,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=drafts.find(session,addressForm.getCheckoutToken(),customerId);
        if(draft==null) throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng chọn lại sản phẩm.");
        if(!errors.hasErrors()) {
            Long addressId=checkoutAddresses.create(customerId,addressForm);
            flash.addFlashAttribute("successMessage","Đã lưu địa chỉ nhận hàng.");
            return "redirect:/customer/checkout?addressId="+addressId+draft.selectedItemIds().stream()
                    .map(id -> "&itemIds="+id).collect(Collectors.joining());
        }
        var form=new CheckoutRequest(); populate(customerId,form,checkout.preview(customerId,draft.selectedItemIds()),session,model);
        model.addAttribute("addressFormOpen",true); return "customer/checkout";
    }
    @ExceptionHandler(CheckoutException.class)
    public String invalidSelection(CheckoutException ex,RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage",ex.getMessage()); return "redirect:/cart";
    }
    private void populate(Long customerId,CheckoutRequest form,VietFreshHub.Checkout.dto.CheckoutPreview preview,
                          HttpSession session,Model model) {
        var draft=drafts.issue(session,customerId,preview); form.setCheckoutToken(draft.token());
        model.addAttribute("form",form); model.addAttribute("checkout",preview); model.addAttribute("cart",preview.cart());
        model.addAttribute("cartBadgeTotal",carts.getActiveCart(customerId).getTotalItems());
        if(!model.containsAttribute("addressForm")) model.addAttribute("addressForm",new CheckoutAddressRequest());
        ((CheckoutAddressRequest)model.getAttribute("addressForm")).setCheckoutToken(draft.token());
        model.addAttribute("shopGroups",preview.cart().getItems().stream().collect(Collectors.groupingBy(
                CartItemDto::getShopId,LinkedHashMap::new,Collectors.toList())));
    }
}
