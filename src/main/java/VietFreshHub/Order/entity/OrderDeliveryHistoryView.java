package VietFreshHub.Order.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
@Entity @Table(schema="dbo",name="delivery_status_history") @Immutable
@Getter @Setter @NoArgsConstructor
public class OrderDeliveryHistoryView {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="history_id") private Long historyId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="delivery_id",nullable=false) private OrderDeliveryView delivery;
    @Column(nullable=false,length=30) private String status;
    @Column(name="changed_by") private Long changedBy;
    @Nationalized @Column(length=1000) private String note;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
}
