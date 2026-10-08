package VietFreshHub.Shop.entity;

import VietFreshHub.Product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@Entity
@Table(schema = "dbo", name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "application_id")
    private Long applicationId;

    @Nationalized
    @Column(name = "shop_name", nullable = false, length = 200)
    private String shopName;

    @Nationalized
    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Nationalized
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Nationalized
    @Column(name = "phone", length = 30)
    private String phone;

    @Nationalized
    @Column(name = "email", length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "availability_status", nullable = false, length = 30)
    private String availabilityStatus = "OPEN";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();
}
