package VietFreshHub.Product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="price_history", schema="dbo")
@Getter @Setter @NoArgsConstructor
public class PriceHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="price_history_id")
    private Long priceHistoryId;
    @Column(name="variant_id")
    private Long variantId;
    @Column(name="price", precision=18, scale=2)
    private BigDecimal price;
    @Column(name="effective_from")
    private LocalDateTime effectiveFrom;
    @Column(name="effective_to")
    private LocalDateTime effectiveTo;
    @Column(name="changed_by")
    private Long changedBy;
}

