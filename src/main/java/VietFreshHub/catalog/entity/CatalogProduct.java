package VietFreshHub.catalog.entity;

import VietFreshHub.common.VietnamDatabaseTime;
import VietFreshHub.shop.entity.Shop;

import jakarta.persistence.*;
import jakarta.persistence.Convert;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "dbo", name = "products")
public class CatalogProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Nationalized
    @Column(name = "name", nullable = false, length = 250)
    private String name;

    @Column(name = "slug", nullable = false, length = 300)
    private String slug;

    @Nationalized
    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Nationalized
    @Column(name = "origin", length = 200)
    private String origin;

    @Transient private String imageUrl;

    @OneToMany(mappedBy = "product")
    @org.hibernate.annotations.BatchSize(size = 16)
    private List<CatalogProductImage> images = new ArrayList<>();

    @Column(name = "requires_preparation_check", nullable = false)
    private boolean requiresPreparationCheck;

    @Nationalized
    @Column(name = "preparation_note", length = 1000)
    private String preparationNote;

    public String getImageUrl() {
        if (this.imageUrl != null && !this.imageUrl.isBlank()) {
            return this.imageUrl;
        }
        var primary =
                images.stream()
                        .filter(i -> i.getVariant() == null)
                        .filter(i -> i.getImageUrl() != null && !i.getImageUrl().isBlank())
                        .sorted(
                                java.util.Comparator.comparing(CatalogProductImage::isPrimary)
                                        .reversed()
                                        .thenComparing(CatalogProductImage::getSortOrder)
                                        .thenComparing(CatalogProductImage::getImageId))
                        .map(CatalogProductImage::getImageUrl)
                        .findFirst();
        if (primary.isPresent()) return primary.get();
        var thumbnail =
                variants.stream()
                        .map(CatalogVariant::getThumbnailUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .findFirst();
        if (thumbnail.isPresent()) return thumbnail.get();
        return "/images/product/product-placeholder.svg";
    }

    @Column(nullable = false, length = 30)
    private String status = "INACTIVE";

    @Column(name = "moderation_blocked", nullable = false)
    private boolean moderationBlocked;

    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "PENDING";

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updatedAt;

    // Each variant is refreshed under its own lock during checkout.
    @OneToMany(
            mappedBy = "product",
            cascade = {
                CascadeType.PERSIST,
                CascadeType.MERGE,
                CascadeType.REMOVE,
                CascadeType.DETACH
            },
            orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 16)
    private List<CatalogVariant> variants = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            schema = "dbo",
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private java.util.Set<CatalogCategory> categories = new java.util.LinkedHashSet<>();

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
