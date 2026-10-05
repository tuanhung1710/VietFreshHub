package VietFreshHub.Product.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
@Getter @Setter
public class VariantRequest {
    @NotBlank(message="Nhập tên SKU.") @Size(max=200) private String variantName="Mặc định";
    @Size(max=100) @Pattern(regexp="[A-Za-z0-9_-]*", message="SKU gồm chữ Latin, số, - hoặc _.") private String sku;
    @NotBlank(message="Chọn ảnh SKU.") @Size(max=1000) private String thumbnailUrl;
    @NotNull(message="Nhập giá.") @DecimalMin(value="10000",message="Giá từ 10.000đ.") @Digits(integer=16,fraction=0,message="Giá là số nguyên VND.") private BigDecimal price;
    @DecimalMin("0") @Digits(integer=16,fraction=0) private BigDecimal compareAtPrice;
    @DecimalMin("0.001") @Digits(integer=15,fraction=3) private BigDecimal weight;
    @NotBlank private String unit="kg";
    @NotNull @Min(0) private Integer lowStockThreshold=5;
    // The checkbox is explicit; an empty quantity is never interpreted as zero.
    private boolean noInitialStock;
    @Min(0) private Integer initialQuantity;
    @Size(max=100) private String batchCode;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate receivedDate;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate expiryDate;
    @DecimalMin("0") @Digits(integer=16,fraction=2) private BigDecimal costPrice;
    @Size(max=900) private String reason;
}
