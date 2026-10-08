package VietFreshHub.Checkout.dto;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CheckoutRequest {
    @NotNull(message="Vui lòng chọn địa chỉ nhận hàng.")
    @Positive(message="Địa chỉ nhận hàng không hợp lệ.")
    private Long addressId;
    @NotBlank(message="Vui lòng chọn phương thức thanh toán.")
    @Pattern(regexp="COD",message="Hiện chỉ hỗ trợ thanh toán khi nhận hàng.")
    private String paymentMethodCode="COD";
    @NotBlank(message="Phiên đặt hàng không hợp lệ. Vui lòng tải lại trang.")
    @Pattern(regexp="[0-9a-f]{32}",message="Phiên đặt hàng không hợp lệ. Vui lòng tải lại trang.")
    private String checkoutToken;
}
