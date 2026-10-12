package VietFreshHub.promotion.dto;

import VietFreshHub.promotion.entity.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PromotionForm {

    @NotBlank(message = "Tên chương trình không được để trống")
    @Size(max = 200, message = "Tên không được vượt quá 200 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @NotNull(message = "Vui lòng chọn loại giảm giá")
    private DiscountType discountType;

    @NotNull(message = "Vui lòng nhập giá trị giảm")
    @DecimalMin(
            value = "0",
            inclusive = false,
            message = "Giá trị giảm phải lớn hơn 0"
    )
    @Digits(integer = 16, fraction = 2,
            message = "Tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal discountValue;

    @DecimalMin(value = "0",
            message = "Mức giảm tối đa không được âm")
    @Digits(integer = 16, fraction = 2,
            message = "Tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal maxDiscount;

    @NotNull(message = "Vui lòng nhập giá trị đơn tối thiểu")
    @DecimalMin(value = "0",
            message = "Giá trị đơn tối thiểu không được âm")
    @Digits(integer = 16, fraction = 2,
            message = "Tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    @AssertTrue(message = "Giảm theo phần trăm không được vượt quá 100%")
    public boolean isDiscountPercentValid() {
        if (discountType == null || discountValue == null) {
            return true;
        }

        return discountType != DiscountType.PERCENT
                || discountValue.compareTo(new BigDecimal("100")) <= 0;
    }

    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu")
    public boolean isPeriodValid() {
        if (startAt == null || endAt == null) {
            return true;
        }

        return endAt.isAfter(startAt);
    }
}