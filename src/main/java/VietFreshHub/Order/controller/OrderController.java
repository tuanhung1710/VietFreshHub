package VietFreshHub.Order.controller;

import VietFreshHub.Order.dto.IncomingOrderResponse;
import VietFreshHub.Order.dto.OrderFilterRequest;
import VietFreshHub.Order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/seller/incoming-orders")
    public String incomingOrders(@ModelAttribute("filter") OrderFilterRequest filter,
                                 @RequestParam(defaultValue = "0") int page,
                                 Authentication authentication, Model model) {
        return showOrders(orderService.getIncomingOrders(authentication, filter, page), "Đơn hàng mới",
                "Hiện không có đơn hàng mới.", "/seller/incoming-orders", "incoming", false, model);
    }

    @GetMapping("/seller/orders")
    public String allOrders(@ModelAttribute("filter") OrderFilterRequest filter,
                            @RequestParam(defaultValue = "0") int page,
                            Authentication authentication, Model model) {
        return showOrders(orderService.getAllOrders(authentication, filter, page), "Tất cả đơn",
                "Hiện không có đơn hàng.", "/seller/orders", "all", true, model);
    }

    @GetMapping("/seller/confirmed-orders")
    public String confirmedOrders(@ModelAttribute("filter") OrderFilterRequest filter,
                                  @RequestParam(defaultValue = "0") int page,
                                  Authentication authentication, Model model) {
        return showOrders(orderService.getConfirmedOrders(authentication, filter, page), "Đã xác nhận",
                "Hiện không có đơn hàng đã xác nhận.", "/seller/confirmed-orders", "confirmed", false, model);
    }

    @GetMapping("/seller/preparing-orders")
    public String preparingOrders(@ModelAttribute("filter") OrderFilterRequest filter,
                                  @RequestParam(defaultValue = "0") int page,
                                  Authentication authentication, Model model) {
        return showOrders(orderService.getPreparingOrders(authentication, filter, page), "Đang chuẩn bị",
                "Hiện không có đơn hàng đang chuẩn bị.", "/seller/preparing-orders", "preparing", false, model);
    }

    @GetMapping("/seller/ready-orders")
    public String readyOrders(@ModelAttribute("filter") OrderFilterRequest filter,
                              @RequestParam(defaultValue = "0") int page,
                              Authentication authentication, Model model) {
        return showOrders(orderService.getReadyOrders(authentication, filter, page), "Sẵn sàng giao",
                "Hiện không có đơn hàng sẵn sàng giao.", "/seller/ready-orders", "ready", false, model);
    }

    @GetMapping("/seller/processed-orders")
    public String processedOrders(@ModelAttribute("filter") OrderFilterRequest filter,
                                  @RequestParam(defaultValue = "0") int page,
                                  Authentication authentication, Model model) {
        Page<?> orders = orderService.getProcessedOrderHistory(authentication, filter, page);
        model.addAttribute("processedOrders", orders.getContent());
        addPageAttributes(orders, model);
        return "store_manager/processed-orders";
    }

    @GetMapping("/seller/orders/filter-count")
    public ResponseEntity<Long> filterCount(@RequestParam String view,
                                            @ModelAttribute OrderFilterRequest filter,
                                            Authentication authentication) {
        long count = orderService.countFilteredOrders(authentication, filter, view);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(count);
    }

    @GetMapping("/seller/orders/{shopOrderId}")
    public String orderDetail(@PathVariable Long shopOrderId, Authentication authentication, Model model) {
        model.addAttribute("orderDetail", orderService.getOrderDetail(shopOrderId, authentication));
        model.addAttribute("shopOrderId", shopOrderId);
        return "store_manager/order-detail";
    }

    @PostMapping("/seller/orders/{shopOrderId}/confirm")
    public String confirmOrder(@PathVariable Long shopOrderId, Authentication authentication) {
        orderService.confirmOrder(shopOrderId, authentication);
        return "redirect:/seller/orders/" + shopOrderId;
    }

    @PostMapping("/seller/orders/{shopOrderId}/prepare")
    public String startPreparing(@PathVariable Long shopOrderId, Authentication authentication) {
        orderService.startPreparing(shopOrderId, authentication);
        return "redirect:/seller/orders/" + shopOrderId;
    }

    @PostMapping("/seller/orders/{shopOrderId}/ready")
    public String markReady(@PathVariable Long shopOrderId, Authentication authentication) {
        orderService.markReady(shopOrderId, authentication);
        return "redirect:/seller/orders/" + shopOrderId;
    }

    @PostMapping("/seller/orders/bulk-confirm")
    public String confirmOrders(@RequestParam(required = false) List<Long> shopOrderIds,
                                Authentication authentication, RedirectAttributes redirectAttributes) {
        orderService.confirmOrders(shopOrderIds, authentication);
        redirectAttributes.addFlashAttribute("clearOrderSelection", "incoming");
        return "redirect:/seller/incoming-orders";
    }

    @PostMapping("/seller/orders/bulk-prepare")
    public String startPreparingOrders(@RequestParam(required = false) List<Long> shopOrderIds,
                                      Authentication authentication, RedirectAttributes redirectAttributes) {
        orderService.startPreparingOrders(shopOrderIds, authentication);
        redirectAttributes.addFlashAttribute("clearOrderSelection", "confirmed");
        return "redirect:/seller/confirmed-orders";
    }

    @PostMapping("/seller/orders/bulk-ready")
    public String markOrdersReady(@RequestParam(required = false) List<Long> shopOrderIds,
                                 Authentication authentication, RedirectAttributes redirectAttributes) {
        orderService.markOrdersReady(shopOrderIds, authentication);
        redirectAttributes.addFlashAttribute("clearOrderSelection", "preparing");
        return "redirect:/seller/preparing-orders";
    }

    @PostMapping("/seller/orders/bulk-invoices")
    public String bulkInvoices(@RequestParam(required = false) List<Long> shopOrderIds,
                               @RequestParam String view,
                               Authentication authentication, Model model) {
        model.addAttribute("invoices", orderService.getBulkInvoices(shopOrderIds, authentication, view));
        return "store_manager/bulk-invoices";
    }

    private String showOrders(Page<IncomingOrderResponse> orders, String pageTitle,
                              String emptyMessage, String filterAction, String orderView,
                              boolean showStatusFilter, Model model) {
        model.addAttribute("orders", orders.getContent());
        addPageAttributes(orders, model);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("emptyMessage", emptyMessage);
        model.addAttribute("filterAction", filterAction);
        model.addAttribute("orderView", orderView);
        model.addAttribute("showStatusFilter", showStatusFilter);
        model.addAttribute("bulkAction", switch (orderView) {
            case "incoming" -> "/seller/orders/bulk-confirm";
            case "confirmed" -> "/seller/orders/bulk-prepare";
            case "preparing" -> "/seller/orders/bulk-ready";
            case "ready" -> "/seller/orders/bulk-invoices";
            default -> null;
        });
        return "store_manager/incoming-orders";
    }

    private void addPageAttributes(Page<?> page, Model model) {
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalElements", page.getTotalElements());
        model.addAttribute("pageSize", page.getSize());
        model.addAttribute("pageOffset", page.getPageable().getOffset());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
