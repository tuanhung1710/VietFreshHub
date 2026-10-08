package VietFreshHub.Order.dto;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
@Getter @Setter
public class OrderFilterRequest {
    @Min(value=1,message="Trang phải từ 1 trở lên.") @Max(value=10000,message="Trang vượt giới hạn cho phép.") private int page=1;
    @Size(max=100,message="Mã đơn tìm kiếm tối đa 100 ký tự.") private String keyword="";
    @Pattern(regexp="|PENDING|CONFIRMED|PROCESSING|PARTIALLY_COMPLETED|COMPLETED|CANCELLED",message="Trạng thái lọc không hợp lệ.") private String status="";
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate fromDate;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate toDate;
    @AssertTrue(message="Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.")
    public boolean isDateRangeValid() { return fromDate==null || toDate==null || !toDate.isBefore(fromDate); }
}
