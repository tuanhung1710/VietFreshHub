package VietFreshHub.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "shop_locations")
public class ShopLocation {

    @Id
    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "province_id")
    private Integer provinceId;

    @Nationalized
    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "district_id")
    private Integer districtId;

    @Nationalized
    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "ward_id")
    private Integer wardId;

    @Nationalized
    @Column(name = "ward", nullable = false, length = 100)
    private String ward;

    @Nationalized
    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Nationalized
    @Column(name = "formatted_address", length = 1000)
    private String formattedAddress;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(0)")
    private LocalDateTime updatedAt;

    public ShopLocation(Long shopId) {
        this.shopId = shopId;
    }

    public void updateLocation(Integer provinceId, String province, Integer districtId, String district,
                               Integer wardId, String ward, String addressLine, String formattedAddress,
                               BigDecimal latitude, BigDecimal longitude) {
        this.provinceId = provinceId;
        this.province = province;
        this.districtId = districtId;
        this.district = district;
        this.wardId = wardId;
        this.ward = ward;
        this.addressLine = addressLine;
        this.formattedAddress = formattedAddress;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
