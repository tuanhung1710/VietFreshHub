package VietFreshHub.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class VariantRequest {
    @NotBlank(message = "Nhập tên phân loại.")
    @Size(max = 200, message = "Tên phân loại không được vượt quá 200 ký tự.")
    private String variantName = "";

    @Size(max = 100, message = "SKU không được vượt quá 100 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9_-]*", message = "SKU chỉ được chứa chữ cái không dấu, chữ số, dấu gạch nối (-) và dấu gạch dưới (_).")
    private String sku;

    @NotBlank(message = "Chọn ảnh phân loại hoặc dán liên kết ảnh.")
    @Size(max = 1000, message = "Liên kết ảnh phân loại không được vượt quá 1.000 ký tự.")
    private String thumbnailUrl;

    @NotNull(message = "Nhập giá bán.")
    @DecimalMin(value = "10000", message = "Giá bán phải từ 10.000 đ.")
    @Digits(integer = 16, fraction = 0, message = "Giá bán phải là số nguyên đồng, tối đa 16 chữ số.")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "Giá trước giảm không được âm.")
    @Digits(integer = 16, fraction = 0, message = "Giá trước giảm phải là số nguyên đồng, tối đa 16 chữ số.")
    private BigDecimal compareAtPrice;

    @DecimalMin(value = "0.001", message = "Giá trị phải từ 0,001 kg.")
    @Digits(integer = 15, fraction = 3, message = "Giá trị tính bằng kg, tối đa 15 chữ số phần nguyên và 3 chữ số thập phân.")
    private BigDecimal weight;

    @NotBlank(message = "Chọn đơn vị bán.")
    private String unit = "kg";

    @NotNull(message = "Nhập mức tồn kho cảnh báo.")
    @Min(value = 0, message = "Mức tồn kho cảnh báo phải lớn hơn hoặc bằng 0.")
    private Integer lowStockThreshold = 5;

    // The checkbox is explicit; an empty quantity is never interpreted as zero.
    private boolean noInitialStock;

    @Min(value = 0, message = "Số lượng nhập ban đầu phải lớn hơn hoặc bằng 0.")
    private Integer initialQuantity;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @Min(value = 0, message = "Số ngày bảo quản phải lớn hơn hoặc bằng 0.")
    private Integer shelfLifeDays;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;

    @DecimalMin(value = "0", message = "Giá vốn không được âm.")
    @Digits(integer = 16, fraction = 2, message = "Giá vốn không được vượt quá 16 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal costPrice;

    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;

    // SQL DECIMAL supplies .00 on edit; accept whole dong without rounding actual fractions.
    public void setPrice(BigDecimal value) {
        price = normalizedAmount(value);
    }

    public void setCompareAtPrice(BigDecimal value) {
        compareAtPrice = normalizedAmount(value);
    }

    private BigDecimal normalizedAmount(BigDecimal value) {
        if (value == null) return null;
        BigDecimal amount = value.stripTrailingZeros();
        // Keep valid whole amounts readable in number inputs; invalid fractions/oversized
        // exponents still reach validation unchanged, without rounding or huge allocations.
        return amount.scale() < 0 && (long) amount.precision() - amount.scale() <= 16
                ? amount.setScale(0) : amount;
    }
}
