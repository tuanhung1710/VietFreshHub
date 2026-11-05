package VietFreshHub.shop.entity;

import VietFreshHub.common.VietnamDatabaseTime;
import jakarta.persistence.Convert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(0)")
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updatedAt;

    public ShopLocation(Long shopId) {
        this.shopId = shopId;
    }

    public String getDisplayAddress() {
        return VietFreshHub.shop.util.ShopAddressFormatter.format(formattedAddress, addressLine, ward, district, province);
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

    @jakarta.persistence.PrePersist
    void setUtcTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void updateUtcTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
