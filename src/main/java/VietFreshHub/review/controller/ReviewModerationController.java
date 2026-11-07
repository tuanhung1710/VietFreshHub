package VietFreshHub.review.controller;

import VietFreshHub.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ReviewModerationController {
    private final ReviewService reviewService;

    @GetMapping("/admin/review-reports")
    public String reports(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reports", reviewService.pendingReports(page));
        return "admin/review-reports";
    }

    @PostMapping("/admin/review-reports/{reportId}/moderate")
    public String moderateReport(@PathVariable Long reportId, @RequestParam String action,
                                 Authentication authentication, RedirectAttributes flash) {
        reviewService.moderate(authentication, reportId, action);
        flash.addFlashAttribute("successMessage", "Đã xử lý báo cáo đánh giá.");
        return "redirect:/admin/review-reports";
    }

}
