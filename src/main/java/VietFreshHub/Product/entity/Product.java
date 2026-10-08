package VietFreshHub.Product.entity;

import VietFreshHub.Shop.entity.Shop;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "dbo", name = "products")
public class Product {

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

    @Transient
    private String imageUrl;

    public String getImageUrl() {
        if (this.imageUrl != null && !this.imageUrl.isBlank()) {
            return this.imageUrl;
        }
        if (slug != null) {
            String s = slug.toLowerCase();
            if (s.contains("buoi")) return "/images/buoi-da-xanh.jpg";
            if (s.contains("sau-rieng")) return "/images/sau-rieng.jpg";
            if (s.contains("xoai")) return "/images/xoai-cat.png";
            if (s.contains("vai")) return "/images/vai-thieu.png";
        }
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.contains("bưởi")) return "/images/buoi-da-xanh.jpg";
            if (lower.contains("sầu")) return "/images/sau-rieng.jpg";
            if (lower.contains("xoài")) return "/images/xoai-cat.png";
            if (lower.contains("vải")) return "/images/vai-thieu.png";
        }
        return "/images/buoi-da-xanh.jpg";
    }

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "PENDING";

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 16)
    private List<ProductVariant> variants = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(schema = "dbo", name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private java.util.Set<Category> categories = new java.util.LinkedHashSet<>();
}
