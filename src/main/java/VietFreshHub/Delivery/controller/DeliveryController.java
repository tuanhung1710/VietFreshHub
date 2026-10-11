package VietFreshHub.Delivery.controller;

import VietFreshHub.Delivery.service.DeliveryAssignmentService;
import VietFreshHub.Delivery.service.DeliveryStaffActionService;
import VietFreshHub.Delivery.service.DeliveryStaffQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryStaffQueryService deliveryStaffQueryService;
    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryStaffActionService deliveryStaffActionService;

    @GetMapping("/delivery")
    public String showDeliveryPage(Authentication authentication, Model model) {
        model.addAttribute("assignedDeliveries", deliveryStaffQueryService.getAssignedDeliveries(authentication));
        return "delivery/home";
    }

    @GetMapping("/delivery/assignments/{assignmentId}")
    public String showDeliveryDetail(@PathVariable Long assignmentId, Authentication authentication, Model model) {
        model.addAttribute("deliveryDetail",
                deliveryStaffQueryService.getAssignedDeliveryDetail(assignmentId, authentication));
        return "delivery/detail";
    }

    @PostMapping("/delivery/assignments/{assignmentId}/accept")
    public String acceptAssignment(@PathVariable Long assignmentId, Authentication authentication) {
        deliveryAssignmentService.accept(assignmentId, authentication);
        return "redirect:/delivery/assignments/" + assignmentId;
    }

    @PostMapping("/delivery/assignments/{assignmentId}/reject")
    public String rejectAssignment(@PathVariable Long assignmentId, Authentication authentication) {
        deliveryAssignmentService.reject(assignmentId, authentication);
        return "redirect:/delivery";
    }

    @PostMapping("/delivery/assignments/{assignmentId}/pickup")
    public String confirmPickup(@PathVariable Long assignmentId, Authentication authentication) {
        deliveryStaffActionService.confirmPickup(assignmentId, authentication);
        return "redirect:/delivery/assignments/" + assignmentId;
    }

    @PostMapping("/delivery/assignments/{assignmentId}/start-delivery")
    public String startDelivery(@PathVariable Long assignmentId, Authentication authentication) {
        deliveryStaffActionService.startDelivery(assignmentId, authentication);
        return "redirect:/delivery/assignments/" + assignmentId;
    }

    @PostMapping("/delivery/assignments/{assignmentId}/delivered")
    public String markDelivered(@PathVariable Long assignmentId, Authentication authentication) {
        deliveryStaffActionService.markDelivered(assignmentId, authentication);
        return "redirect:/delivery";
    }

    @PostMapping("/delivery/assignments/{assignmentId}/schedule-redelivery")
    public String scheduleRedelivery(@PathVariable Long assignmentId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                     LocalDateTime redeliveryScheduledAt,
                                     Authentication authentication) {
        deliveryStaffActionService.scheduleRedelivery(assignmentId, redeliveryScheduledAt, authentication);
        return "redirect:/delivery";
    }
}
