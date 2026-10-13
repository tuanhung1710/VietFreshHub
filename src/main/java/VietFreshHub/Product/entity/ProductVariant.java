package VietFreshHub.Product.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "dbo", name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Nationalized
    @Column(name = "variant_name", nullable = false, length = 200)
    private String variantName;

    @Nationalized @Column(name = "thumbnail_url", nullable = false, length = 1000)
    private String thumbnailUrl;

    @Nationalized @Column(nullable = false, length = 30)
    private String unit;

    @Column(name = "compare_at_price", precision = 18, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(precision = 18, scale = 3)
    private BigDecimal weight;

    public String getDisplayImageUrl() {
        return thumbnailUrl != null && !thumbnailUrl.isBlank() ? thumbnailUrl : product.getImageUrl();
    }

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 0;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, OUT_OF_STOCK

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void initializeUtcTimestamps() {
        var now = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateUtcTimestamps() {
        updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    }
}
