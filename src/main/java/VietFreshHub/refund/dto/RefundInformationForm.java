package VietFreshHub.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundInformationForm {

    @NotBlank(message = "Vui lòng nhập ngân hàng nhận tiền.")
    @Size(max = 120, message = "Tên ngân hàng không được quá 120 ký tự.")
    @Pattern(regexp = "(?=.*[\\p{L}\\p{N}])(?![\\s\\p{Z}]*[=+@-])[^\\p{Cc}\\p{Cf}]*",
            message = "Tên ngân hàng phải có chữ hoặc số, không chứa ký tự điều khiển hay bắt đầu bằng =, +, -, @.")
    private String bankName;

    @NotBlank(message = "Vui lòng nhập số tài khoản.")
    @Size(max = 50, message = "Số tài khoản không được quá 50 ký tự.")
    @Pattern(regexp = "[\\p{L}\\p{N}]+", message = "Số tài khoản chỉ được chứa chữ hoặc chữ số.")
    private String bankAccountNumber;

    @NotBlank(message = "Vui lòng nhập tên chủ tài khoản.")
    @Size(max = 150, message = "Tên chủ tài khoản không được quá 150 ký tự.")
    @Pattern(regexp = "(?=.*[\\p{L}\\p{N}])(?![\\s\\p{Z}]*[=+@-])[^\\p{Cc}\\p{Cf}]*",
            message = "Tên chủ tài khoản phải có chữ hoặc số, không chứa ký tự điều khiển hay bắt đầu bằng =, +, -, @.")
    private String accountHolderName;
}
