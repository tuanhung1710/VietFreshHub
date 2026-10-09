package VietFreshHub.chat.controller;

import VietFreshHub.chat.dto.ChatConversationView;
import VietFreshHub.chat.dto.ChatMessageView;
import VietFreshHub.chat.dto.ChatShopOption;
import VietFreshHub.chat.dto.ChatStartRequest;
import VietFreshHub.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping("/customer/shops")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ChatShopOption> customerShops(Authentication authentication) {
        return chatService.listCustomerShops(authentication.getName());
    }

    @GetMapping("/customer/conversations")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ChatConversationView> customerConversations(Authentication authentication) {
        return chatService.listCustomerConversations(authentication.getName());
    }

    @PostMapping("/customer/conversations")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ChatConversationView startConversation(
            Authentication authentication,
            @Valid @RequestBody ChatStartRequest request
    ) {
        return chatService.startCustomerConversation(authentication.getName(), request.shopId());
    }

    @GetMapping("/shop/conversations")
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public List<ChatConversationView> shopConversations(Authentication authentication) {
        return chatService.listShopConversations(authentication.getName());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageView> messages(
            Authentication authentication,
            @PathVariable Long conversationId
    ) {
        return chatService.listMessages(authentication.getName(), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/read")
    public void markRead(Authentication authentication, @PathVariable Long conversationId) {
        chatService.markMessagesRead(authentication.getName(), conversationId);
    }
}
