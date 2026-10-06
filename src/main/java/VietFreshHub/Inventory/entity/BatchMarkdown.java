package VietFreshHub.Inventory.entity;

import jakarta.persistence.*;
import VietFreshHub.Inventory.enums.MarkdownStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="batch_markdowns", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class BatchMarkdown {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="markdown_id")
    private Long markdownId;
    @Column(name="batch_id")
    private Long batchId;
    @Column(name="price", precision=18, scale=2)
    private BigDecimal price;
    @Column(name="start_date")
    private LocalDate startDate;
    @Column(name="end_date")
    private LocalDate endDate;
    @Column(name="status", length=20)
    @Enumerated(EnumType.STRING)
    private MarkdownStatus status;
    @Nationalized
    @Column(name="note", length=1000)
    private String note;
    @Column(name="created_by")
    private Long createdBy;
    @Column(name="created_at")
    private LocalDateTime createdAt;
}
