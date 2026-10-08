package VietFreshHub.Checkout.dto;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CheckoutAddressRequest {
    @NotBlank(message="Vui lòng nhập tên người nhận.") @Size(max=150,message="Tên người nhận tối đa 150 ký tự.")
    private String recipientName;
    @NotBlank(message="Vui lòng nhập số điện thoại.") @Size(max=30,message="Số điện thoại tối đa 30 ký tự.")
    @Pattern(regexp="\\+?[0-9][0-9 .()-]*",message="Số điện thoại chỉ gồm số và ký tự liên lạc hợp lệ.")
    private String phone;
    @NotBlank(message="Vui lòng nhập tỉnh hoặc thành phố.") @Size(max=100,message="Tỉnh/thành phố tối đa 100 ký tự.")
    private String province;
    @NotBlank(message="Vui lòng nhập quận/huyện.") @Size(max=100,message="Quận/huyện tối đa 100 ký tự.")
    private String district;
    @NotBlank(message="Vui lòng nhập phường/xã.") @Size(max=100,message="Phường/xã tối đa 100 ký tự.")
    private String ward;
    @NotBlank(message="Vui lòng nhập số nhà và tên đường.") @Size(max=500,message="Địa chỉ chi tiết tối đa 500 ký tự.")
    private String addressLine;
    @NotBlank(message="Phiên đặt hàng không hợp lệ. Vui lòng tải lại trang.")
    private String checkoutToken;
}
