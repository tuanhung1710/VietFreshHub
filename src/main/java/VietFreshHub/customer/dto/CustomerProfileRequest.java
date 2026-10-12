package VietFreshHub.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CustomerProfileRequest {

    private String email;

    @NotBlank(message = "Vui lòng nhập họ và tên.")
    @Size(max = 150, message = "Họ và tên tối đa 150 ký tự.")
    private String fullName;

    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự.")
    private String phone;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
