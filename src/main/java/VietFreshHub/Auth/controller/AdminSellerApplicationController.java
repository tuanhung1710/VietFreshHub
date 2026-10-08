package VietFreshHub.Auth.controller;

import VietFreshHub.Auth.entity.SellerApplicationStatus;
import VietFreshHub.Auth.service.AdminSellerApplicationService;
import VietFreshHub.Auth.service.SellerApplicationDecisionException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/seller-applications")
public class AdminSellerApplicationController {

    private final AdminSellerApplicationService service;

    public AdminSellerApplicationController(AdminSellerApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) SellerApplicationStatus status,
            Model model
    ) {
        model.addAttribute("applications", service.getApplications(status));
        model.addAttribute("counts", service.getStatusCounts());
        model.addAttribute("selectedStatus", status);
        return "admin/list";
    }

    @GetMapping("/{applicationId}")
    public String detail(@PathVariable Long applicationId, Model model) {
        try {
            model.addAttribute("applicationDetail", service.getDetail(applicationId));
            System.out.println(service.getDetail(applicationId).toString());
            return "admin/detail";
        } catch (SellerApplicationDecisionException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping("/{applicationId}/start-review")
    public String startReview(
            @PathVariable Long applicationId,
            @RequestParam(required = false) String note,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return runAction(applicationId, redirectAttributes,
                () -> service.startReview(applicationId, authentication.getName(), note),
                "Đã chuyển hồ sơ sang trạng thái đang thẩm định.");
    }

    @PostMapping("/{applicationId}/documents/{documentId}/verify")
    public String verifyDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return runAction(applicationId, redirectAttributes,
                () -> service.verifyDocument(applicationId, documentId, authentication.getName()),
                "Đã xác minh giấy tờ.");
    }
    @PostMapping("/{applicationId}/documents/{documentId}/reject")
    public String rejectDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return runAction(
                applicationId,
                redirectAttributes,
                () -> service.rejectDocument(
                        applicationId,
                        documentId,
                        authentication.getName()
                ),
                "Đã từ chối giấy tờ và hồ sơ đăng ký."
        );
    }
    @PostMapping("/{applicationId}/approve")
    public String approve(
            @PathVariable Long applicationId,
            @RequestParam(required = false) String note,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return runAction(applicationId, redirectAttributes,
                () -> service.approve(applicationId, authentication.getName(), note),
                "Đã phê duyệt hồ sơ và cấp quyền Store Manager.");
    }

    @PostMapping("/{applicationId}/reject")
    public String reject(
            @PathVariable Long applicationId,
            @RequestParam String reason,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return runAction(applicationId, redirectAttributes,
                () -> service.reject(applicationId, authentication.getName(), reason),
                "Đã từ chối hồ sơ.");
    }

    private String runAction(
            Long applicationId,
            RedirectAttributes redirectAttributes,
            Runnable action,
            String successMessage
    ) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (SellerApplicationDecisionException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/seller-applications/" + applicationId;
    }
}
