# Task 04 — Hủy đơn customer

Ngày thực hiện: 20/10/2026. Căn cứ: SRS UC-22, GBR-25 (Pending/Confirmed được hủy trước Preparing), GBR-16 (giải phóng giữ kho).

## Thay đổi

- `CustomerOrderServiceImpl.cancellationHint`: cho phép Order Pending/Confirmed khi mọi ShopOrder còn Pending/Confirmed. `actions` và `cancel` tiếp tục dùng cùng điều kiện, nên nút hủy và POST thống nhất. Đơn đa shop bị chặn toàn bộ nếu một shop đã bắt đầu chuẩn bị; không thêm hủy một phần.
- `customer/order-detail.html`: đổi hướng dẫn hủy từ “trước xác nhận” thành “trước chuẩn bị”.
- Giữ ownership, tài khoản ACTIVE, khóa transaction, kiểm tra delivery/payment/discount, đối chiếu allocation với ledger, và hủy lặp. Không thay đổi `OrderInventoryReleaseService`, checkout hoặc production của TV1/TV2/TV4/TV5 trong task này.
- Bổ sung unit test dưới `src/test/java/VietFreshHub/order/`. SQL integration dùng lại harness checkout để tạo đơn thật và DB có marker; tránh sao chép cấu hình JPA/fixture. Các dependency ngoài đường chạy được để trống trong harness, không phải cấu hình production.

## Kiểm thử

Trước sửa: 14 unit test chạy, đúng một ca Confirmed thất bại vì bị chặn. Sau sửa: kiểm tra Pending/Confirmed, trạng thái đã xử lý/hủy, paid, online, discount, đã lấy hàng, ownership, tài khoản bị khóa, lý do quá dài, thông số trả kho và hủy lặp.

Bổ sung 7 ca SQL Server/JPA/transaction thật:

1. Đa shop Pending/Confirmed: hủy cả đơn, payment/history cập nhật, allocation RELEASED, reserved trở về trước checkout, on-hand không đổi; hủy lại không thêm RELEASE.
2. Preparing: từ chối và giữ nguyên kho.
3. Hai lần hủy đồng thời: cả hai hoàn tất, ledger chỉ trả đúng số lượng một lần.
4. Constraint thanh toán gây lỗi sau release: rollback order/shop/payment/history/stock cùng transaction; kiểm tra trực tiếp order/shop/stock và không có RELEASE commit.
5. Allocation SOLD dù trạng thái đơn còn cũ: từ chối; fixture được phục hồi sau test.
6. Ledger RESERVE không khớp allocation: từ chối, không đổi đơn/kho; fixture được phục hồi.
7. Hủy cạnh tranh với `OrderServiceImpl.startPreparing` thật: chỉ một thao tác thành công, trạng thái và RELEASE khớp nhau. Shop/auth được stub trong test để chọn shop; không kiểm thử phân quyền HTTP của TV4.

Script `scripts/tv3-sqlserver-tests.ps1` đã thêm unit test hủy đơn vào bộ hồi quy. Chạy với JAVA_HOME trỏ Java 21 và credential qua biến môi trường như Task 03; không đưa secret vào tài liệu. Script tạo DB ngẫu nhiên mới từ hai SQL đã kiểm tra hash, giữ DB để review. Thời gian chờ khóa **chỉ ở DB test** tăng từ 15 lên 60 giây, phù hợp khi nhiều transaction chờ trong môi trường integration; không đổi timeout production. Một lượt trước đã timeout khi hủy đồng thời ở ngưỡng 15 giây; không coi lượt đó là PASS.

## Giới hạn và task tiếp theo

- Chưa cho hủy tự động đơn online/đã thu tiền/refund hoặc có discount cần đối soát; chưa có nghiệp vụ hoàn tiền và coupon vẫn đợi thành viên phụ trách.
- Chưa nghiệm thu security filter/CSRF qua HTTP, coverage, tải lớn hoặc toàn bộ nhánh pickup/delivery. Test chuẩn bị dùng đường chạy TV4 hiện tại nhưng không sửa module đó.
- Task 05: tracking customer theo UC-23 — lý do giao thất bại, timeline/trạng thái và test ownership; chỉ đọc dữ liệu delivery từ TV4. Sau đó tiếp tục history/reorder và các phần payment/admin trong workflow.

## Kết quả nghiệm thu

Maven ngày 20/10/2026: **108/108 PASS**, 0 failure/error/skipped: 79 hồi quy Cart/Checkout, 14 unit test hủy đơn và 15 SQL integration (8 cũ + 7 mới). Đây là số test trong một lượt chạy tổng hợp, không cộng các lượt chạy lại; chưa đo coverage.

DB nghiệm thu: `VietFreshHub_TV3_Test_0bfeb58cad8c4afc946c7332fa578192`, tạo mới từ schema/seed nhóm đã cung cấp. Đối soát cuối: 0 chênh lệch reserved/allocation, tồn vật lý/ledger, giữ kho/ledger và tổng tiền order/shop; 0 constraint test còn sót. Không chạy ứng dụng/scheduler và không ghi DB nhóm.
