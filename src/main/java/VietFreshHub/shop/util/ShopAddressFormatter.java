package VietFreshHub.shop.util;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ShopAddressFormatter {
    private ShopAddressFormatter() { }

    public static String format(String formattedAddress, String addressLine, String ward,
                                String district, String province) {
        if (formattedAddress != null && !formattedAddress.isBlank()) return formattedAddress.strip();
        String address = Stream.of(addressLine, ward, district, province)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip).collect(Collectors.joining(", "));
        return address.isEmpty() ? null : address;
    }
}
