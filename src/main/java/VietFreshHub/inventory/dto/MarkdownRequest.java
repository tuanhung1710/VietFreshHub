package VietFreshHub.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MarkdownRequest {
    @NotNull(message = "Vui lòng nhập giá giảm.")
    @DecimalMin(value = "1", message = "Giá giảm phải từ 1 đ.")
    @Digits(integer = 16, fraction = 0, message = "Giá giảm phải là số nguyên đồng, tối đa 16 chữ số.")
    private BigDecimal price;

    @NotNull(message = "Vui lòng chọn ngày kết thúc giảm giá.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotBlank(message = "Vui lòng nhập lý do.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
