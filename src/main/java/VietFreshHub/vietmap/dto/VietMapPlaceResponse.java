package VietFreshHub.vietmap.dto;

import java.math.BigDecimal;

public record VietMapPlaceResponse(
        Integer provinceId,
        String province,
        Integer districtId,
        String district,
        Integer wardId,
        String ward,
        String addressLine,
        String formattedAddress,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
