package VietFreshHub.admin.controller;

import VietFreshHub.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    @GetMapping("/admin/dashboard")
    public String dashboard(
            @RequestParam(defaultValue = "LAST_30_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int noOrderPage,
            Model model) {
        model.addAttribute("period", period);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        try {
            model.addAttribute("dashboard", dashboardService.getDashboard(period, from, to, noOrderPage));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("filterError", exception.getMessage());
        }
        return "admin/dashboard";
    }
}
