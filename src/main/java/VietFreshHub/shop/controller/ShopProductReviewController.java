package VietFreshHub.shop.controller;

import VietFreshHub.review.service.ReviewService;
import VietFreshHub.review.dto.ReviewReportForm;
import VietFreshHub.review.dto.ShopReplyForm;
import jakarta.validation.Valid;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class ShopProductReviewController {
    private final ShopService shops;
    private final ReviewService reviews;

    @GetMapping("/seller/reviews")
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public String list(Authentication authentication, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviewsPage", reviews.shopProductReviews(shops.getManagedShopId(authentication), page));
        return "store_manager/reviews";
    }

    @PostMapping("/seller/reviews/{reviewId}/reply")
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public String reply(@PathVariable Long reviewId, @Valid ShopReplyForm form, BindingResult errors,
                        Authentication authentication, @RequestParam(defaultValue = "0") int page,
                        RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", "Vui lòng nhập phản hồi tối đa 3000 ký tự.");
        else try {
            reviews.replyAsShopOwner(authentication, reviewId, form);
            flash.addFlashAttribute("successMessage", "Đã gửi phản hồi đánh giá.");
        } catch (ResponseStatusException error) {
            flash.addFlashAttribute("errorMessage", error.getReason());
        }
        return "redirect:/seller/reviews?page=" + Math.max(0, page);
    }

    @PostMapping("/seller/reviews/{reviewId}/report")
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public String report(@PathVariable Long reviewId, @Valid ReviewReportForm form, BindingResult errors,
                         Authentication authentication, @RequestParam(defaultValue = "0") int page,
                         RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", "Vui lòng chọn lý do báo cáo hợp lệ.");
        else try {
            reviews.reportAsShopOwner(authentication, reviewId, form);
            flash.addFlashAttribute("successMessage", "Đã gửi báo cáo đến quản trị viên.");
        } catch (ResponseStatusException error) {
            flash.addFlashAttribute("errorMessage", error.getReason());
        }
        return "redirect:/seller/reviews?page=" + Math.max(0, page);
    }
}
