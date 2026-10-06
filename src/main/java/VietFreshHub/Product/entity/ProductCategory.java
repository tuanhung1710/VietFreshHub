package VietFreshHub.Product.entity;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
@Entity @Table(name="product_categories", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class ProductCategory {
    @EmbeddedId private Key id;
    @Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name="product_id") private Long productId;
        @Column(name="category_id") private Long categoryId;
    }
    public ProductCategory(Long productId, Long categoryId) { id = new Key(productId, categoryId); }
}

