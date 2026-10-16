package VietFreshHub.product.entity;

import VietFreshHub.product.enums.CategoryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "categories", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "parent_category_id")
    private Long parentCategoryId;

    @Nationalized
    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "slug", length = 200)
    private String slug;

    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    private CategoryStatus status;
}
