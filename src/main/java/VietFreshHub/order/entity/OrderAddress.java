package VietFreshHub.order.entity;

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
    @Column(name="order_id", insertable=false, updatable=false) private Long orderId;
    @Nationalized @Column(name="recipient_name", nullable=false, length=150) private String recipientName;
    @Nationalized @Column(nullable=false, length=30) private String phone;
    @Nationalized @Column(nullable=false, length=100) private String province;
    @Nationalized @Column(length=100) private String district;
    @Nationalized @Column(nullable=false, length=100) private String ward;
    @Nationalized @Column(name="address_line", nullable=false, length=500) private String addressLine;

    @Column(name="province_id") private Integer provinceId;
    @Column(name="district_id") private Integer districtId;
    @Column(name="ward_id") private Integer wardId;
    @Nationalized @Column(name="formatted_address", length=1000) private String formattedAddress;
    @Column(precision=9, scale=6) private java.math.BigDecimal latitude;
    @Column(precision=10, scale=6) private java.math.BigDecimal longitude;
}
