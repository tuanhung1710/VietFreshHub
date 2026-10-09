package VietFreshHub.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Nationalized
    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Nationalized
    @Column(nullable = false, length = 30)
    private String phone;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String province;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String district;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String ward;

    @Nationalized
    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
