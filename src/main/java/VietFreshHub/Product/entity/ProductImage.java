package VietFreshHub.Product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(schema="dbo", name="product_images")
@Getter @Setter @NoArgsConstructor
public class ProductImage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="image_id") private Long imageId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id", nullable=false) private Product product;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="variant_id") private ProductVariant variant;
    @Nationalized @Column(name="image_url", nullable=false, length=1000) private String imageUrl;
    @Column(name="is_primary", nullable=false) private boolean primary;
    @Column(name="sort_order", nullable=false) private Integer sortOrder=0;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @PrePersist void timestamp() { if(createdAt==null) createdAt=LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
}
