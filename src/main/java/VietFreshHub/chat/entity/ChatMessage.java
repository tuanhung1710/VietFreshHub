package VietFreshHub.chat.entity;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

@Entity
@Table(schema = "dbo", name = "messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Nationalized
    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String content;

    @Column(name = "message_type", nullable = false, length = 30)
    private String messageType = "TEXT";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected ChatMessage() {
    }

    public static ChatMessage text(Conversation conversation, User sender, String content) {
        ChatMessage message = new ChatMessage();
        message.conversation = conversation;
        message.sender = sender;
        message.content = content;
        return message;
    }

    public Long getMessageId() {
        return messageId;
    }

    public User getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
