package VietFreshHub.Order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "order_addresses")
public class OrderAddress {

    @Id
    @Column(name = "order_address_id")
    private Long orderAddressId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "province")
    private String province;

    @Column(name = "district")
    private String district;

    @Column(name = "ward")
    private String ward;

    @Column(name = "address_line")
    private String addressLine;
}
