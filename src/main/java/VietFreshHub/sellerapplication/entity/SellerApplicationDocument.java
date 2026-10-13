package VietFreshHub.sellerapplication.entity;

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
        name = "seller_application_documents"
)
public class SellerApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "application_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "FK_seller_application_documents_application"
            )
    )
    private SellerApplication application;

    // Ví dụ: BUSINESS_LICENSE, ID_CARD
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Nationalized
    @Column(name = "file_url", nullable = false, length = 1000)
    private String cloudinaryPublicId;

    @Column(name = "cloudinary_resource_type", length = 20)
    private String cloudinaryResourceType;

    @Column(name = "cloudinary_format", length = 20)
    private String cloudinaryFormat;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private DocumentVerificationStatus verificationStatus =
            DocumentVerificationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}