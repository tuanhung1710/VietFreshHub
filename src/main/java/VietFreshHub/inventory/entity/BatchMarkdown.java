package VietFreshHub.inventory.entity;

import VietFreshHub.inventory.enums.MarkdownStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_markdowns", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class BatchMarkdown {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "markdown_id")
    private Long markdownId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private MarkdownStatus status;

    @Nationalized
    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
