package VietFreshHub.inventory.rule;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.product.exception.CatalogException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Shared stock checks. Shop dates are Vietnamese local dates; stored timestamps are UTC. */
public final class InventoryRules {

    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String EXPIRED_DISPOSAL_REASON = "Hủy toàn bộ hàng hết hạn chưa giữ cho đơn hàng.";
    private static final BigDecimal MINIMUM_PRICE = new BigDecimal("10000");

    private InventoryRules() {
    }

    public static LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    public static LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(SHOP_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDate shopDate(LocalDateTime utcTime) {
        return utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(SHOP_ZONE).toLocalDate();
    }

    public static boolean expired(InventoryBatch batch, LocalDate date) {
        return batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(date);
    }

    public static boolean nearExpiry(InventoryBatch batch, LocalDate date) {
        return nearExpiry(batch, date, 3);
    }

    public static boolean nearExpiry(InventoryBatch batch, LocalDate date, Integer warningDays) {
        Long remainingDays = daysUntilExpiry(batch, date);
        int threshold = warningDays == null ? 3 : warningDays;
        return remainingDays != null && remainingDays >= 0 && remainingDays <= threshold;
    }

    public static Long daysUntilExpiry(InventoryBatch batch, LocalDate date) {
        return batch.getExpiryDate() == null
                ? null
                : ChronoUnit.DAYS.between(date, batch.getExpiryDate());
    }

    // Discount eligibility retains its existing three-day window independently of seller reminders.
    public static boolean markdownEligible(InventoryBatch batch, LocalDate date) {
        return available(batch, date) > 0 && nearExpiry(batch, date, 3);
    }

    public static int available(InventoryBatch batch, LocalDate date) {
        if (batch.getStatus() != BatchStatus.ACTIVE || expired(batch, date)) {
            return 0;
        }
        return Math.max(0, batch.getQuantityOnHand() - batch.getReservedQuantity());
    }

    public static boolean isApplicableMarkdown(
            InventoryBatch batch, BatchMarkdown markdown, BigDecimal basePrice, LocalDate date) {
        return batch.getBatchId().equals(markdown.getBatchId())
                && !markdown.getStartDate().isAfter(date)
                && !markdown.getEndDate().isBefore(date)
                && markdownEligible(batch, date)
                && markdown.getPrice().compareTo(basePrice) < 0;
    }

    public static void price(BigDecimal value) {
        require(
                isWholeVndPrice(value) && value.compareTo(MINIMUM_PRICE) >= 0,
                "Giá bán phải là số nguyên đồng và từ 10.000 đ.");
    }

    private static boolean isWholeVndPrice(BigDecimal value) {
        return value != null
                && value.stripTrailingZeros().scale() <= 0
                && value.precision() - value.scale() <= 16;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new CatalogException(message);
        }
    }

    public static String reason(String value) {
        require(
                value != null && !value.isBlank() && value.trim().length() <= 900,
                "Vui lòng nhập lý do, tối đa 900 ký tự.");
        return value.trim();
    }

    public static LocalDate resolveExpiryDate(LocalDate receivedDate, Integer shelfLifeDays, LocalDate expiryDate) {
        if (shelfLifeDays == null) return expiryDate;
        require(receivedDate != null, "Chọn ngày nhập kho để tính hạn sử dụng.");
        require(shelfLifeDays >= 0, "Số ngày bảo quản phải lớn hơn hoặc bằng 0.");
        try {
            LocalDate calculated = receivedDate.plusDays(shelfLifeDays);
            require(calculated.getYear() >= 1 && calculated.getYear() <= 9999,
                    "Hạn sử dụng vượt giới hạn cho phép.");
            return calculated;
        } catch (DateTimeException exception) {
            throw new CatalogException("Hạn sử dụng vượt giới hạn cho phép.");
        }
    }

    public static void dates(LocalDate receivedDate, LocalDate expiryDate, LocalDate date) {
        require(
                receivedDate != null && !receivedDate.isAfter(date),
                "Ngày nhập kho không được sau hôm nay.");
        require(
                expiryDate != null
                        && !expiryDate.isBefore(date)
                        && !expiryDate.isBefore(receivedDate),
                "Hạn sử dụng phải từ hôm nay trở đi và không trước ngày nhập kho.");
    }

    public static int adjustedQuantity(InventoryBatch batch, int delta, LocalDate date) {
        require(delta != 0, "Số lượng điều chỉnh phải khác 0.");
        require(
                !expired(batch, date) && batch.getStatus() == BatchStatus.ACTIVE,
                "Không thể điều chỉnh tồn kho của lô đã hết hạn hoặc bị khóa. Với lô hết hạn, dùng Hủy hàng hết hạn.");

        long nextQuantity = (long) batch.getQuantityOnHand() + delta;
        require(
                nextQuantity >= batch.getReservedQuantity() && nextQuantity <= Integer.MAX_VALUE,
                "Tồn kho sau điều chỉnh không được thấp hơn số lượng đã giữ cho đơn hàng hoặc vượt giới hạn cho phép.");
        return (int) nextQuantity;
    }

    public static int disposableQuantity(InventoryBatch batch, LocalDate date) {
        if (!expired(batch, date) || batch.getQuantityOnHand() == null || batch.getReservedQuantity() == null) return 0;
        return Math.max(0, batch.getQuantityOnHand() - batch.getReservedQuantity());
    }

    public static int disposedQuantity(InventoryBatch batch, int quantity, LocalDate date) {
        require(expired(batch, date), "Chỉ sử dụng thao tác này cho lô đã hết hạn.");
        int unreservedQuantity = batch.getQuantityOnHand() - batch.getReservedQuantity();
        require(
                quantity > 0 && quantity <= unreservedQuantity,
                "Chỉ được ghi nhận hủy số lượng chưa giữ cho đơn hàng.");
        return batch.getQuantityOnHand() - quantity;
    }

    public static void markdown(
            InventoryBatch batch,
            BigDecimal basePrice,
            BigDecimal offerPrice,
            LocalDate endDate,
            LocalDate date) {
        require(
                isWholeVndPrice(offerPrice) && offerPrice.compareTo(BigDecimal.ONE) >= 0,
                "Giá giảm phải là số nguyên đồng và từ 1 đ.");
        require(available(batch, date) > 0, "Lô phải còn số lượng khả dụng.");
        require(
                batch.getExpiryDate() != null && !batch.getExpiryDate().isAfter(date.plusDays(7)),
                "Chỉ có thể thiết lập giá giảm trong 7 ngày trước hạn sử dụng.");
        require(offerPrice.compareTo(basePrice) < 0, "Giá giảm phải thấp hơn giá bán của phân loại.");
        require(
                endDate != null
                        && !endDate.isBefore(date)
                        && !endDate.isAfter(batch.getExpiryDate()),
                "Ngày kết thúc giảm giá phải từ hôm nay đến hạn sử dụng của lô.");
    }
}
