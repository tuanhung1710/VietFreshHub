package VietFreshHub.Order.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
/** Read-only mapping for TV3 tracking; fulfillment mutations belong to TV4. */
@Entity @Table(schema="dbo",name="deliveries") @Immutable
@Getter @Setter @NoArgsConstructor
public class OrderDeliveryView {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="delivery_id") private Long deliveryId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="shop_order_id",nullable=false,unique=true) private ShopOrder shopOrder;
    @Column(nullable=false,length=30) private String status;
    @Column(name="assigned_at") private LocalDateTime assignedAt;
    @Column(name="picked_up_at") private LocalDateTime pickedUpAt;
    @Column(name="delivered_at") private LocalDateTime deliveredAt;
    @Nationalized @Column(name="failure_reason",length=1000) private String failureReason;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
