package VietFreshHub.delivery.controller;

import VietFreshHub.delivery.dto.DeliveryFailureRequest;
import VietFreshHub.delivery.dto.DeliveryHistoryFilter;
import VietFreshHub.delivery.dto.DailyCodSummary;
import VietFreshHub.delivery.service.DeliveryAssignmentService;
import VietFreshHub.delivery.service.DeliveryStaffActionService;
import VietFreshHub.delivery.service.DeliveryStaffQueryService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryStaffQueryService deliveryStaffQueryService;
    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryStaffActionService deliveryStaffActionService;

    @GetMapping("/delivery")
    public String showDeliveryPage(Authentication authentication, Model model) {
        model.addAttribute("assignedDeliveries", deliveryStaffQueryService.getAssignedDeliveries(authentication));
        model.addAttribute("activeAssignmentCount", deliveryStaffQueryService.getActiveAssignmentCount(authentication));
        model.addAttribute("maxActiveAssignments", DeliveryAssignmentService.MAX_ACTIVE_ASSIGNMENTS);
        return "delivery/home";
    }

    @GetMapping("/delivery/available")
    public String showAvailableDeliveries(@RequestParam(defaultValue = "false") boolean fragment,
                                         Authentication authentication, Model model) {
        model.addAttribute("availableDeliveries", deliveryStaffQueryService.getAvailableDeliveries(authentication));
        return fragment ? "delivery/available :: deliveries" : "delivery/available";
    }

    @GetMapping("/delivery/available/{deliveryId}")
    public String showAvailableDeliveryDetail(@PathVariable Long deliveryId, Authentication authentication,
                                             Model model, HttpServletResponse response) {
        try {
            model.addAttribute("availableDelivery",
                    deliveryStaffQueryService.getAvailableDeliveryDetail(deliveryId, authentication));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw exception;
            }
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            model.addAttribute("availabilityMessage", exception.getReason());
        }
        return "delivery/available-detail";
    }

    @PostMapping("/delivery/available/{deliveryId}/claim")
    public String claimDelivery(@PathVariable Long deliveryId, Authentication authentication,
                                Model model, HttpServletResponse response) {
        try {
            deliveryAssignmentService.claimDelivery(deliveryId, authentication);
            return "redirect:/delivery/available";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw exception;
            }
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            model.addAttribute("errorMessage", exception.getReason());
            return showAvailableDeliveryDetail(deliveryId, authentication, model, response);
        }
    }

    @GetMapping("/delivery/cod-summary")
    public String showDailyCodSummary(@RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     Authentication authentication, Model model) {
        DailyCodSummary codSummary = deliveryStaffQueryService.getDailyCodSummary(authentication, date);
        model.addAttribute("codSummary", codSummary);
        model.addAttribute("isToday", codSummary.date().equals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        return "delivery/cod-summary";
    }

    @GetMapping("/delivery/history")
    public String history(@ModelAttribute("filter") DeliveryHistoryFilter filter, BindingResult errors,
                          @RequestParam(defaultValue = "0") int page, Authentication authentication,
                          Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorMessage", "Ngày, mã cửa hàng hoặc giá trị đơn hàng không hợp lệ. Vui lòng kiểm tra bộ lọc.");
            model.addAttribute("history", deliveryStaffQueryService.getDeliveryHistoryOptions(authentication));
        } else {
            try {
                model.addAttribute("history", deliveryStaffQueryService.getDeliveryHistory(authentication, filter, page));
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() != HttpStatus.BAD_REQUEST.value()) {
                    throw exception;
                }
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                model.addAttribute("errorMessage", exception.getReason());
                model.addAttribute("history", deliveryStaffQueryService.getDeliveryHistoryOptions(authentication));
            }
        }
        return "delivery/history";
    }

    @GetMapping("/delivery/history/{assignmentId}")
    public String historicalDetail(@PathVariable Long assignmentId, Authentication authentication, Model model) {
        model.addAttribute("record", deliveryStaffQueryService.getHistoricalDeliveryDetail(assignmentId, authentication));
        return "delivery/history-detail";
    }

    @GetMapping("/delivery/assignments/{assignmentId}")
    public String showDeliveryDetail(@PathVariable Long assignmentId, Authentication authentication, Model model) {
        model.addAttribute("deliveryDetail",
                deliveryStaffQueryService.getAssignedDeliveryDetail(assignmentId, authentication));
        model.addAttribute("failureRequest", new DeliveryFailureRequest());
        return "delivery/detail";
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
        return "redirect:/delivery/assignments/" + assignmentId;
    }

    @PostMapping("/delivery/assignments/{assignmentId}/failed")
    public String markFailed(@PathVariable Long assignmentId,
                              @Valid @ModelAttribute("failureRequest") DeliveryFailureRequest failureRequest,
                              BindingResult bindingResult, Authentication authentication, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                deliveryStaffActionService.markFailed(assignmentId, failureRequest.getFailureReason(), authentication);
                return "redirect:/delivery/assignments/" + assignmentId;
            } catch (ResponseStatusException exception) {
                bindingResult.reject("delivery.failure", exception.getReason());
            }
        }
        model.addAttribute("deliveryDetail",
                deliveryStaffQueryService.getAssignedDeliveryDetail(assignmentId, authentication));
        return "delivery/detail";
    }
}
