package VietFreshHub.checkout.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.dto.CheckoutAddressRequest;
import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.service.CheckoutAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.checkout.service.CheckoutDraftStore;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.checkout.service.CheckoutShippingQuoteService;
import VietFreshHub.customer.dto.CustomerAddressRequest;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.vietmap.service.VietMapService;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j @Controller @RequiredArgsConstructor
@RequestMapping("/customer/checkout")
public class CheckoutController {
    private final CurrentCustomerService currentCustomer;
    private final CheckoutService checkout;
    private final CheckoutShippingQuoteService shippingQuotes;
    private final CheckoutDraftStore drafts;
    private final CheckoutAddressService checkoutAddresses;
    private final CustomerAddressService customerAddresses;
    private final VietMapService vietMapService;
    private final VietFreshHub.cart.service.CartService carts;
    @InitBinder("addressForm")
    public void bindAddressForm(WebDataBinder binder) {
        binder.setAllowedFields("recipientName","phone","refId","searchText","checkoutToken");
    }
    @GetMapping
    public String show(Authentication authentication,HttpSession session,Model model,
                       @RequestParam(required=false) List<Long> itemIds,
                       @RequestParam(defaultValue="false") boolean selectionSubmitted,
                       @RequestParam(required=false) String checkoutToken,
                       @RequestParam(required=false) Long addressId) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var form=new CheckoutRequest();
        var draft=checkoutToken==null ? null : requireCheckoutDraft(session,checkoutToken,customerId);
        CheckoutPreview preview;
        if(draft!=null) preview=previewDraft(customerId,draft);
        else preview=itemIds==null && !selectionSubmitted ? checkout.preview(customerId)
                : checkout.preview(customerId,itemIds==null ? List.of() : itemIds);
        preview=shippingQuotes.quote(customerId,preview,addressId!=null ? addressId : draft==null ? null : draft.addressId());
        populate(customerId,form,preview,session,model);
        return "customer/checkout";
    }
    @GetMapping("/address/new")
    public String newAddress(@RequestParam(required=false) String checkoutToken,Authentication authentication,
                             HttpSession session,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=requireCheckoutDraft(session,checkoutToken,customerId);
        previewDraft(customerId,draft);
        var addressForm=new CheckoutAddressRequest();
        addressForm.setCheckoutToken(draft.token());
        model.addAttribute("addressForm",addressForm);
        populateAddressPage(customerId,draft.token(),model);
        return "customer/address-form";
    }
    @GetMapping("/address/{addressId}/edit")
    public String editAddress(@PathVariable Long addressId,@RequestParam(required=false) String checkoutToken,
                              Authentication authentication,HttpSession session,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=requireCheckoutDraft(session,checkoutToken,customerId);
        previewDraft(customerId,draft);
        var addressForm=ownedAddress(authentication,addressId);
        model.addAttribute("addressForm",addressForm);
        populateEditAddressPage(customerId,draft.token(),addressForm,model);
        return "customer/address-form";
    }
    @PostMapping
    public String confirm(@Valid @ModelAttribute("form") CheckoutRequest form,BindingResult errors,
                          Authentication authentication,HttpSession session,Model model,HttpServletResponse response) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=drafts.find(session,form.getCheckoutToken(),customerId);
        if(draft==null) throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng chọn lại sản phẩm trước khi xác nhận.");
        if(!errors.hasErrors()) {
            try {
                var order=checkout.confirm(customerId,draft,form);
                return "redirect:/customer/orders/"+order.orderId()+"/confirmation";
            } catch(CheckoutException ex) {
                errors.reject("checkout",ex.getMessage());
            } catch(DataAccessException | TransactionException ex) {
                log.warn("Checkout outcome needs verification for customerId={}, errorType={}",customerId,ex.getClass().getSimpleName());
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                // A connection/commit failure may leave the outcome unknown. Keep the original
                // idempotency token and avoid another database/provider call on the error path.
                model.addAttribute("checkoutToken",draft.token());
                model.addAttribute("orderCode","VF-"+draft.token());
                return "customer/checkout-unavailable";
            }
        }
        CheckoutPreview preview=checkout.preview(customerId,draft.selectedItemIds());
        preview=shippingQuotes.quote(customerId,preview,form.getAddressId()==null ? draft.addressId() : form.getAddressId());
        populate(customerId,form,preview,session,model);
        return "customer/checkout";
    }
    @PostMapping("/address")
    public String addAddress(@Valid @ModelAttribute("addressForm") CheckoutAddressRequest addressForm,BindingResult errors,
                             Authentication authentication,HttpSession session,Model model,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=requireCheckoutDraft(session,addressForm.getCheckoutToken(),customerId);
        previewDraft(customerId,draft);
        if(!errors.hasErrors()) {
            VietMapPlaceResponse place=null;
            try {
                place=vietMapService.place(addressForm.getRefId());
            } catch(ResponseStatusException ex) {
                errors.rejectValue("refId","address.provider","Không thể xác minh địa chỉ. Vui lòng thử lại.");
            }
            if(!errors.hasErrors()) {
                draft=requireCheckoutDraft(session,addressForm.getCheckoutToken(),customerId);
                previewDraft(customerId,draft);
                try {
                    Long addressId=checkoutAddresses.create(customerId,addressForm,place);
                    flash.addFlashAttribute("successMessage","Đã lưu địa chỉ nhận hàng.");
                    return "redirect:/customer/checkout?checkoutToken="+draft.token()+"&selectionSubmitted=true&addressId="+addressId;
                } catch(CheckoutException ex) {
                    errors.reject("address.invalid",ex.getMessage());
                } catch(DataAccessException | TransactionException ex) {
                    errors.reject("address.saveFailed","Không thể lưu địa chỉ. Vui lòng thử lại sau.");
                }
            }
        }
        populateAddressPage(customerId,draft.token(),model);
        return "customer/address-form";
    }
    @PostMapping("/address/{addressId}/edit")
    public String saveAddress(@PathVariable Long addressId,
                              @Valid @ModelAttribute("addressForm") CustomerAddressRequest addressForm,BindingResult errors,
                              @RequestParam(required=false) String checkoutToken,Authentication authentication,
                              HttpSession session,Model model,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        var draft=requireCheckoutDraft(session,checkoutToken,customerId);
        previewDraft(customerId,draft);
        var currentAddress=ownedAddress(authentication,addressId);
        addressForm.setAddressId(currentAddress.getAddressId());
        addressForm.setDefault(currentAddress.isDefault());
        if(!errors.hasErrors()) {
            VietMapPlaceResponse place=null;
            try {
                if(customerAddresses.requiresLocationVerification(authentication.getName(),addressForm)) {
                    if(addressForm.getRefId()==null || addressForm.getRefId().isBlank()) {
                        throw new IllegalArgumentException("Vui lòng chọn địa chỉ từ danh sách gợi ý.");
                    }
                    place=vietMapService.place(addressForm.getRefId());
                    customerAddresses.validateDeliveryLocation(place);
                }
            } catch(ResponseStatusException ex) {
                errors.rejectValue("refId","address.provider","Không thể xác minh địa chỉ. Vui lòng thử lại.");
            } catch(IllegalArgumentException ex) {
                errors.rejectValue("refId","address.invalid",ex.getMessage());
            }
            if(!errors.hasErrors()) {
                draft=requireCheckoutDraft(session,checkoutToken,customerId);
                previewDraft(customerId,draft);
                try {
                    customerAddresses.saveAddress(authentication.getName(),addressForm,place);
                    flash.addFlashAttribute("successMessage","Đã lưu địa chỉ.");
                    return "redirect:/customer/checkout?checkoutToken="+draft.token()+"&selectionSubmitted=true&addressId="+addressId;
                } catch(IllegalArgumentException ex) {
                    errors.reject("address.invalid",ex.getMessage());
                } catch(DataAccessException | TransactionException ex) {
                    errors.reject("address.saveFailed","Không thể lưu địa chỉ. Vui lòng thử lại sau.");
                }
            }
        }
        populateEditAddressPage(customerId,draft.token(),currentAddress,model);
        return "customer/address-form";
    }
    @ExceptionHandler(CheckoutException.class)
    public String invalidSelection(CheckoutException ex,RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage",ex.getMessage()); return "redirect:/cart";
    }
    private CheckoutDraft requireCheckoutDraft(HttpSession session,String token,Long customerId) {
        var draft=drafts.find(session,token,customerId);
        if(draft==null) throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng chọn lại sản phẩm.");
        return draft;
    }
    private CheckoutPreview previewDraft(Long customerId,CheckoutDraft draft) {
        var preview=checkout.preview(customerId,draft.selectedItemIds());
        if(!Objects.equals(preview.cart().getCartId(),draft.cartId()) || !Objects.equals(preview.fingerprint(),draft.fingerprint())) {
            throw new CheckoutException("Giỏ hàng đã thay đổi. Vui lòng chọn lại sản phẩm.");
        }
        return preview;
    }
    private CustomerAddressRequest ownedAddress(Authentication authentication,Long addressId) {
        try {
            return customerAddresses.getAddress(authentication.getName(),addressId);
        } catch(IllegalArgumentException ex) {
            throw new CheckoutException(ex.getMessage());
        }
    }
    private void populateAddressPage(Long customerId,String checkoutToken,Model model) {
        model.addAttribute("checkoutAddressForm",true);
        model.addAttribute("checkoutAddressEditing",false);
        model.addAttribute("addressCheckoutToken",checkoutToken);
        model.addAttribute("addressFormTitle","Thêm địa chỉ nhận hàng");
        model.addAttribute("addressSubmitLabel","Lưu và dùng địa chỉ này");
        model.addAttribute("addressFormAction","/customer/checkout/address");
        model.addAttribute("cartBadgeTotal",carts.getActiveCart(customerId).getTotalItems());
    }
    private void populateEditAddressPage(Long customerId,String checkoutToken,CustomerAddressRequest address,Model model) {
        populateAddressPage(customerId,checkoutToken,model);
        model.addAttribute("checkoutAddressEditing",true);
        model.addAttribute("addressFormTitle","Sửa địa chỉ nhận hàng");
        model.addAttribute("addressSubmitLabel","Lưu và quay lại đặt hàng");
        model.addAttribute("addressFormAction","/customer/checkout/address/"+address.getAddressId()+"/edit");
        model.addAttribute("currentAddress",address);
    }
    private void populate(Long customerId,CheckoutRequest form,VietFreshHub.checkout.dto.CheckoutPreview preview,
                          HttpSession session,Model model) {
        var draft=drafts.issue(session,customerId,preview); form.setCheckoutToken(draft.token());
        form.setAddressId(preview.addressId());
        model.addAttribute("form",form); model.addAttribute("checkout",preview); model.addAttribute("cart",preview.cart());
        model.addAttribute("cartBadgeTotal",carts.getActiveCart(customerId).getTotalItems());
        model.addAttribute("shopGroups",preview.cart().getItems().stream().collect(Collectors.groupingBy(
                CartItemDto::getShopId,LinkedHashMap::new,Collectors.toList())));
    }
}
