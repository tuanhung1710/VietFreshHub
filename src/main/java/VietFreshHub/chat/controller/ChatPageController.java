package VietFreshHub.chat.controller;

import VietFreshHub.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ChatPageController {

    private final ChatService chatService;

    @GetMapping("/customer/chat")
    public String customerChat(Authentication authentication, Model model) {
        model.addAttribute("chatMode", "customer");
        model.addAttribute("currentUserId", chatService.getCurrentUserId(authentication.getName()));
        model.addAttribute("conversations", chatService.listCustomerConversations(authentication.getName()));
        model.addAttribute("shops", chatService.listCustomerShops(authentication.getName()));
        return "chat/chat";
    }

    @GetMapping("/store_manager/chat")
    public String shopChat(Authentication authentication, Model model) {
        model.addAttribute("chatMode", "shop");
        model.addAttribute("currentUserId", chatService.getCurrentUserId(authentication.getName()));
        model.addAttribute("conversations", chatService.listShopConversations(authentication.getName()));
        model.addAttribute("shops", java.util.List.of());
        return "chat/chat";
    }
}
