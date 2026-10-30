package VietFreshHub.delivery.controller;

import VietFreshHub.delivery.dto.DeliveryMonitoringDetail;
import VietFreshHub.delivery.dto.AdminDeliveryMonitoringDetail;
import VietFreshHub.delivery.dto.DeliveryMonitoringRow;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.service.DeliveryMonitoringService;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.order.service.OrderLabels;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DeliveryMonitoringController {

    private final DeliveryMonitoringService deliveryMonitoringService;
    private final DeliveryAssignmentService deliveryAssignmentService;

    @GetMapping("/seller/deliveries")
    public String managedShopDeliveries(@RequestParam(required = false) DeliveryStatus status,
                                         @RequestParam(defaultValue = "") String orderCode,
                                         @RequestParam(defaultValue = "placedAt") String sortBy,
                                         @RequestParam(defaultValue = "desc") String direction,
                                         @RequestParam(defaultValue = "0") int page,
                                         Authentication authentication, Model model) {
        return showList(deliveryMonitoringService.getManagedShopDeliveries(
                        authentication, status, orderCode, sortBy, direction, page),
                status, orderCode, sortBy, direction, "/seller/deliveries", "/seller",
                "Giao hàng của cửa hàng bạn quản lý", model);
    }

    @GetMapping("/seller/deliveries/{deliveryId}")
    public String managedShopDeliveryDetail(@PathVariable Long deliveryId, Authentication authentication, Model model) {
        return showDetail(deliveryMonitoringService.getManagedShopDeliveryDetail(deliveryId, authentication),
                "/seller/deliveries", model);
    }

    @GetMapping("/admin/deliveries")
    public String adminDeliveries(@RequestParam(required = false) DeliveryStatus status,
                                   @RequestParam(defaultValue = "") String orderCode,
                                   @RequestParam(required = false) Long shopId,
                                   @RequestParam(defaultValue = "placedAt") String sortBy,
                                   @RequestParam(defaultValue = "desc") String direction,
                                   @RequestParam(defaultValue = "0") int page,
                                   Authentication authentication, Model model) {
        model.addAttribute("shops", deliveryMonitoringService.getAdminShopOptions(authentication));
        model.addAttribute("selectedShopId", shopId);
        return showList(deliveryMonitoringService.getAdminDeliveries(
                        authentication, status, orderCode, shopId, sortBy, direction, page),
                status, orderCode, sortBy, direction, "/admin/deliveries", "/admin/accounts",
                "Giao hàng toàn hệ thống", model);
    }

    @GetMapping("/admin/deliveries/{deliveryId}")
    public String adminDeliveryDetail(@PathVariable Long deliveryId, Authentication authentication, Model model) {
        AdminDeliveryMonitoringDetail detail = deliveryMonitoringService.getAdminDeliveryDetail(deliveryId, authentication);
        model.addAttribute("adminDetail", detail);
        return showDetail(detail.deliveryDetail(), "/admin/deliveries", model);
    }

    @PostMapping("/seller/deliveries/{deliveryId}/confirm-return")
    public String confirmGoodsReturn(@PathVariable Long deliveryId, Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        deliveryAssignmentService.confirmGoodsReturn(deliveryId, authentication);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận nhận lại hàng và hoàn tất phân công.");
        return "redirect:/seller/deliveries/" + deliveryId;
    }

    private String showList(Page<DeliveryMonitoringRow> deliveries, DeliveryStatus status,
                             String orderCode, String sortBy, String direction, String basePath,
                             String homePath, String scopeLabel, Model model) {
        if (status == DeliveryStatus.ASSIGNED) {
            status = DeliveryStatus.UNASSIGNED;
        }
        Map<DeliveryStatus, String> statusOptions = new LinkedHashMap<>();
        for (DeliveryStatus option : DeliveryStatus.values()) {
            if (option != DeliveryStatus.REDELIVERY_SCHEDULED && option != DeliveryStatus.ASSIGNED
                    && option != DeliveryStatus.CANCELLED) {
                statusOptions.put(option, option == DeliveryStatus.ACCEPTED ? "Đã phân công" : OrderLabels.delivery(option.name()));
            }
        }
        model.addAttribute("deliveriesPage", deliveries);
        model.addAttribute("statusOptions", statusOptions);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("orderCode", orderCode.strip());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("basePath", basePath);
        model.addAttribute("homePath", homePath);
        model.addAttribute("scopeLabel", scopeLabel);
        return "delivery/monitoring-list";
    }

    private String showDetail(DeliveryMonitoringDetail detail, String basePath, Model model) {
        model.addAttribute("deliveryDetail", detail);
        model.addAttribute("basePath", basePath);
        return "delivery/monitoring-detail";
    }
}
