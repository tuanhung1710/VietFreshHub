package VietFreshHub.inventory.rule;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.product.exception.CatalogException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Quy tắc kho dùng chung: ngày nghiệp vụ theo giờ Việt Nam, thời điểm lưu trữ theo UTC. */
public final class InventoryRules {

    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String EXPIRED_DISPOSAL_REASON = "Hủy toàn bộ hàng hết hạn chưa giữ cho đơn hàng.";
    private static final BigDecimal MINIMUM_PRICE = new BigDecimal("10000");

    private InventoryRules() {
    }

    /** Lấy ngày hiện tại theo múi giờ Việt Nam để kiểm tra nghiệp vụ của cửa hàng. */
    public static LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    /** Lấy thời điểm UTC hiện tại, bỏ phần nano giây để phù hợp độ chính xác lưu trữ. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    /**
     * Chuyển đầu ngày tại Việt Nam sang UTC và kiểm tra mốc thời gian nằm trong giới hạn lưu trữ.
     */
    public static LocalDateTime startOfDayUtc(LocalDate date) {
        require(databaseDate(date), "Ngày vượt giới hạn lưu trữ cho phép.");
        LocalDateTime timestamp = date.atStartOfDay(SHOP_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        require(timestamp.getYear() >= 1, "Ngày vượt giới hạn lưu trữ cho phép.");
        return timestamp;
    }

    /** Đổi thời điểm UTC đã lưu thành ngày tại Việt Nam để hiển thị hoặc kiểm tra nghiệp vụ. */
    public static LocalDate shopDate(LocalDateTime utcTime) {
        return utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(SHOP_ZONE).toLocalDate();
    }

    /** Lô chỉ hết hạn khi hạn sử dụng trước ngày kiểm tra; ngay ngày hết hạn vẫn còn hạn. */
    public static boolean expired(InventoryBatch batch, LocalDate date) {
        return batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(date);
    }

    /**
     * Kiểm tra số ngày còn lại từ 0 đến mốc cảnh báo, gồm cả hai đầu; mặc định 3 ngày nếu chưa cấu hình.
     */
    public static boolean nearExpiry(InventoryBatch batch, LocalDate date, Integer warningDays) {
        Long remainingDays = daysUntilExpiry(batch, date);
        int threshold = warningDays == null ? 3 : warningDays;
        return remainingDays != null && remainingDays >= 0 && remainingDays <= threshold;
    }

    /** Tính số ngày đến hạn: âm nếu đã hết hạn, null nếu lô chưa có hạn sử dụng. */
    public static Long daysUntilExpiry(InventoryBatch batch, LocalDate date) {
        return batch.getExpiryDate() == null
                ? null
                : ChronoUnit.DAYS.between(date, batch.getExpiryDate());
    }

    /**
     * Kiểm tra lô còn hàng khả dụng và cách hạn tối đa 3 ngày.
     * Mốc áp dụng giá giảm này độc lập với số ngày nhắc hạn do người bán cấu hình.
     */
    public static boolean markdownEligible(InventoryBatch batch, LocalDate date) {
        return available(batch, date) > 0 && nearExpiry(batch, date, 3);
    }

    /**
     * Tính số lượng còn có thể bán sau khi trừ phần đã giữ cho đơn hàng.
     * Lô không hoạt động hoặc đã hết hạn được xem là không còn hàng khả dụng.
     */
    public static int available(InventoryBatch batch, LocalDate date) {
        // Lô bị khóa hoặc đã hết hạn vẫn có tồn thực tế nhưng không được tính là hàng có thể bán.
        if (batch.getStatus() != BatchStatus.ACTIVE || expired(batch, date)) {
            return 0;
        }
        return Math.max(0, batch.getQuantityOnHand() - batch.getReservedQuantity());
    }

    /**
     * Kiểm tra giá giảm thuộc đúng lô, nằm trong khoảng ngày áp dụng, thấp hơn giá gốc và lô đủ điều kiện.
     */
    public static boolean isApplicableMarkdown(
            InventoryBatch batch, BatchMarkdown markdown, BigDecimal basePrice, LocalDate date) {
        return batch.getBatchId().equals(markdown.getBatchId())
                && !markdown.getStartDate().isAfter(date)
                && !markdown.getEndDate().isBefore(date)
                && markdownEligible(batch, date)
                && markdown.getPrice().compareTo(basePrice) < 0;
    }

    /** Yêu cầu giá bán là số nguyên đồng từ 10.000 đồng, trong giới hạn 16 chữ số. */
    public static void price(BigDecimal value) {
        require(
                isWholeVndPrice(value) && value.compareTo(MINIMUM_PRICE) >= 0,
                "Giá bán phải là số nguyên đồng và từ 10.000 đ.");
    }

    /** Kiểm tra giá khác null, không có phần lẻ đồng và không vượt 16 chữ số phần nguyên. */
    private static boolean isWholeVndPrice(BigDecimal value) {
        return value != null
                && value.stripTrailingZeros().scale() <= 0
                && (long) value.precision() - value.scale() <= 16;
    }

    /** Ném lỗi nghiệp vụ với thông báo cho người dùng khi điều kiện không thỏa mãn. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new CatalogException(message);
        }
    }

    /** Kiểm tra lý do bắt buộc, tối đa 900 ký tự sau khi bỏ khoảng trắng ở hai đầu. */
    public static String reason(String value) {
        require(
                value != null && !value.isBlank() && value.trim().length() <= 900,
                "Vui lòng nhập lý do, tối đa 900 ký tự.");
        return value.trim();
    }

    /** Kiểm tra ngày nhập không sau hôm nay và hạn sử dụng không trước hôm nay hoặc ngày nhập. */
    public static void dates(LocalDate receivedDate, LocalDate expiryDate, LocalDate date) {
        require((receivedDate == null || databaseDate(receivedDate))
                        && (expiryDate == null || databaseDate(expiryDate)),
                "Ngày nhập kho và hạn sử dụng phải nằm trong khoảng năm 1 đến 9999.");
        require(
                receivedDate != null && !receivedDate.isAfter(date),
                "Ngày nhập kho không được sau hôm nay.");
        require(
                expiryDate != null
                        && !expiryDate.isBefore(date)
                        && !expiryDate.isBefore(receivedDate),
                "Hạn sử dụng phải từ hôm nay trở đi và không trước ngày nhập kho.");
    }

    /** Kiểm tra ngày khác null và thuộc khoảng năm 1 đến 9999 có thể lưu trong cơ sở dữ liệu. */
    private static boolean databaseDate(LocalDate date) {
        return date != null && date.getYear() >= 1 && date.getYear() <= 9999;
    }

    /**
     * Tính tồn sau điều chỉnh cho lô đang hoạt động, chưa hết hạn.
     * Tồn mới phải đủ cho lượng đã giữ và không vượt giới hạn số nguyên; chưa ghi vào lô.
     */
    public static int adjustedQuantity(InventoryBatch batch, int delta, LocalDate date) {
        require(delta != 0, "Số lượng điều chỉnh phải khác 0.");
        require(
                !expired(batch, date) && batch.getStatus() == BatchStatus.ACTIVE,
                "Không thể điều chỉnh tồn kho của lô đã hết hạn hoặc bị khóa. Với lô hết hạn, dùng Hủy hàng hết hạn.");

        // Tính bằng long trước khi kiểm tra giới hạn, tránh tràn int làm sai kết quả điều chỉnh.
        long nextQuantity = (long) batch.getQuantityOnHand() + delta;
        require(
                nextQuantity >= batch.getReservedQuantity() && nextQuantity <= Integer.MAX_VALUE,
                "Tồn kho sau điều chỉnh không được thấp hơn số lượng đã giữ cho đơn hàng hoặc vượt giới hạn cho phép.");
        return (int) nextQuantity;
    }

    /**
     * Tính phần hàng hết hạn chưa giữ cho đơn có thể hủy; trả về 0 nếu chưa hết hạn hoặc thiếu số liệu.
     */
    public static int disposableQuantity(InventoryBatch batch, LocalDate date) {
        if (!expired(batch, date) || batch.getQuantityOnHand() == null || batch.getReservedQuantity() == null) {
            return 0;
        }
        return Math.max(0, batch.getQuantityOnHand() - batch.getReservedQuantity());
    }

    /**
     * Tính tồn còn lại sau khi hủy lượng hàng hết hạn hợp lệ, bảo toàn lượng giữ cho đơn; chưa ghi vào lô.
     */
    public static int disposedQuantity(InventoryBatch batch, int quantity, LocalDate date) {
        require(expired(batch, date), "Chỉ sử dụng thao tác này cho lô đã hết hạn.");
        // Phần đang giữ cho đơn phải được bảo toàn, chỉ phần còn lại mới được phép hủy.
        int unreservedQuantity = batch.getQuantityOnHand() - batch.getReservedQuantity();
        require(
                quantity > 0 && quantity <= unreservedQuantity,
                "Chỉ được ghi nhận hủy số lượng chưa giữ cho đơn hàng.");
        return batch.getQuantityOnHand() - quantity;
    }

    /**
     * Kiểm tra giá giảm nguyên đồng, thấp hơn giá gốc và lô còn lượng khả dụng.
     * Cho thiết lập trong 7 ngày trước hạn; ngày kết thúc phải từ hôm nay đến hạn sử dụng.
     */
    public static void markdown(
            InventoryBatch batch,
            BigDecimal basePrice,
            BigDecimal offerPrice,
            LocalDate endDate,
            LocalDate date) {
        // Đây là điều kiện thiết lập trước giá giảm trong 7 ngày; điều kiện áp dụng thực tế là 3 ngày.
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
