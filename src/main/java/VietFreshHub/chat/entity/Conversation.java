package VietFreshHub.chat.entity;

import VietFreshHub.auth.entity.User;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(schema = "dbo", name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected Conversation() {
    }

    public static Conversation open(User customer, Shop shop) {
        Conversation conversation = new Conversation();
        conversation.customer = customer;
        conversation.shop = shop;
        return conversation;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public User getCustomer() {
        return customer;
    }

    public Shop getShop() {
        return shop;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void reopen() {
        closedAt = null;
    }

    @jakarta.persistence.PrePersist
    void setUtcTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        if (createdAt == null) createdAt = now;
    }
}
