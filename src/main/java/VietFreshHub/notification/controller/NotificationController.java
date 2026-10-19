package VietFreshHub.notification.controller;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.notification.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final AuthService authService;

    @GetMapping
    public String list(Authentication authentication,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Long userId = authService.getCurrentUserId(authentication);
        model.addAttribute("notificationPage", notificationService.getMyNotifications(userId, page));
        return "notification/notification-list";
    }

    @PostMapping("/{notificationId}/read")
    public String markAsRead(Authentication authentication, @PathVariable Long notificationId) {
        Long userId = authService.getCurrentUserId(authentication);
        notificationService.markAsRead(userId, notificationId);
        return "redirect:/notifications";
    }
}
