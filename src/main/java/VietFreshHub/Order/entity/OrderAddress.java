package VietFreshHub.Order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "order_addresses")
public class OrderAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_address_id")
    private Long orderAddressId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "province", nullable = false)
    private String province;

    @Column(name = "district")
    private String district;

    @Column(name = "ward", nullable = false)
    private String ward;

    @Column(name = "address_line")
    private String addressLine;

    @Column(name = "province_id")
    private Integer provinceId;

    @Column(name = "district_id")
    private Integer districtId;

    @Column(name = "ward_id")
    private Integer wardId;

    @Nationalized
    @Column(name = "formatted_address", length = 1000)
    private String formattedAddress;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;
}
