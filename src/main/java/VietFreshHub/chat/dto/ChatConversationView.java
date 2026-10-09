package VietFreshHub.chat.dto;

import java.time.LocalDateTime;

public record ChatConversationView(
        Long conversationId,
        Long shopId,
        String shopName,
        Long customerId,
        String customerName,
        String otherPartyName,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}
