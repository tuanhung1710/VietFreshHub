package VietFreshHub.common;

import java.util.regex.Pattern;

/** Display legacy order references without changing checkout idempotency keys. */
public final class OrderCodes {
    private static final Pattern LEGACY_NUMBERED_CODE = Pattern.compile("(?<![A-Za-z0-9_-])ORD-[0-9]{6,}(?![A-Za-z0-9_-])");

    private OrderCodes() {}

    public static String display(String storedCode, Long orderId) {
        if (storedCode == null || orderId == null
                || !(storedCode.startsWith("VF-") || storedCode.startsWith("ORD-"))) {
            return storedCode;
        }
        return BusinessCodes.format("VFH", orderId);
    }

    public static String displayInText(String text) {
        if (text == null) return null;
        return LEGACY_NUMBERED_CODE.matcher(text).replaceAll(match -> {
            Long orderId = BusinessCodes.parse("ORD", match.group());
            return orderId == null ? match.group() : BusinessCodes.format("VFH", orderId);
        });
    }
}
