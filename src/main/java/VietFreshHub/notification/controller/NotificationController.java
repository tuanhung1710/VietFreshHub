package VietFreshHub.notification.controller;

import VietFreshHub.delivery.service.DeliveryNotificationService;
import VietFreshHub.notification.dto.NotificationRow;
import VietFreshHub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final DeliveryNotificationService deliveryNotificationService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("notificationsPage", notificationService.getNotifications(authentication, page));
        return "notifications/list";
    }

    @GetMapping("/unread-count")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("unreadCount", notificationService.countUnread(authentication)));
    }

    @PostMapping("/{notificationId}/read")
    public String markRead(@PathVariable Long notificationId, @RequestParam(defaultValue = "0") int page,
                           Authentication authentication) {
        notificationService.markRead(notificationId, authentication);
        return "redirect:/notifications?page=" + Math.max(0, page);
    }

    @GetMapping("/{notificationId}/open")
    public String open(@PathVariable Long notificationId, Authentication authentication) {
        NotificationRow notification = notificationService.getOwnedNotification(notificationId, authentication);
        return "redirect:" + deliveryNotificationService.getDestination(notification, authentication);
    }
}
