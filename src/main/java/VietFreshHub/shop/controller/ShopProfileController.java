package VietFreshHub.shop.controller;

import VietFreshHub.shop.service.ShopProfileService;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.sellerapplication.service.CloudinaryStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import VietFreshHub.shop.dto.ShopProfileRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/profile")
public class ShopProfileController {
    private final ShopProfileService profiles;
    private final ShopService shops;
    private final CloudinaryStorageService images;

    @GetMapping
    public String profile(Authentication authentication, Model model) {
        preparePage(profiles.getManagedProfile(authentication), model);
        return "store_manager/profile";
    }

    @GetMapping("/edit")
    public String edit(Authentication authentication, Model model) {
        var shop = profiles.getManagedProfile(authentication);
        preparePage(shop, model);
        var form = new ShopProfileRequest();
        form.setShopName(shop.getShopName());
        form.setDescription(shop.getDescription());
        form.setPhone(shop.getPhone());
        form.setEmail(shop.getEmail());
        form.setLogoUrl(shop.getLogoUrl());
        form.setBannerUrl(shop.getBannerUrl());
        model.addAttribute("profileForm", form);
        return "store_manager/profile-form";
    }

    @PostMapping
    public String update(Authentication authentication,
                         @Valid @ModelAttribute("profileForm") ShopProfileRequest form,
                         BindingResult errors, Model model, RedirectAttributes flash,
                         @RequestParam(required = false) MultipartFile logoFile,
                         @RequestParam(required = false) MultipartFile bannerFile) {
        var shop = profiles.getManagedProfile(authentication);
        validateImage(logoFile, "logoUrl", errors);
        validateImage(bannerFile, "bannerUrl", errors);
        if (errors.hasErrors()) {
            preparePage(shop, model);
            return "store_manager/profile-form";
        }
        CloudinaryStorageService.UploadedAvatar logo = null;
        CloudinaryStorageService.UploadedAvatar banner = null;
        String logoUrl = form.getLogoUrl();
        String bannerUrl = form.getBannerUrl();
        try {
            if (logoFile != null && !logoFile.isEmpty()) {
                logo = images.uploadPublicAvatar(logoFile);
                form.setLogoUrl(logo.secureUrl());
            }
            if (bannerFile != null && !bannerFile.isEmpty()) {
                banner = images.uploadPublicAvatar(bannerFile);
                form.setBannerUrl(banner.secureUrl());
            }
            profiles.update(authentication, form);
        } catch (RuntimeException exception) {
            if (logo != null) images.deleteUploadedAvatar(logo.publicId());
            if (banner != null) images.deleteUploadedAvatar(banner.publicId());
            form.setLogoUrl(logoUrl);
            form.setBannerUrl(bannerUrl);
            errors.reject("profile.update.failed", "Không thể lưu hồ sơ hoặc tải ảnh lên. Vui lòng kiểm tra ảnh và thử lại.");
            preparePage(shop, model);
            return "store_manager/profile-form";
        }
        flash.addFlashAttribute("successMessage", "Đã cập nhật hồ sơ cửa hàng.");
        return "redirect:/seller/profile";
    }

    private void preparePage(Shop shop, Model model) {
        model.addAttribute("shop", shop);
        model.addAttribute("pickupLocation", shops.findLocationByShopId(shop.getShopId()).orElse(null));
    }

    private void validateImage(MultipartFile file, String field, BindingResult errors) {
        if (file == null || file.isEmpty()) return;
        if (file.getSize() > 1024 * 1024) {
            errors.rejectValue(field, "image.size", "Ảnh không được vượt quá 1 MB.");
        } else if (!"image/jpeg".equals(file.getContentType()) && !"image/png".equals(file.getContentType())) {
            errors.rejectValue(field, "image.type", "Vui lòng chọn ảnh JPG hoặc PNG.");
        }
    }

    @PostMapping("/availability")
    public String availability(Authentication authentication, @RequestParam String status, RedirectAttributes flash) {
        try {
            profiles.setAvailability(authentication, status);
            flash.addFlashAttribute("successMessage", "Đã cập nhật trạng thái nhận đơn.");
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/seller/profile";
    }
}
