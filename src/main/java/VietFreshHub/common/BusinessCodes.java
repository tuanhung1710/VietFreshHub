package VietFreshHub.common;

import java.util.Locale;

/** Stable business references backed by existing identity keys; never truncate large IDs. */
public final class BusinessCodes {
    private BusinessCodes() {}

    public static String format(String prefix, Long id) {
        if (id == null) return null;
        if (id <= 0) throw new IllegalArgumentException("Business reference requires a persisted ID");
        int width = switch (prefix) {
            case "SHP", "PRM" -> 4;
            case "PRD", "SKU", "LOT", "ORD", "SOR", "VFH", "PAY", "DLV", "INV" -> 6;
            default -> throw new IllegalArgumentException("Unknown business code prefix: " + prefix);
        };
        return prefix + "-" + String.format(Locale.ROOT, "%0" + width + "d", id);
    }

    /** Exact, case-insensitive lookup, including IDs beyond the minimum display width. */
    public static Long parse(String prefix, String value) {
        if (value == null) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!code.startsWith(prefix + "-")) return null;
        try {
            long id = Long.parseLong(code.substring(prefix.length() + 1));
            return id > 0 && code.equals(format(prefix, id)) ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
