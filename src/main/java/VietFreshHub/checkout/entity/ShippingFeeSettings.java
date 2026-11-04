package VietFreshHub.checkout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipping_fee_settings", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class ShippingFeeSettings {

    @Id
    @Column(name = "settings_id", nullable = false)
    private Long settingsId;

    @Column(name = "base_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "included_distance_km", nullable = false, precision = 11, scale = 3)
    private BigDecimal includedDistanceKm;

    @Column(name = "additional_fee_per_km", nullable = false, precision = 18, scale = 2)
    private BigDecimal additionalFeePerKm;

    @Column(name = "max_delivery_distance_km", nullable = false, precision = 11, scale = 3)
    private BigDecimal maxDeliveryDistanceKm;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
