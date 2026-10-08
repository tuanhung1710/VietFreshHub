package VietFreshHub.Auth.controller;

import VietFreshHub.Auth.service.CustomerSellerApplicationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/seller-applications")
public class CustomerSellerApplicationController {

    private final CustomerSellerApplicationService service;

    public CustomerSellerApplicationController(
            CustomerSellerApplicationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute(
                "applications",
                service.getMyApplications(authentication.getName())
        );
        return "customer/seller-applications/list";
    }

    @GetMapping("/{applicationId}")
    public String detail(
            @PathVariable Long applicationId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            model.addAttribute(
                    "detail",
                    service.getMyApplication(applicationId, authentication.getName())
            );
            return "customer/seller-applications/detail";
        } catch (CustomerSellerApplicationService.SellerApplicationUnderReviewException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/customer/seller-applications";
        }
    }

    @PostMapping("/{applicationId}/documents/{documentId}/replace")
    public String replaceDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            service.replaceRejectedDocument(
                    applicationId,
                    documentId,
                    authentication.getName(),
                    file
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đã tải tài liệu thay thế. Tài liệu đang chờ admin xác minh."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/customer/seller-applications/" + applicationId;
    }

    @PostMapping("/{applicationId}/resubmit")
    public String resubmit(
            @PathVariable Long applicationId,
            @RequestParam String businessName,
            @RequestParam(required = false) String taxCode,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            service.resubmit(
                    applicationId,
                    authentication.getName(),
                    businessName,
                    taxCode
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Hồ sơ đã được gửi lại và đang chờ admin tiếp nhận."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/customer/seller-applications/" + applicationId;
    }
}