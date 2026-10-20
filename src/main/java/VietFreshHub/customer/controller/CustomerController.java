package VietFreshHub.customer.controller;

import VietFreshHub.customer.dto.CustomerProfileRequest;
import VietFreshHub.customer.service.CustomerProfileService;
import VietFreshHub.sellerapplication.service.CloudinaryStorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerProfileService customerProfileService;
    private final CloudinaryStorageService cloudinaryStorageService;

    public CustomerController(
            CustomerProfileService customerProfileService,
            CloudinaryStorageService cloudinaryStorageService
    ) {
        this.customerProfileService = customerProfileService;
        this.cloudinaryStorageService = cloudinaryStorageService;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("profile", customerProfileService.getProfile(authentication.getName()));
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profile") CustomerProfileRequest profile,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile
    ) {
        if (!bindingResult.hasErrors()) {
            CloudinaryStorageService.UploadedAvatar uploadedAvatar = null;
            try {
                if (avatarFile != null && !avatarFile.isEmpty()) {
                    uploadedAvatar = cloudinaryStorageService.uploadPublicAvatar(avatarFile);
                }

                String previousAvatarUrl = customerProfileService.updateProfile(
                        authentication.getName(),
                        profile,
                        uploadedAvatar == null ? null : uploadedAvatar.secureUrl()
                );
                if (uploadedAvatar != null) {
                    cloudinaryStorageService.deleteManagedAvatarByUrl(previousAvatarUrl);
                }
                redirectAttributes.addFlashAttribute("successMessage", "Thông tin cá nhân đã được cập nhật.");
                return "redirect:/customer/profile";
            } catch (IllegalArgumentException exception) {
                if (uploadedAvatar != null) {
                    cloudinaryStorageService.deleteUploadedAvatar(uploadedAvatar.publicId());
                }
                bindingResult.reject("profile.update.failed", exception.getMessage());
            } catch (RuntimeException exception) {
                if (uploadedAvatar != null) {
                    cloudinaryStorageService.deleteUploadedAvatar(uploadedAvatar.publicId());
                }
                bindingResult.reject(
                        "profile.update.failed",
                        "Không thể cập nhật hồ sơ lúc này. Vui lòng thử lại."
                );
            }
        }

        CustomerProfileRequest currentProfile = customerProfileService.getProfile(authentication.getName());
        profile.setEmail(currentProfile.getEmail());
        profile.setAvatarUrl(currentProfile.getAvatarUrl());
        model.addAttribute("profile", profile);
        return "customer/profile";
    }
}
