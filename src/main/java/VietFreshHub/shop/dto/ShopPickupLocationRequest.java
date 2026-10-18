package VietFreshHub.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopPickupLocationRequest {

    @NotBlank(message = "Vui lòng chọn một địa chỉ từ danh sách gợi ý.")
    private String refId;

    private String searchText;
}
