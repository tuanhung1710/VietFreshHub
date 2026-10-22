package VietFreshHub.order.controller;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.order.dto.*;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller @RequiredArgsConstructor @RequestMapping("/customer/orders")
public class CustomerOrderController {
    private final CurrentCustomerService currentCustomer;
    private final CustomerOrderService orders;
    private final CartService carts;
    private final CustomerOrderQueryService readOrders;
    @GetMapping
    public String history(@Valid @ModelAttribute("filter") CustomerOrderFilterRequest filter,BindingResult errors,Authentication authentication,Model model) {
        Long customerId=currentCustomer.requireCustomerId(authentication);
        model.addAttribute("invalidFilter",errors.hasErrors());
        if(errors.hasErrors()) {
            model.addAttribute("filterError","Bộ lọc không hợp lệ. Vui lòng sửa các thông tin bên dưới rồi lọc lại.");
            model.addAttribute("filterMessages",errors.getAllErrors().stream().map(error -> {
                if(error instanceof FieldError field && field.isBindingFailure())
                    return "page".equals(field.getField()) ? "Trang phải là số nguyên hợp lệ." : "Ngày lọc phải có định dạng yyyy-MM-dd.";
                return error.getDefaultMessage();
            }).toList());
            model.addAttribute("orderPage",Page.empty());
            model.addAttribute("orderSummaries",Map.of());
        } else {
            int requestedPage=filter.getPage();
            var page=orders.history(customerId,filter);
            model.addAttribute("orderPage",page);
            model.addAttribute("orderSummaries",orders.historySummaries(customerId,
                    page.getContent().stream().map(OrderListRow::orderId).toList()));
            if(requestedPage!=page.getNumber()+1)
                model.addAttribute("pageNotice","Trang yêu cầu không còn tồn tại. Đang hiển thị trang " + (page.getNumber()+1) + ".");
        }
        model.addAttribute("cartBadgeTotal",readOrders.getCartBadgeTotal(customerId));
        Map<String,String> options=new LinkedHashMap<>();
        for (var stage : java.util.List.of(CustomerOrderStage.WAITING_PAYMENT, CustomerOrderStage.WAITING_CONFIRMATION,
                CustomerOrderStage.PREPARING, CustomerOrderStage.AWAITING_DELIVERY, CustomerOrderStage.SHIPPING,
                CustomerOrderStage.COMPLETED, CustomerOrderStage.CANCELLED)) options.put(stage.name(), stage.label());
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
