package VietFreshHub.chat.repository;

import VietFreshHub.chat.entity.ChatMessage;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(
            Long conversationId,
            Limit limit
    );

    Optional<ChatMessage> findFirstByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(
            Long conversationId
    );

    long countByConversation_ConversationIdAndSender_UserIdNotAndReadAtIsNull(
            Long conversationId,
            Long senderUserId
    );

    @Modifying
    @Transactional
    @Query("""
            update ChatMessage m
            set m.readAt = :readAt
            where m.conversation.conversationId = :conversationId
              and m.sender.userId <> :readerId
              and m.readAt is null
            """)
    int markUnreadMessagesRead(
            @Param("conversationId") Long conversationId,
            @Param("readerId") Long readerId,
            @Param("readAt") LocalDateTime readAt
    );
}
