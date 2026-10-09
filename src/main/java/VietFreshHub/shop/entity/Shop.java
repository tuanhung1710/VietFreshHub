package VietFreshHub.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(schema = "dbo", name = "shops")
public class Shop {

    @Id
    @Column(name = "shop_id")
    private Long shopId;

    @Nationalized
    @Column(name = "shop_name", nullable = false, length = 200)
    private String shopName;

    @Column(nullable = false, length = 30)
    private String status;

    protected Shop() {
    }

    public Long getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public String getStatus() {
        return status;
    }
}
