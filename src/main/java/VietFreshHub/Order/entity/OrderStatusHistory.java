package VietFreshHub.Order.entity;

import VietFreshHub.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(schema="dbo", name="order_status_history")
@Getter @Setter @NoArgsConstructor
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="history_id") private Long historyId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="shop_order_id", nullable=false) private ShopOrder shopOrder;
    @Column(nullable=false, length=30) private String status;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="changed_by") private User changedBy;
    @Nationalized @Column(length=1000) private String note;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @PrePersist void timestamp() { if(createdAt==null) createdAt=LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
}
