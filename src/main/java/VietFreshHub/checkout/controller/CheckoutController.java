package VietFreshHub.checkout.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.checkout.dto.CheckoutAddressRequest;
import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.dto.CheckoutResult;
import VietFreshHub.checkout.service.CheckoutAddressService;
import VietFreshHub.coupon.services.CouponApplicationService;
import VietFreshHub.loyalty.services.LoyaltyService;
import VietFreshHub.checkout.service.CheckoutDraftStore;
import VietFreshHub.checkout.service.CheckoutService;
import VietFreshHub.checkout.service.CheckoutShippingQuoteService;
import VietFreshHub.customer.dto.CustomerAddressRequest;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import VietFreshHub.vietmap.service.VietMapService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import VietFreshHub.payment.vnpay.VnpayPaymentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/customer/checkout")
public class CheckoutController {

    private final CurrentCustomerService currentCustomer;
    private final CheckoutService checkout;
    private final CheckoutShippingQuoteService shippingQuotes;
    private final CheckoutDraftStore drafts;
    private final CheckoutAddressService checkoutAddresses;
    private final CustomerAddressService customerAddresses;
    private final VietMapService vietMapService;
    private final CartService carts;
    private final CouponApplicationService couponApplicationService;
    private final LoyaltyService loyaltyService;
    private final VnpayPaymentService vnpayPayments;
    @InitBinder("addressForm")
    public void bindAddressForm(WebDataBinder binder) {
        binder.setAllowedFields("recipientName", "phone", "refId", "searchText", "checkoutToken");
    }

