package VietFreshHub.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageView(
        Long messageId,
        Long senderUserId,
        String senderName,
        String content,
        LocalDateTime createdAt,
        boolean mine
) {
}
