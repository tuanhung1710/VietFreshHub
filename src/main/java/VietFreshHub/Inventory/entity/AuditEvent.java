package VietFreshHub.Inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="audit_events", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="audit_id")
    private Long auditId;
    @Column(name="actor_id")
    private Long actorId;
    @Column(name="action", length=100)
    private String action;
    @Column(name="entity_type", length=100)
    private String entityType;
    @Column(name="entity_id")
    private Long entityId;
    @Nationalized
    @Column(name="reason", length=1000)
    private String reason;
    @Column(name="created_at")
    private LocalDateTime createdAt;
}

