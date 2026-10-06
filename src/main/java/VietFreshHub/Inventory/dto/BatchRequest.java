package VietFreshHub.Inventory.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.format.annotation.DateTimeFormat;
@Getter @Setter
public class BatchRequest {
    @Size(max=100) @Pattern(regexp="[A-Za-z0-9_-]*",message="Mã lô gồm chữ Latin, số, - hoặc _.") private String batchCode;
    @NotNull(message="Nhập số lượng.") @Min(value=1,message="Số lượng nhập phải lớn hơn 0.") private Integer quantity;
    @NotNull(message="Chọn ngày nhập.") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate receivedDate;
    @NotNull(message="Chọn hạn dùng.") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate expiryDate;
    @DecimalMin("0") @Digits(integer=16,fraction=2) private BigDecimal costPrice;
    @NotBlank(message="Nhập lý do.") @Size(max=900) private String reason;
}
