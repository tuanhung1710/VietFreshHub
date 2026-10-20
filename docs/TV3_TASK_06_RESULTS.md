# Task 06 — Lịch sử đơn và mua lại

Ngày: 20/10/2026. Đọc lại SRS gốc phần chi tiết **UC-24 View Order History**, GBR-05 và GBR-26: chỉ hiển thị đơn của customer; mua lại chỉ thêm sản phẩm còn bán/còn tồn và báo món bị loại. Bộ lọc “Đang giao” theo yêu cầu trước đó trong hội thoại và mục B3 của workflow.

## Thay đổi production trong TV3

### History

- Bỏ lựa chọn PROCESSING/PARTIALLY_COMPLETED, thêm OUT_FOR_DELIVERY với nhãn “Đang giao” ở DTO validation/controller/service. Không thêm enum Order hoặc sửa DB.
- Query history vẫn ràng buộc customer, tìm mã bằng chuỗi literal (không biến `%`/`_` thành wildcard), lọc ngày, sort mới nhất và phân trang. Thêm điều kiện EXISTS theo ShopOrder/Delivery cho bộ lọc đang giao, tránh nhân bản dòng/count khi đa shop.
- Định nghĩa “Đang giao”: Order chưa CANCELLED/COMPLETED; ít nhất một ShopOrder OUT_FOR_DELIVERY; delivery chưa có hoặc đang PICKED_UP/OUT_FOR_DELIVERY. Delivery FAILED/DELIVERED/REDELIVERY_SCHEDULED/CANCELLED và các trạng thái chưa đi giao không được coi là đang giao. Các bộ lọc trạng thái Order khác vẫn dùng trạng thái tổng đang lưu; task này không thay luồng ghi của TV4.
- Khoảng ngày của customer dùng giờ Việt Nam, chuyển thành `[đầu ngày UTC, đầu ngày kế tiếp UTC)`. Riêng 31/12/9999 không bind năm 10000; 01/01/0001 không bind năm 0. Trang vượt dữ liệu được đưa về trang cuối; không có kết quả về trang đầu.
- SQL test ranh giới ngày phát hiện mapping `Order.placedAt` bị JDBC chuyển timezone thêm lần nữa khi Hibernate có `hibernate.jdbc.time_zone=UTC`. Thêm `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)` cho `placedAt` để DATETIME2 đọc/ghi/bind giá trị UTC trực tiếp. Không đổi kiểu cột, không dịch lại dữ liệu cũ và không chạy migration trên DB nhóm.

### Reorder

- Tách hàm thành các bước rõ ràng: khóa customer + ownership → gom số lượng theo variant → đọc giỏ/giá/tồn hiện tại → thêm hoặc báo món bị loại.
- Cộng số lượng mua lại với giỏ hiện có trước khi kiểm tra tồn; dùng long để tránh tràn int; không tự giảm số lượng nếu thiếu hàng.
- Lưu giá variant hiện tại vào giỏ, không sao chép giá/discount cũ hoặc sửa snapshot OrderItem. Không tạo order, reserve hoặc payment khi chỉ mua lại.
- Giữ transaction và khóa customer để hai thao tác mua lại không tạo giỏ trùng/mất cập nhật. Mua lại là hành động cộng thêm có chủ ý; hai lần gọi thành công cộng hai lần, không phải idempotent checkout.
- Món không còn bán hoặc thiếu tồn được báo qua `ReorderResult.skipped`, controller flash và UI giỏ hiện có; không tạo giỏ rỗng nếu tất cả món bị loại.

## Kiểm thử

- `CustomerOrderHistoryTest`: 12 unit test cho chuyển ngày, biên DATETIME2, filter đang giao/giá trị bị bỏ, dữ liệu không hợp lệ và phân trang.
- `CustomerOrderReorderTest`: 10 unit test cho giá mới, số lượng cộng dồn, tồn không đủ, variant thiếu/ngừng bán, gom dòng cùng variant, overflow, ownership, inactive customer và đơn rỗng.
- `CustomerOrderHistoryControllerTest`: 4 ca binding/model/flash: dropdown mới; page lỗi; ngày đảo; redirect giỏ kèm món bị loại/thông báo giá mới. Dùng MockMvc với view mock, chưa thay thế security filter chain.
- Thêm 4 ca SQL/JPA thật: mua lại kết hợp món mua được/món bị loại và giá mới; hai lần reorder đồng thời; history ownership/ranh giới ngày/từ khóa literal/phân trang/biên năm; lọc đang giao khi parent còn Pending và loại delivery Failed/Order Cancelled.
- Hồi quy toàn bộ bằng `scripts/tv3-sqlserver-tests.ps1`, DB mới riêng có marker từ schema/seed đã kiểm tra hash. Các lần trước có test thất bại không được tính là PASS; lỗi matcher trong unit fixture đã sửa, lỗi lệch ngày được sửa ở mapping production.

## Phạm vi còn lại

Kết quả cuối: **157/157 PASS**, 0 failure/error/skipped trong một lượt Maven: 79 Cart/Checkout, 14 cancellation unit, 18 tracking unit/template, 12 history unit, 10 reorder unit, 4 history/reorder controller và 20 SQL integration. DB riêng: `VietFreshHub_TV3_Test_6b5dd86a762f4cd184820dffe72dea8a`. Không cộng các lượt chạy lại, chưa đo coverage.

- Chưa kết luận toàn bộ customer/TV3 đã hoàn thành: security chain/CSRF HTTP toàn ứng dụng, coverage, payment/COD reconciliation và các hợp đồng trạng thái/stock/notification liên module còn theo workflow.
- Không sửa service/repository của TV1/TV2/TV4/TV5, cấu hình ứng dụng hoặc SQL nguồn/DB nhóm. `Order` là mapping dùng chung do TV3 sử dụng; thay đổi chỉ ở JDBC type của `placedAt`, API/status/quan hệ không đổi. Dữ liệu cũ từng ghi sai timezone, nếu có, cần kiểm tra riêng trước khi cân nhắc hiệu chỉnh.
- Coupon tiếp tục chờ thành viên phụ trách; online chưa bật.
