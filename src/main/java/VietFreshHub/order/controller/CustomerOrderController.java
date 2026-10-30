package VietFreshHub.order.controller;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.order.dto.*;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;
import VietFreshHub.payment.vnpay.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import VietFreshHub.exception.CheckoutException;

@Controller @RequiredArgsConstructor @RequestMapping("/customer/orders")
public class CustomerOrderController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderService orders;
    private final VietFreshHub.order.service.CustomerOrderQueryService readOrders;
    private final VnpayPaymentService vnpayPayments;
    @GetMapping
    public String history(@Valid @ModelAttribute("filter") CustomerOrderFilterRequest filter,BindingResult errors,Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        model.addAttribute("invalidFilter",errors.hasErrors());
        if(errors.hasErrors()) {
            model.addAttribute("filterError","Bộ lọc không hợp lệ. Vui lòng sửa các thông tin bên dưới rồi lọc lại.");
            model.addAttribute("filterMessages",errors.getAllErrors().stream().map(error -> {
                if(error instanceof org.springframework.validation.FieldError field && field.isBindingFailure())
                    return "page".equals(field.getField()) ? "Trang phải là số nguyên hợp lệ." : "Ngày lọc phải có định dạng yyyy-MM-dd.";
                return error.getDefaultMessage();
            }).toList());
            model.addAttribute("orderPage",org.springframework.data.domain.Page.empty());
        } else {
            int requestedPage=filter.getPage();
            var page=orders.history(customerId,filter);
            model.addAttribute("orderPage",page);
            if(requestedPage!=page.getNumber()+1)
                model.addAttribute("pageNotice","Trang yêu cầu không còn tồn tại. Đang hiển thị trang " + (page.getNumber()+1) + ".");
        }
        model.addAttribute("cartBadgeTotal",readOrders.getCartBadgeTotal(customerId));
        model.addAttribute("statusOptions",OrderLabels.customerStatusOptions());
        return "customer/orders";
    }
    @GetMapping("/{orderId}/tracking")
    public String tracking(@PathVariable Long orderId,Authentication authentication,Model model) {
        readOrders.getDetails(currentCustomer.requireCustomerId(authentication),orderId);
        return "redirect:/customer/orders/"+orderId;
    }
    @GetMapping("/shops/{shopOrderId}/tracking")
    public String shopTracking(@PathVariable Long shopOrderId,Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        model.addAttribute("tracking",orders.shopTracking(customerId,shopOrderId));
        model.addAttribute("shopOrderId",shopOrderId);
        model.addAttribute("cartBadgeTotal",readOrders.getCartBadgeTotal(customerId));
        return "customer/order-tracking";
    }
    @PostMapping("/shops/{shopOrderId}/cancel")
    public String cancelShop(@PathVariable Long shopOrderId,@Valid @ModelAttribute("cancelForm") OrderCancelRequest form,BindingResult errors,
                             Authentication authentication,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        if(errors.hasErrors()) {
            flash.addFlashAttribute("errorMessage","Lý do hủy tối đa 500 ký tự.");
            flash.addFlashAttribute("cancelReason",form.getReason());
        } else try {
            orders.cancelShop(customerId,shopOrderId,form.getReason());
            flash.addFlashAttribute("successMessage","Đơn cửa hàng đã được hủy.");
        } catch(CustomerOrderException ex) {
            flash.addFlashAttribute("errorMessage",ex.getMessage());
        }
        return "redirect:/customer/orders/shops/"+shopOrderId;
    }
    @PostMapping({"/{orderId}/cancel","/{orderId}/reorder"})
    public String legacyAction(@PathVariable Long orderId,Authentication authentication,RedirectAttributes flash) {
        readOrders.getDetails(currentCustomer.requireCustomerId(authentication),orderId);
        flash.addFlashAttribute("errorMessage","Vui lòng chọn đơn cửa hàng để hủy hoặc mua lại.");
        return "redirect:/customer/orders/"+orderId;
    }
    @PostMapping("/shops/{shopOrderId}/reorder")
    public String reorderShop(@PathVariable Long shopOrderId,Authentication authentication,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        try {
            var result=orders.reorderShop(customerId,shopOrderId);
            if(result.addedLines()>0) flash.addFlashAttribute("successMessage","Đã thêm "+result.addedLines()+" phân loại vào giỏ theo giá hiện tại. Vui lòng kiểm tra trước khi đặt hàng.");
            else flash.addFlashAttribute("errorMessage","Chưa thêm được sản phẩm nào từ đơn này. Xem các sản phẩm không khả dụng bên dưới.");
            flash.addFlashAttribute("reorderSkipped",result.skipped()); return "redirect:/cart";
        } catch(CustomerOrderException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); return "redirect:/customer/orders/shops/"+shopOrderId; }
    }
    @PostMapping("/{orderId}/retry-payment")
    public String retryPayment(@PathVariable Long orderId,Authentication authentication,
                               HttpServletRequest request,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        try {
            var result=vnpayPayments.retryPayment(orderId,customerId,request.getRemoteAddr());
            if(result.started()) return "redirect:"+result.paymentUrl();
            flash.addFlashAttribute("errorMessage",result.message());
        } catch(CustomerOrderException | VietFreshHub.exception.ResourceNotFoundException | CheckoutException ex) {
            flash.addFlashAttribute("errorMessage",ex.getMessage());
        }
        return "redirect:/customer/orders/"+orderId;
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() { var view=new ModelAndView("customer/order-not-found"); view.setStatus(HttpStatus.NOT_FOUND); return view; }
}
