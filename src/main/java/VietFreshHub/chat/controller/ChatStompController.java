package VietFreshHub.chat.controller;

import VietFreshHub.chat.dto.ChatMessageView;
import VietFreshHub.chat.dto.ChatSendRequest;
import VietFreshHub.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{conversationId}/send")
    public void send(
            @DestinationVariable Long conversationId,
            @Valid @Payload ChatSendRequest request,
            Principal principal
    ) {
        if (principal == null) throw new AccessDeniedException("Đăng nhập để gửi tin nhắn.");
        ChatMessageView message = chatService.sendMessage(
                principal.getName(), conversationId, request.content());
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, message);
    }
}
