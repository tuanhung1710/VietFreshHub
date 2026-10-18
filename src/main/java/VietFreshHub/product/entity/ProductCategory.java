package VietFreshHub.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "product_categories", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class ProductCategory {

    @EmbeddedId
    private Key id;

    public ProductCategory(Long productId, Long categoryId) {
        id = new Key(productId, categoryId);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "product_id")
        private Long productId;

        @Column(name = "category_id")
        private Long categoryId;
    }
}
