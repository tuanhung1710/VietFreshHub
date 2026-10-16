package VietFreshHub.product.entity;

import VietFreshHub.product.enums.VariantStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_variants", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Nationalized
    @Column(name = "variant_name", length = 200)
    private String variantName;

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 18, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "weight", precision = 18, scale = 3)
    private BigDecimal weight;

    @Nationalized
    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    private VariantStatus status;

    @Nationalized
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
