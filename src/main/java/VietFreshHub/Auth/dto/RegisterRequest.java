package VietFreshHub.Auth.dto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(min = 2, max = 150, message = "Họ tên phải từ 2 đến 150 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    private String email;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(
            regexp = "^0[35789][0-9]{8}$",
            message = "Số điện thoại phải có 10 số và bắt đầu bằng 03, 05, 07, 08 hoặc 09"
    )
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 8, max = 100, message = "Mật khẩu phải có ít nhất 8 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu")
    private String confirmPassword;

    @AssertTrue(message = "Bạn cần đồng ý với điều khoản")
    private boolean terms;

    @AssertTrue(message = "Mật khẩu xác nhận không khớp")
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
