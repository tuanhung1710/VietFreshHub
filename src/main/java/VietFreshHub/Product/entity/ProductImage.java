package VietFreshHub.Product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="product_images", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class ProductImage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="image_id")
    private Long imageId;
    @Column(name="product_id")
    private Long productId;
    @Column(name="variant_id")
    private Long variantId;
    @Nationalized
    @Column(name="image_url", length=1000)
    private String imageUrl;
    @Column(name="is_primary")
    private Boolean primary;
    @Column(name="sort_order")
    private Integer sortOrder;
    @Column(name="created_at")
    private LocalDateTime createdAt;
}

