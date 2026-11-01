package VietFreshHub.payment.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter @Setter
public class PaymentHistoryFilter {
    @Min(1) @Max(10000)
    private int page = 1;
    @NotNull @Size(max=100, message="Mã đơn tối đa 100 ký tự.")
    private String keyword = "";
    @Pattern(regexp="|PENDING|PROCESSING|PAID|FAILED|CANCELLED|REFUNDED|PARTIALLY_REFUNDED", message="Trạng thái thanh toán không hợp lệ.")
    private String status = "";
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    @AssertTrue(message="Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !toDate.isBefore(fromDate);
    }
    @AssertTrue(message="Ngày lọc phải nằm trong khoảng năm 0001 đến 9999.")
    public boolean isDatabaseDateRangeValid() { return validDate(fromDate) && validDate(toDate); }
    private boolean validDate(LocalDate date) { return date == null || date.getYear() >= 1 && date.getYear() <= 9999; }
    public boolean hasFilters() {
        return keyword != null && !keyword.isBlank() || status != null && !status.isBlank() || fromDate != null || toDate != null;
    }
}
