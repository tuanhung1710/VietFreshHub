package VietFreshHub.Product.entity;

import VietFreshHub.Product.enums.ProductStatus;
import VietFreshHub.Product.enums.ApprovalStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="products", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="product_id")
    private Long productId;
    @Column(name="shop_id")
    private Long shopId;
    @Nationalized
    @Column(name="name", length=250)
    private String name;
    @Column(name="slug", length=300)
    private String slug;
    @Nationalized
    @Column(name="description", length=20000)
    private String description;
    @Column(name="status", length=30)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;
    @Column(name="approval_status", length=30)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;
    @Column(name="approved_by")
    private Long approvedBy;
    @Column(name="approved_at")
    private LocalDateTime approvedAt;
    @Column(name="requires_preparation_check")
    private Boolean requiresPreparationCheck;
    @Nationalized
    @Column(name="preparation_note", length=1000)
    private String preparationNote;
    @Column(name="created_at")
    private LocalDateTime createdAt;
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
