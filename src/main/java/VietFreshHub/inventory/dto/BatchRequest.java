package VietFreshHub.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
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
public class BatchRequest {
    @NotNull(message = "Vui lòng nhập số lượng.")
    @Min(value = 1, message = "Số lượng nhập phải lớn hơn 0.")
    private Integer quantity;

    @NotNull(message = "Vui lòng chọn ngày nhập kho.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;

    @Min(value = 0, message = "Số ngày bảo quản phải lớn hơn hoặc bằng 0.")
    private Integer shelfLifeDays;

    @DecimalMin(value = "0", message = "Giá vốn không được âm.")
    @Digits(integer = 16, fraction = 2, message = "Giá vốn không được vượt quá 16 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal costPrice;

    @NotBlank(message = "Vui lòng nhập lý do.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
