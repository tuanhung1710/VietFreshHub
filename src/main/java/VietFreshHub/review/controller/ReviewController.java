package VietFreshHub.review.controller;

import VietFreshHub.review.dto.ReviewForm;
import VietFreshHub.review.dto.ReviewReportForm;
import VietFreshHub.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/reviews/product/{productId}")
    public String productReviews(@PathVariable Long productId, @RequestParam(defaultValue = "0") int page,
                                 Authentication authentication, Model model) {
        model.addAttribute("targetType", "product");
        model.addAttribute("targetId", productId);
        model.addAttribute("reviews", reviewService.productReviews(productId, page));
        model.addAttribute("rating", reviewService.productRating(productId));
        addCustomerForm(authentication, productId, null, model);
        return "review/list";
    }

    @GetMapping("/reviews/shop/{shopId}")
    public String shopReviews(@PathVariable Long shopId, @RequestParam(defaultValue = "0") int page,
                              Authentication authentication, Model model) {
        model.addAttribute("targetType", "shop");
        model.addAttribute("targetId", shopId);
        model.addAttribute("reviews", reviewService.shopReviews(shopId, page));
        model.addAttribute("rating", reviewService.shopRating(shopId));
        addCustomerForm(authentication, null, shopId, model);
        return "review/list";
    }

    @GetMapping("/reviews/mine")
    public String myReviews(Authentication authentication, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviewService.myReviews(authentication, page));
        model.addAttribute("reviewForm", new ReviewForm(null, null));
        return "review/mine";
    }

    @PostMapping("/reviews/product/{productId}")
    public String createProductReview(@PathVariable Long productId, @RequestParam Long orderItemId,
                                      @Valid @ModelAttribute("reviewForm") ReviewForm form,
                                      BindingResult errors, Authentication authentication, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", "Điểm đánh giá phải từ 1 đến 5.");
        else {
            reviewService.createProductReview(authentication, productId, orderItemId, form);
            flash.addFlashAttribute("successMessage", "Đánh giá đã gửi và đang chờ duyệt.");
        }
        return "redirect:/reviews/product/" + productId;
    }

    @PostMapping("/reviews/shop/{shopId}")
    public String createShopReview(@PathVariable Long shopId, @RequestParam Long shopOrderId,
                                   @Valid @ModelAttribute("reviewForm") ReviewForm form,
                                   BindingResult errors, Authentication authentication, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", "Điểm đánh giá phải từ 1 đến 5.");
        else {
            reviewService.createShopReview(authentication, shopId, shopOrderId, form);
            flash.addFlashAttribute("successMessage", "Đánh giá đã gửi và đang chờ duyệt.");
        }
        return "redirect:/reviews/shop/" + shopId;
    }

    @PostMapping("/reviews/{reviewId}/edit")
    public String edit(@PathVariable Long reviewId, @Valid @ModelAttribute("reviewForm") ReviewForm form,
                       BindingResult errors, Authentication authentication, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", "Nội dung đánh giá không hợp lệ.");
        else {
            reviewService.edit(authentication, reviewId, form);
            flash.addFlashAttribute("successMessage", "Đã cập nhật đánh giá đang chờ duyệt.");
        }
        return "redirect:/reviews/mine";
    }

    @PostMapping("/reviews/{reviewId}/delete")
    public String delete(@PathVariable Long reviewId, Authentication authentication, RedirectAttributes flash) {
        reviewService.delete(authentication, reviewId);
        flash.addFlashAttribute("successMessage", "Đã xóa đánh giá.");
        return "redirect:/reviews/mine";
    }

    @PostMapping("/reviews/{reviewId}/report")
    public String report(@PathVariable Long reviewId, @Valid @ModelAttribute ReviewReportForm form,
                         BindingResult errors, Authentication authentication, RedirectAttributes flash,
                         @RequestParam(defaultValue = "/home") String returnTo) {
        if (errors.hasErrors()) flash.addFlashAttribute("errorMessage", "Vui lòng nhập lý do báo cáo.");
        else {
            reviewService.report(authentication, reviewId, form);
            flash.addFlashAttribute("successMessage", "Đã gửi báo cáo đánh giá.");
        }
        return "redirect:" + safeReturn(returnTo);
    }

    private void addCustomerForm(Authentication authentication, Long productId, Long shopId, Model model) {
        boolean customer = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        model.addAttribute("reviewForm", new ReviewForm(null, null));
        model.addAttribute("reportForm", new ReviewReportForm(null, null));
        model.addAttribute("eligiblePurchases", customer
                ? productId != null ? reviewService.eligibleOrderItems(authentication, productId)
                : reviewService.eligibleShopOrders(authentication, shopId)
                : java.util.List.of());
        model.addAttribute("canReview", customer);
    }

    private String safeReturn(String path) {
        return path != null && (path.startsWith("/reviews/product/") || path.startsWith("/reviews/shop/"))
                ? path : "/home";
    }
}
