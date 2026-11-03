package VietFreshHub.sellerapplication.controller;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.sellerapplication.dto.SellerApplicationForm;
import VietFreshHub.sellerapplication.service.SellerApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService applicationService;
    private final UserRepository userRepository;

    @GetMapping({"/customer/apply", "/seller-onboarding/apply"})
    public String showForm(Authentication authentication, Model model, HttpServletRequest request) {
        User user = getCurrentUser(authentication);

        model.addAttribute("applicant", user);
        model.addAttribute("sellerApplicationForm", new SellerApplicationForm());
        model.addAttribute("applicationAction", applicationAction(request));

        return "customer/application";
    }

    @PostMapping(
            value = {"/customer/apply", "/seller-onboarding/apply"},
            consumes = "multipart/form-data"
    )
    public String submit(
            @Valid @ModelAttribute("sellerApplicationForm")
            SellerApplicationForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request,
            @RequestParam(value = "identityCardUploadToken", required = false)
            String identityCardUploadToken,
            @RequestParam(value = "businessLicenseUploadToken", required = false)
            String businessLicenseUploadToken
    ) {
        User user = getCurrentUser(authentication);
        String applicationAction = applicationAction(request);
        SellerApplicationService.StagedDocument identityCard =
                applicationService.getStagedDocument(
                        user,
                        identityCardUploadToken,
                        SellerApplicationService.IDENTITY_CARD
                );
        SellerApplicationService.StagedDocument businessLicense =
                applicationService.getStagedDocument(
                        user,
                        businessLicenseUploadToken,
                        SellerApplicationService.BUSINESS_LICENSE
                );

        if (identityCard == null) {
            bindingResult.rejectValue(
                    "identityCard",
                    "required",
                    "Vui lòng tải giấy tờ tùy thân lên."
            );
        }

        if (businessLicense == null) {
            bindingResult.rejectValue(
                    "businessLicense",
                    "required",
                    "Vui lòng tải giấy phép hoặc chứng nhận lên."
            );
        }

        if (bindingResult.hasErrors()) {
            addFormModel(model, user, applicationAction, identityCard, businessLicense);
            return "customer/application";
        }

        try {
            applicationService.submitStaged(
                    user,
                    form,
                    identityCardUploadToken,
                    businessLicenseUploadToken
            );
        } catch (IllegalArgumentException | IOException e) {
            bindingResult.reject("uploadError", e.getMessage());
            identityCard = applicationService.getStagedDocument(
                    user,
                    identityCardUploadToken,
                    SellerApplicationService.IDENTITY_CARD
            );
            businessLicense = applicationService.getStagedDocument(
                    user,
                    businessLicenseUploadToken,
                    SellerApplicationService.BUSINESS_LICENSE
            );
            addFormModel(model, user, applicationAction, identityCard, businessLicense);
            return "customer/application";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Hồ sơ đã được gửi để xét duyệt."
        );

        return request.getServletPath().startsWith("/seller-onboarding/")
                ? "redirect:/customer/seller-applications"
                : "redirect:/customer/apply/success";
    }

    @PostMapping(
            value = {
                    "/customer/apply/documents",
                    "/seller-onboarding/apply/documents"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> uploadDocument(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "previousUploadToken", required = false)
            String previousUploadToken
    ) {
        User user = getCurrentUser(authentication);
        try {
            return ResponseEntity.ok(applicationService.stageDocument(
                    user,
                    file,
                    documentType,
                    previousUploadToken
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(errorBody(exception.getMessage()));
        } catch (IOException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody("Không thể tải tài liệu lên kho lưu trữ riêng tư. Vui lòng thử lại."));
        }
    }

    @DeleteMapping({
            "/customer/apply/documents",
            "/seller-onboarding/apply/documents"
    })
    @ResponseBody
    public ResponseEntity<Void> discardDocument(
            Authentication authentication,
            @RequestParam("uploadToken") String uploadToken,
            @RequestParam("documentType") String documentType
    ) {
        User user = getCurrentUser(authentication);
        applicationService.discardStagedDocument(
                user.getUserId(),
                documentType,
                uploadToken
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customer/apply/success")
    public String showSuccessPage() {
        return "redirect:/customer/home";
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản đang đăng nhập"
                ));
    }

    private void addFormModel(
            Model model,
            User user,
            String applicationAction,
            SellerApplicationService.StagedDocument identityCard,
            SellerApplicationService.StagedDocument businessLicense
    ) {
        model.addAttribute("applicant", user);
        model.addAttribute("applicationAction", applicationAction);
        addStagedDocumentModel(model, "identityCard", identityCard);
        addStagedDocumentModel(model, "businessLicense", businessLicense);
    }

    private void addStagedDocumentModel(
            Model model,
            String fieldPrefix,
            SellerApplicationService.StagedDocument document
    ) {
        model.addAttribute(
                fieldPrefix + "UploadToken",
                document == null ? "" : document.uploadToken()
        );
        model.addAttribute(
                fieldPrefix + "PreviewUrl",
                document == null || document.previewUrl() == null ? "" : document.previewUrl()
        );
        model.addAttribute(
                fieldPrefix + "FileName",
                document == null ? "" : document.fileName()
        );
        model.addAttribute(
                fieldPrefix + "ContentType",
                document == null ? "" : document.contentType()
        );
    }

    private Map<String, String> errorBody(String message) {
        return Map.of(
                "message",
                message == null || message.isBlank()
                        ? "Không thể xử lý tài liệu. Vui lòng thử lại."
                        : message
        );
    }

    private String applicationAction(HttpServletRequest request) {
        return request.getServletPath().startsWith("/seller-onboarding/")
                ? "/seller-onboarding/apply"
                : "/customer/apply";
    }
}
