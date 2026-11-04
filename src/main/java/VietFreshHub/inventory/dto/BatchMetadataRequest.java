package VietFreshHub.inventory.dto;

import jakarta.validation.constraints.DecimalMax;
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
public class BatchMetadataRequest {
    @NotNull(message = "Vui lòng chọn ngày nhập kho.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @NotNull(message = "Vui lòng chọn hạn sử dụng.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;

    @DecimalMin(value = "0", message = "Giá vốn không được âm.")
    @Digits(
            integer = 16,
            fraction = 2,
            message = "Giá vốn không được vượt quá 16 chữ số phần nguyên và 2 chữ số thập phân.")
    @DecimalMax(value = "9999999999999999.99", message = "Giá trị vượt giới hạn cho phép.")
    private BigDecimal costPrice;

    @NotBlank(message = "Vui lòng nhập lý do.")
    @Size(max = 900, message = "Lý do không được vượt quá 900 ký tự.")
    private String reason;
}
