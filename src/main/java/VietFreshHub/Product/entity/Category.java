package VietFreshHub.Product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(schema = "dbo", name = "categories")
@Getter @Setter @NoArgsConstructor
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;
    @Column(name = "parent_category_id")
    private Long parentCategoryId;
    @Nationalized @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 200)
    private String slug;
    @Nationalized @Column(length = 1000)
    private String description;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
