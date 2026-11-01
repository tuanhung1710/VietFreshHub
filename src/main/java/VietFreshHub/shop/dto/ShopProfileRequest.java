package VietFreshHub.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopProfileRequest {
    @NotBlank(message = "Vui lòng nhập tên cửa hàng.")
    @Size(max = 200, message = "Tên cửa hàng tối đa 200 ký tự.")
    private String shopName;
    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự.")
    private String description;
    @Pattern(regexp = "^$|[+0-9 ()-]{8,30}", message = "Số điện thoại không hợp lệ.")
    private String phone;
    @Email(message = "Email không hợp lệ.")
    @Size(max = 255)
    private String email;
    @Size(max = 1000)
    @Pattern(regexp = "^$|https://[^\\s]+", message = "Ảnh phải là đường dẫn HTTPS.")
    private String logoUrl;
    @Size(max = 1000)
    @Pattern(regexp = "^$|https://[^\\s]+", message = "Ảnh phải là đường dẫn HTTPS.")
    private String bannerUrl;
}