    @GetMapping
    public String show(Authentication authentication,
                       HttpSession session,
                       Model model,
                       @RequestParam(required = false) List<Long> itemIds,
                       @RequestParam(defaultValue = "false") boolean selectionSubmitted,
                       @RequestParam(required = false) String checkoutToken,
                       @RequestParam(required = false) Long addressId,
                       @RequestParam(required = false) Long buyNowVariantId,
                       @RequestParam(required = false) Integer buyNowQuantity,
                       @RequestParam(required = false) List<String> couponCodes,
                       @RequestParam(defaultValue = "0") BigDecimal rewardAmountToUse) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CheckoutRequest form = new CheckoutRequest();
        form.setCouponCodes(couponCodes == null ? new ArrayList<>() : couponCodes);
        form.setRewardAmountToUse(rewardAmountToUse);
        CheckoutDraft draft = null;
        if (checkoutToken != null) {
            draft = requireCheckoutDraft(session, checkoutToken, customerId);
        }
        CheckoutPreview preview;
        if (draft != null) {
            preview = previewDraft(customerId, draft);
            form.setCheckoutToken(draft.token());
        } else if (buyNowVariantId != null) {
            preview = checkout.previewBuyNow(customerId, buyNowVariantId, buyNowQuantity);
        } else if (itemIds == null && !selectionSubmitted) {
            preview = checkout.preview(customerId);
        } else {
            List<Long> selectedItemIds = itemIds;
            if (selectedItemIds == null) {
                selectedItemIds = List.of();
            }
            preview = checkout.preview(customerId, selectedItemIds);
        }
        Long selectedAddressId = addressId;
        if (selectedAddressId == null && draft != null) {
            selectedAddressId = draft.addressId();
        }
        preview = shippingQuotes.quote(customerId, preview, selectedAddressId);
        populate(customerId, form, preview, session, model, draft == null && buyNowVariantId != null);
        return "customer/checkout";
    }

    @GetMapping("/address/new")
    public String newAddress(@RequestParam(required = false) String checkoutToken,
                       Authentication authentication,
                       HttpSession session,
                       Model model) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CheckoutDraft draft = requireCheckoutDraft(session, checkoutToken, customerId);
        previewDraft(customerId, draft);
        CheckoutAddressRequest addressForm = new CheckoutAddressRequest();
        addressForm.setCheckoutToken(draft.token());
        model.addAttribute("addressForm", addressForm);
        populateAddressPage(customerId, draft.token(), model);
        return "customer/address-form";
    }

    @GetMapping("/address/{addressId}/edit")
    public String editAddress(@PathVariable Long addressId,
                       @RequestParam(required = false) String checkoutToken,
                       Authentication authentication,
                       HttpSession session,
                       Model model) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CheckoutDraft draft = requireCheckoutDraft(session, checkoutToken, customerId);
        previewDraft(customerId, draft);
        CustomerAddressRequest addressForm = ownedAddress(authentication, addressId);
        model.addAttribute("addressForm", addressForm);
        populateEditAddressPage(customerId, draft.token(), addressForm, model);
        return "customer/address-form";
    }

    @PostMapping
    public String confirm(@Valid @ModelAttribute("form") CheckoutRequest form,
                       BindingResult errors,
                       Authentication authentication,
                       HttpSession session,
                       Model model,
                       HttpServletResponse response, HttpServletRequest request, RedirectAttributes flash) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CheckoutDraft draft = drafts.find(session, form.getCheckoutToken(), customerId);
        if (draft == null) {
            throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng chọn lại sản phẩm trước khi xác nhận.");
        }
        if (!errors.hasErrors()) {
            try {
                CheckoutResult order = checkout.confirm(customerId, draft, form);
                if ("BANK_TRANSFER".equals(order.paymentMethodCode()) || "VNPAY".equals(order.paymentMethodCode())) {
                    var start = vnpayPayments.startInitialPayment(order.paymentId(), customerId, request.getRemoteAddr());
                    if (start.started()) return "redirect:" + start.paymentUrl();
                    flash.addFlashAttribute("errorMessage", start.message());
                    return "redirect:/customer/orders";
                }
                flash.addFlashAttribute("successMessage", "Đặt hàng thành công.");
                return "redirect:/customer/orders";
            } catch (CheckoutException ex) {
                errors.reject("checkout", ex.getMessage());
            } catch (TransientDataAccessException | TransactionException ex) {
                log.warn("Checkout outcome needs verification for customerId={}, errorType={}", customerId, ex.getClass().getSimpleName());
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                // A connection/commit failure may leave the outcome unknown. Keep the original
                // idempotency token and avoid another database/provider call on the error path.
                model.addAttribute("checkoutToken", draft.token());
                model.addAttribute("orderCode", "VF-" + draft.token());
                return "customer/checkout-unavailable";
            }
        }
        CheckoutPreview preview = draft.buyNow() ? checkout.previewBuyNow(customerId, draft.buyNowVariantId(), draft.buyNowQuantity()) : checkout.preview(customerId, draft.selectedItemIds());
        Long selectedAddressId = form.getAddressId();
        if (selectedAddressId == null) {
            selectedAddressId = draft.addressId();
        }
        preview = shippingQuotes.quote(customerId, preview, selectedAddressId);
        populate(customerId, form, preview, session, model);
        return "customer/checkout";
    }

    @PostMapping("/address")
    public String addAddress(@Valid @ModelAttribute("addressForm") CheckoutAddressRequest addressForm,
                       BindingResult errors,
                       Authentication authentication,
                       HttpSession session,
                       Model model,
                       RedirectAttributes flash) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CheckoutDraft draft = requireCheckoutDraft(session, addressForm.getCheckoutToken(), customerId);
        previewDraft(customerId, draft);
        if (!errors.hasErrors()) {
            VietMapPlaceResponse place = null;
            try {
                place = vietMapService.place(addressForm.getRefId());
            } catch (ResponseStatusException ex) {
                errors.rejectValue("refId", "address.provider", "Không thể xác minh địa chỉ. Vui lòng thử lại.");
            }
            if (!errors.hasErrors()) {
                draft = requireCheckoutDraft(session, addressForm.getCheckoutToken(), customerId);
                previewDraft(customerId, draft);
                try {
                    Long addressId = checkoutAddresses.create(customerId, addressForm, place);
                    flash.addFlashAttribute("successMessage", "Đã lưu địa chỉ nhận hàng.");
                    return "redirect:/customer/checkout?checkoutToken=" + draft.token() + "&selectionSubmitted=true&addressId=" + addressId;
                } catch (CheckoutException ex) {
                    errors.reject("address.invalid", ex.getMessage());
                } catch (DataAccessException | TransactionException ex) {
                    errors.reject("address.saveFailed", "Không thể lưu địa chỉ. Vui lòng thử lại sau.");
                }
            }
        }
        populateAddressPage(customerId, draft.token(), model);
        return "customer/address-form";
    }

    @PostMapping("/address/{addressId}/edit")
    public String saveAddress(@PathVariable Long addressId,
                       @Valid @ModelAttribute("addressForm") CustomerAddressRequest addressForm,
                       BindingResult errors,
                       @RequestParam(required = false) String checkoutToken,
                       Authentication authentication,
                       HttpSession session,
                       Model model,
                       RedirectAttributes flash) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        CheckoutDraft draft = requireCheckoutDraft(session, checkoutToken, customerId);
        previewDraft(customerId, draft);
        CustomerAddressRequest currentAddress = ownedAddress(authentication, addressId);
        addressForm.setAddressId(currentAddress.getAddressId());
        addressForm.setDefault(currentAddress.isDefault());
        if (!errors.hasErrors()) {
            VietMapPlaceResponse place = null;
            try {
                if (customerAddresses.requiresLocationVerification(authentication.getName(), addressForm)) {
                    if (addressForm.getRefId() == null || addressForm.getRefId().isBlank()) {
                        throw new IllegalArgumentException("Vui lòng chọn địa chỉ từ danh sách gợi ý.");
                    }
                    place = vietMapService.place(addressForm.getRefId());
                    customerAddresses.validateDeliveryLocation(place);
                }
            } catch (ResponseStatusException ex) {
                errors.rejectValue("refId", "address.provider", "Không thể xác minh địa chỉ. Vui lòng thử lại.");
            } catch (IllegalArgumentException ex) {
                errors.rejectValue("refId", "address.invalid", ex.getMessage());
            }
            if (!errors.hasErrors()) {
                draft = requireCheckoutDraft(session, checkoutToken, customerId);
                previewDraft(customerId, draft);
                try {
                    customerAddresses.saveAddress(authentication.getName(), addressForm, place);
                    flash.addFlashAttribute("successMessage", "Đã lưu địa chỉ.");
                    return "redirect:/customer/checkout?checkoutToken=" + draft.token() + "&selectionSubmitted=true&addressId=" + addressId;
                } catch (IllegalArgumentException ex) {
                    errors.reject("address.invalid", ex.getMessage());
                } catch (DataAccessException | TransactionException ex) {
                    errors.reject("address.saveFailed", "Không thể lưu địa chỉ. Vui lòng thử lại sau.");
                }
            }
        }
        populateEditAddressPage(customerId, draft.token(), currentAddress, model);
        return "customer/address-form";
    }

    @ExceptionHandler(CheckoutException.class)
    public String invalidSelection(CheckoutException ex, RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/cart";
    }

    private CheckoutDraft requireCheckoutDraft(HttpSession session, String token, Long customerId) {
        CheckoutDraft draft = drafts.find(session, token, customerId);
        if (draft == null) {
            throw new CheckoutException("Phiên đặt hàng không hợp lệ. Vui lòng chọn lại sản phẩm.");
        }
        return draft;
    }

    private CheckoutPreview previewDraft(Long customerId, CheckoutDraft draft) {
        CheckoutPreview preview = draft.buyNow() ? checkout.previewBuyNow(customerId, draft.buyNowVariantId(), draft.buyNowQuantity()) : checkout.preview(customerId, draft.selectedItemIds());
        if (!Objects.equals(preview.cart().getCartId(), draft.cartId()) || !Objects.equals(preview.fingerprint(), draft.fingerprint())) {
            throw new CheckoutException("Giỏ hàng đã thay đổi. Vui lòng chọn lại sản phẩm.");
        }
        return preview;
    }

    private CustomerAddressRequest ownedAddress(Authentication authentication, Long addressId) {
        try {
            return customerAddresses.getAddress(authentication.getName(), addressId);
        } catch (IllegalArgumentException ex) {
            throw new CheckoutException(ex.getMessage());
        }
    }

    private void populateAddressPage(Long customerId, String checkoutToken, Model model) {
        model.addAttribute("checkoutAddressForm", true);
        model.addAttribute("checkoutAddressEditing", false);
        model.addAttribute("addressCheckoutToken", checkoutToken);
        model.addAttribute("addressFormTitle", "Thêm địa chỉ nhận hàng");
        model.addAttribute("addressSubmitLabel", "Lưu và dùng địa chỉ này");
        model.addAttribute("addressFormAction", "/customer/checkout/address");
        model.addAttribute("cartBadgeTotal", carts.countActiveUnits(customerId));
    }

    private void populateEditAddressPage(Long customerId, String checkoutToken, CustomerAddressRequest address, Model model) {
        populateAddressPage(customerId, checkoutToken, model);
        model.addAttribute("checkoutAddressEditing", true);
        model.addAttribute("addressFormTitle", "Sửa địa chỉ nhận hàng");
        model.addAttribute("addressSubmitLabel", "Lưu và quay lại đặt hàng");
        model.addAttribute("addressFormAction", "/customer/checkout/address/" + address.getAddressId() + "/edit");
        model.addAttribute("currentAddress", address);
    }

    private void populate(Long customerId, CheckoutRequest form, CheckoutPreview preview, HttpSession session, Model model) {
        populate(customerId,form,preview,session,model,false);
    }

    private void populate(Long customerId, CheckoutRequest form, CheckoutPreview preview, HttpSession session, Model model, boolean fresh) {
        CheckoutDraft draft = "BUY_NOW".equals(preview.cart().getStatus())
                ? drafts.issueBuyNow(session, customerId, preview, fresh ? null : form.getCheckoutToken())
                : drafts.issue(session, customerId, preview);
        form.setCheckoutToken(draft.token());
        if (!preview.codAvailable() && preview.vnpayAvailable()) form.setPaymentMethodCode("BANK_TRANSFER");
        form.setAddressId(preview.addressId());
        model.addAttribute("form", form);
        model.addAttribute("checkout", preview);
        model.addAttribute("cart", preview.cart());
        model.addAttribute("cartBadgeTotal", carts.countActiveUnits(customerId));
        Map<Long, List<CartItemDto>> shopGroups = new LinkedHashMap<>();
        for (CartItemDto item : preview.cart().getItems()) {
            shopGroups.computeIfAbsent(item.getShopId(), ignored -> new ArrayList<>()).add(item);
        }
        model.addAttribute("shopGroups", shopGroups);

        BigDecimal couponDiscount = BigDecimal.ZERO;
        BigDecimal loyaltyDiscount = BigDecimal.ZERO;

        model.addAttribute("couponApplications", List.of());
        Map<String, String> couponErrors = new LinkedHashMap<>();
        model.addAttribute("couponErrors", couponErrors);
        model.addAttribute("redemption", null);
        model.addAttribute("loyaltyError", null);
        model.addAttribute("loyaltyBalance", loyaltyService.getBalance(customerId));
        model.addAttribute("availableCoupons", draft.buyNow()
                ? couponApplicationService.getAvailableBuyNowCoupons(customerId, draft.buyNowVariantId(), draft.buyNowQuantity())
                : couponApplicationService.getAvailableCoupons(customerId, draft.selectedItemIds()));

        boolean couponValid = true;
        List<VietFreshHub.coupon.dto.CouponApplicationResponse> couponApplications = new ArrayList<>();
        Set<Long> selectedCouponShops = new java.util.HashSet<>();
        for (String code : Objects.requireNonNullElse(form.getCouponCodes(), List.<String>of())) {
            if (code == null || code.isBlank()) continue;
            try {
                var application = draft.buyNow()
                        ? couponApplicationService.previewBuyNow(customerId, code, draft.buyNowVariantId(), draft.buyNowQuantity())
                        : couponApplicationService.preview(customerId, code, draft.selectedItemIds());
                if (!selectedCouponShops.add(application.shopId())) {
                    couponErrors.put(code, "Mỗi shop chỉ được áp dụng một coupon.");
                    couponValid = false;
                    continue;
                }
                couponApplications.add(application);
                couponDiscount = couponDiscount.add(application.discountAmount());
            } catch (IllegalArgumentException ex) {
                couponErrors.put(code, ex.getMessage());
                couponValid = false;
            }
        }
        model.addAttribute("couponApplications", couponApplications);

        if (couponValid) {
            BigDecimal goodsAmountAfterCoupon = preview.cart()
                    .getTotalAmount()
                    .subtract(couponDiscount);

            try {
                var redemption = loyaltyService.previewRedemption(
                        customerId,
                        form.getRewardAmountToUse(),
                        goodsAmountAfterCoupon
                );
                loyaltyDiscount = redemption.discountAmount();
                model.addAttribute("redemption", redemption);
            } catch (IllegalArgumentException ex) {
                model.addAttribute("loyaltyError", ex.getMessage());
            }
        }

        model.addAttribute("couponDiscount", couponDiscount);
        model.addAttribute("loyaltyDiscount", loyaltyDiscount);
        model.addAttribute(
                "checkoutTotal",
                preview.grandTotal() == null
                        ? null
                        : preview.grandTotal().subtract(couponDiscount).subtract(loyaltyDiscount)
        );
    }

    @PostMapping("/reward/preview")
    public String previewReward(
            @ModelAttribute("form") CheckoutRequest form,
            @RequestParam(defaultValue = "0") BigDecimal rewardAmountInput,
            Authentication authentication,
            HttpSession session,
            Model model
    ) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        var draft = drafts.find(session, form.getCheckoutToken(), customerId);
        if (draft == null) {
            throw new CheckoutException(
                    "Phiên đặt hàng đã hết hạn. Vui lòng chọn lại sản phẩm."
            );
        }

        form.setRewardAmountToUse(rewardAmountInput);
        var preview = previewDraft(customerId, draft);
        preview = shippingQuotes.quote(customerId, preview, form.getAddressId() == null ? draft.addressId() : form.getAddressId());
        populate(customerId, form, preview, session, model);
        return "customer/checkout";
    }

    @PostMapping("/coupon/preview")
    public String previewCoupon(
            @ModelAttribute("form") CheckoutRequest form,
            Authentication authentication,
            HttpSession session,
            Model model
    ) {
        Long customerId =
                currentCustomer.requireCustomerId(authentication);

        var draft = drafts.find(
                session,
                form.getCheckoutToken(),
                customerId
        );

        if (draft == null) {
            throw new CheckoutException(
                    "Phiên đặt hàng đã hết hạn. Vui lòng chọn lại sản phẩm."
            );
        }

        form.setCouponCodes(Objects.requireNonNullElse(form.getCouponCodes(), List.<String>of()).stream().filter(code -> code != null && !code.isBlank()).map(String::strip).toList());

        var preview = previewDraft(customerId, draft);

        preview = shippingQuotes.quote(customerId, preview, form.getAddressId() == null ? draft.addressId() : form.getAddressId());
        populate(customerId, form, preview, session, model);

        return "customer/checkout";
    }
}
