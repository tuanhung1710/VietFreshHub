package VietFreshHub.Delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class DeliveryLocationResponse {

    private Integer provinceId;
    private String province;
    private Integer districtId;
    private String district;
    private Integer wardId;
    private String ward;
    private String addressLine;
    private String formattedAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
