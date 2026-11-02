package VietFreshHub.checkout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutAddressRequest {
    @NotBlank(message = "Vui lòng nhập tên người nhận.")
    @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự.")
    private String recipientName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự.")
    @Pattern(regexp = "\\+?[0-9][0-9 .()-]*", message = "Số điện thoại chỉ gồm số và ký tự liên lạc hợp lệ.")
    private String phone;

    @NotBlank(message = "Vui lòng chọn địa chỉ từ danh sách gợi ý.")
    private String refId;

    private String searchText;

    @NotBlank(message = "Phiên đặt hàng không hợp lệ. Vui lòng tải lại trang.")
    private String checkoutToken;
}
