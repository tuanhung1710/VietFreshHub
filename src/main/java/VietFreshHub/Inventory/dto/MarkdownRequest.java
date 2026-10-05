package VietFreshHub.Inventory.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
@Getter @Setter
public class MarkdownRequest {
    @NotNull @DecimalMin("10000") @Digits(integer=16,fraction=0) private BigDecimal price;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate endDate;
    @NotBlank @Size(max=900) private String reason;
}
