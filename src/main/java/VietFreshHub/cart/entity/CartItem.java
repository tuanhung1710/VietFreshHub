package VietFreshHub.cart.entity;

import VietFreshHub.catalog.entity.CatalogVariant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "dbo", name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private CatalogVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void initializeUtcTimestamps() {
        var now = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
        if (addedAt == null) addedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateUtcTimestamps() {
        updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    }
}
