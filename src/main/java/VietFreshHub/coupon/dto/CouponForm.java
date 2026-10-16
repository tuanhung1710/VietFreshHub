package VietFreshHub.coupon.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Setter
public class CouponForm {

    @NotNull(message = "Vui lòng chọn chương trình khuyến mãi")
    @Positive(message = "ID chương trình không hợp lệ")
    private Long promotionId;

    @NotBlank(message = "Mã coupon không được để trống")
    @Size(max = 100, message = "Mã coupon không được vượt quá 100 ký tự")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Mã chỉ gồm chữ không dấu, số, dấu gạch ngang hoặc gạch dưới")
    private String code;

    @Positive(message = "Tổng lượt sử dụng phải lớn hơn 0")
    private Integer usageLimit;

    @Positive(message = "Số lượt mỗi khách phải lớn hơn 0")
    private Integer perUserLimit;

    @NotNull(message = "Vui lòng nhập giá trị đơn tối thiểu")
    @DecimalMin(value = "0", message = "Giá trị đơn tối thiểu không được âm")
    @Digits(integer = 16, fraction = 2, message = "Tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu")
    public boolean isPeriodValid() {
        if (startAt == null || endAt == null) {
            return true;
        }

        return endAt.isAfter(startAt);
    }
}