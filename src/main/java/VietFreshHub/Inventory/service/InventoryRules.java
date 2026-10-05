package VietFreshHub.Inventory.service;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Inventory.enums.BatchStatus;
import VietFreshHub.Product.exception.CatalogException;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
/** Pure business checks shared by the two modules. Dates use the shop's Vietnam timezone. */
public final class InventoryRules {
    private InventoryRules() {}
    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static LocalDate today() { return LocalDate.now(SHOP_ZONE); }
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
    public static boolean expired(InventoryBatch batch, LocalDate today) {
        return batch.getExpiryDate()!=null && batch.getExpiryDate().isBefore(today);
    }
    public static int available(InventoryBatch batch, LocalDate today) {
        return batch.getStatus()==BatchStatus.ACTIVE && !expired(batch,today)
            ? Math.max(0,batch.getQuantityOnHand()-batch.getReservedQuantity()) : 0;
    }
    public static void price(BigDecimal value) {
        require(value!=null && value.compareTo(new BigDecimal("10000"))>=0
          && value.stripTrailingZeros().scale()<=0 && value.precision()-value.scale()<=16,
          "Giá phải là số nguyên VND từ 10.000đ.");
    }
    public static void require(boolean condition,String message) {
        if (!condition) throw new CatalogException(message);
    }
    public static String reason(String value) {
        require(value!=null && !value.isBlank() && value.trim().length()<=900,"Nhập lý do (tối đa 900 ký tự).");
        return value.trim();
    }
    public static void dates(LocalDate received, LocalDate expiry, LocalDate today) {
        require(received!=null && !received.isAfter(today),"Ngày nhập không được nằm trong tương lai.");
        require(expiry!=null && !expiry.isBefore(today) && !expiry.isBefore(received),"Hạn dùng phải từ hôm nay và không trước ngày nhập.");
    }
    public static int adjustedQuantity(InventoryBatch batch,int delta,LocalDate today) {
        require(delta!=0,"Điều chỉnh phải khác 0.");
        require(!expired(batch,today) && batch.getStatus()==BatchStatus.ACTIVE,"Không điều chỉnh lô hết hạn hoặc bị khóa; hãy dùng hủy hàng hết hạn.");
        long next=(long)batch.getQuantityOnHand()+delta;
        require(next>=batch.getReservedQuantity() && next<=Integer.MAX_VALUE,"Tồn sau điều chỉnh phải lớn hơn hoặc bằng lượng giữ đơn và không vượt giới hạn.");
        return (int)next;
    }
    public static int disposedQuantity(InventoryBatch batch,int qty,LocalDate today) {
        require(expired(batch,today),"Chỉ hủy bằng thao tác này khi lô đã hết hạn.");
        require(qty>0 && qty<=batch.getQuantityOnHand()-batch.getReservedQuantity(),"Chỉ được hủy lượng chưa giữ cho đơn hàng.");
        return batch.getQuantityOnHand()-qty;
    }
    public static void markdown(InventoryBatch batch,BigDecimal base,BigDecimal price,LocalDate end,LocalDate today) {
        price(price);
        require(available(batch,today)>0,"Lô phải còn hàng bán được.");
        require(batch.getExpiryDate()!=null && !batch.getExpiryDate().isAfter(today.plusDays(7)),"Chỉ giảm giá lô cận hạn trong 7 ngày.");
        require(price.compareTo(base)<0,"Giá cận hạn phải thấp hơn giá SKU.");
        require(end!=null && !end.isBefore(today) && !end.isAfter(batch.getExpiryDate()),"Ngày kết thúc phải từ hôm nay đến hạn dùng của lô.");
    }
}
