package VietFreshHub.Auth.entity;


import VietFreshHub.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        schema = "dbo",
        name = "seller_application_status_history",
        indexes = {
                @Index(
                        name = "IX_seller_application_status_history_application_id",
                        columnList = "application_id"
                )
        }
)
public class SellerApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "application_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "FK_seller_application_status_history_application"
            )
    )
    private SellerApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private SellerApplicationStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private SellerApplicationStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "changed_by",
            foreignKey = @ForeignKey(
                    name = "FK_seller_application_status_history_user"
            )
    )
    private User changedBy;

    @Nationalized
    @Column(name = "note", length = 1000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
