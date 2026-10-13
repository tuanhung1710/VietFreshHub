package VietFreshHub.Auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

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
    @Column(length = 100)
    private String district;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String ward;

    @Nationalized
    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void initializeUtcTimestamps() {
        var now = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateUtcTimestamps() {
        updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    }

    @Column(name="province_id") private Integer provinceId;
    @Column(name="district_id") private Integer districtId;
    @Column(name="ward_id") private Integer wardId;
    @Nationalized @Column(name="formatted_address", length=1000) private String formattedAddress;
    @Column(precision=9, scale=6) private java.math.BigDecimal latitude;
    @Column(precision=10, scale=6) private java.math.BigDecimal longitude;
}
