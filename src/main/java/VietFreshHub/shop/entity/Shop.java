package VietFreshHub.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Read the existing shop availability fields; this does not introduce a new table. */
@Entity
@Table(name = "shops", schema = "dbo")
@Getter
@Setter
public class Shop {
    @Id
    @Column(name = "shop_id")
    private Long shopId;
    @Column(name = "status", length = 30)
    private String status;
    @Column(name = "availability_status", length = 30)
    private String availabilityStatus;
    @jakarta.persistence.Transient
    public String getShopCode() {
        return VietFreshHub.common.BusinessCodes.format("SHP", shopId);
    }
}
