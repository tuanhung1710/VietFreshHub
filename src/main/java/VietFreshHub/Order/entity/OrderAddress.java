package VietFreshHub.Order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity @Table(schema="dbo", name="order_addresses")
@Getter @Setter @NoArgsConstructor
public class OrderAddress {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="order_address_id") private Long orderAddressId;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_id", nullable=false, unique=true) private Order order;
    @Nationalized @Column(name="recipient_name", nullable=false, length=150) private String recipientName;
    @Nationalized @Column(nullable=false, length=30) private String phone;
    @Nationalized @Column(nullable=false, length=100) private String province;
    @Nationalized @Column(nullable=false, length=100) private String district;
    @Nationalized @Column(nullable=false, length=100) private String ward;
    @Nationalized @Column(name="address_line", nullable=false, length=500) private String addressLine;
}
