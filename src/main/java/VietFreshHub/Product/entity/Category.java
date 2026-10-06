package VietFreshHub.Product.entity;

import VietFreshHub.Product.enums.CategoryStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="categories", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class Category {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="category_id")
    private Long categoryId;
    @Column(name="parent_category_id")
    private Long parentCategoryId;
    @Nationalized
    @Column(name="name", length=150)
    private String name;
    @Column(name="slug", length=200)
    private String slug;
    @Column(name="status", length=30)
    @Enumerated(EnumType.STRING)
    private CategoryStatus status;
}
