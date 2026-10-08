package VietFreshHub.Order.controller;
import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Order.dto.*;
import VietFreshHub.Order.exception.CustomerOrderException;
import VietFreshHub.Order.service.CustomerOrderService;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.util.OrderLabels;
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

@Controller @RequiredArgsConstructor @RequestMapping("/customer/orders")
public class CustomerOrderController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderService orders;
    private final CartService carts;
    @GetMapping
    public String history(@Valid @ModelAttribute("filter") OrderFilterRequest filter,BindingResult errors,Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        if(errors.hasErrors()) model.addAttribute("filterError","Bộ lọc không hợp lệ. Kiểm tra trang, mã đơn, trạng thái và khoảng ngày.");
        model.addAttribute("orderPage",orders.history(customerId,errors.hasErrors()?new OrderFilterRequest():filter));
        model.addAttribute("cart",carts.getActiveCart(customerId));
        java.util.Map<String,String> options=new java.util.LinkedHashMap<>();
        for(var status:Order.Status.values()) options.put(status.name(),OrderLabels.order(status));
        model.addAttribute("statusOptions",options);
        return "customer/orders";
    }
    @GetMapping("/{orderId}/tracking")
    public String tracking(@PathVariable Long orderId,Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication); model.addAttribute("tracking",orders.tracking(customerId,orderId));
        model.addAttribute("cart",carts.getActiveCart(customerId)); return "customer/order-tracking";
    }
    @PostMapping("/{orderId}/cancel")
    public String cancel(@PathVariable Long orderId,@Valid @ModelAttribute("cancelForm") OrderCancelRequest form,BindingResult errors,
                         Authentication authentication,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        if(errors.hasErrors()) { flash.addFlashAttribute("errorMessage","Lý do hủy tối đa 500 ký tự."); flash.addFlashAttribute("cancelReason",form.getReason()); }
        else try { orders.cancel(customerId,orderId,form.getReason()); flash.addFlashAttribute("successMessage","Đơn hàng đã hủy. Hàng đã giữ được giải phóng."); }
        catch(CustomerOrderException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/customer/orders/"+orderId;
    }
    @PostMapping("/{orderId}/reorder")
    public String reorder(@PathVariable Long orderId,Authentication authentication,RedirectAttributes flash) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        try {
            var result=orders.reorder(customerId,orderId);
            if(result.addedLines()>0) flash.addFlashAttribute("successMessage","Đã thêm "+result.addedLines()+" phân loại vào giỏ theo giá hiện tại. Vui lòng kiểm tra trước khi đặt hàng.");
            else flash.addFlashAttribute("errorMessage","Chưa thêm được sản phẩm nào từ đơn này. Xem các sản phẩm không khả dụng bên dưới.");
            flash.addFlashAttribute("reorderSkipped",result.skipped()); return "redirect:/cart";
        } catch(CustomerOrderException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); return "redirect:/customer/orders/"+orderId; }
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound() { var view=new ModelAndView("customer/order-not-found"); view.setStatus(HttpStatus.NOT_FOUND); return view; }
}
