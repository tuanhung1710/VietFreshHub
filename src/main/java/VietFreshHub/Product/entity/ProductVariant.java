package VietFreshHub.Product.entity;

import VietFreshHub.Product.enums.VariantStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="product_variants", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class ProductVariant {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="variant_id")
    private Long variantId;
    @Column(name="product_id")
    private Long productId;
    @Column(name="sku", length=100)
    private String sku;
    @Nationalized
    @Column(name="variant_name", length=200)
    private String variantName;
    @Column(name="price", precision=18, scale=2)
    private BigDecimal price;
    @Column(name="compare_at_price", precision=18, scale=2)
    private BigDecimal compareAtPrice;
    @Column(name="weight", precision=18, scale=3)
    private BigDecimal weight;
    @Nationalized
    @Column(name="unit", length=30)
    private String unit;
    @Column(name="low_stock_threshold")
    private Integer lowStockThreshold;
    @Column(name="status", length=30)
    @Enumerated(EnumType.STRING)
    private VariantStatus status;
    @Nationalized
    @Column(name="thumbnail_url", length=1000)
    private String thumbnailUrl;
    @Column(name="created_at")
    private LocalDateTime createdAt;
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
