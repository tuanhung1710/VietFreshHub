package VietFreshHub.sellerapplication.entity;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.*;
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
@Entity
@Table(
        schema = "dbo",
        name = "seller_applications",
        indexes = {
                @Index(name = "IX_seller_applications_user_id", columnList = "user_id"),
                @Index(name = "IX_seller_applications_status", columnList = "status")
        }
)
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    // user_id: người gửi đơn đăng ký bán hàng
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_seller_applications_user")
    )
    private User user;

    @Nationalized
    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Nationalized
    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SellerApplicationStatus status = SellerApplicationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reviewed_by",
            foreignKey = @ForeignKey(name = "FK_seller_applications_reviewer")
    )
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Nationalized
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "application",
            fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true
    )
    private List<SellerApplicationDocument> documents = new ArrayList<>();

    @OneToMany(
            mappedBy = "application",
            fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST
    )
    private List<SellerApplicationStatusHistory> statusHistories = new ArrayList<>();
}