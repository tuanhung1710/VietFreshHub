package VietFreshHub.inventory.dto;

import VietFreshHub.inventory.rule.InventoryRules;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ActivityHistoryFilter {
    public static final Map<String, String> ACTIONS;
    public static final Map<String, String> ENTITIES = Map.of(
            "PRODUCT", "Sản phẩm", "VARIANT", "Phân loại", "BATCH", "Lô hàng");
    static {
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("CREATE", "Tạo sản phẩm");
        actions.put("UPDATE", "Chỉnh sửa sản phẩm / phân loại");
        actions.put("STATUS", "Thay đổi trạng thái");
        actions.put("ADD_SKU", "Thêm phân loại");
        actions.put("PRICE", "Thay đổi giá");
        actions.put("REMINDER", "Thay đổi mốc nhắc hạn sử dụng");
        actions.put("RESTOCK", "Nhập kho");
        actions.put("EDIT", "Chỉnh sửa lô");
        actions.put("ADJUSTMENT", "Điều chỉnh tồn kho");
        actions.put("DISPOSE", "Hủy hàng hết hạn");
        actions.put("BLOCK", "Khóa lô");
        actions.put("UNBLOCK", "Mở khóa lô");
        actions.put("MARKDOWN", "Thiết lập giảm giá lô");
        actions.put("CANCEL_MARKDOWN", "Dừng giảm giá lô");
        actions.put("RESERVE", "Giữ hàng cho đơn");
        actions.put("RELEASE", "Giải phóng hàng giữ");
        actions.put("SALE", "Xuất bán");
        ACTIONS = Collections.unmodifiableMap(actions);
    }

    private String q = "";
    private String action = "";
    private String entity = "";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    public void normalize() {
        q = q == null ? "" : q.trim();
        action = action == null ? "" : action.trim();
        entity = entity == null ? "" : entity.trim();
        InventoryRules.require(q.length() <= 200, "Từ khóa tìm kiếm không được quá 200 ký tự.");
        InventoryRules.require(action.isEmpty() || ACTIONS.containsKey(action), "Chọn loại hoạt động hợp lệ.");
        InventoryRules.require(entity.isEmpty() || ENTITIES.containsKey(entity), "Chọn đối tượng hợp lệ.");
        InventoryRules.require(from == null || to == null || !from.isAfter(to),
                "Ngày bắt đầu không được sau ngày kết thúc.");
    }

    public boolean isActive() {
        return !q.isBlank() || !action.isBlank() || !entity.isBlank() || from != null || to != null;
    }
}
