package VietFreshHub.catalog.entity;

import VietFreshHub.common.VietnamDatabaseTime;
import jakarta.persistence.Convert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

@Entity
@Table(schema = "dbo", name = "categories")
@Getter @Setter @NoArgsConstructor
public class CatalogCategory {
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
    @Nationalized @Column(name = "image_url", length = 1000)
    private String imageUrl;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
     @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;
     @Column(name = "updated_at", nullable = false)
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
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
}
