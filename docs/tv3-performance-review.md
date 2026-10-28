# Review hiệu năng TV3 — 28/10/2026

## Phạm vi và kết luận

Rà các luồng giỏ, danh mục/chi tiết sản phẩm, polling tồn kho, checkout, lịch sử/chi tiết/theo dõi đơn, thanh toán và gợi ý sản phẩm. Chỉ sửa các đoạn thuộc Tuấn Hùng và thêm cấu hình đo thời gian mới. Giữ các thay đổi MVC/clean code trước đó.

Đã giảm công việc thừa được xác nhận trong source. Chưa có benchmark request trên database SQL Server thực tế hoặc execution plan, vì vậy không công bố phần trăm tăng tốc hay khẳng định đã giải quyết hoàn toàn độ trễ người dùng đang gặp.

## Những phần đã tối ưu

| Phần | Trước | Sau |
| --- | --- | --- |
| Header trang chủ/chi tiết | Tải cả giỏ, tồn kho, kiểm tra catalog và tạo DTO chỉ để hiển thị số lượng | Một SUM(quantity) cho toàn giỏ ACTIVE |
| Header checkout, thêm/sửa địa chỉ checkout | Lặp lại getActiveCart() sau khi preview đã đọc giỏ | Dùng countActiveUnits(), không đọc lại tồn kho/catalog |
| Kiểm tra danh mục trong getActiveCart() | Mỗi variant hợp lệ có thể query lại danh sách danh mục | Một lần lấy danh mục hợp lệ cho cả lần đọc giỏ |
| Ghép giao hàng trong lịch sử đơn | Mỗi shop quét lại danh sách giao hàng | Lập map giao hàng đầu tiên theo shop, rồi tra cứu; giữ đúng lựa chọn dòng đầu như trước |

Badge vẫn tính tất cả món trong giỏ ACTIVE, không lấy tổng của riêng các món chọn checkout. Trang /cart vẫn đọc tồn kho hiện tại và giữ toàn bộ dữ liệu hiển thị. Không cache giá, tồn kho, quyền tài khoản hoặc reservation giữa các request.

Batch eligibility áp dụng lại cùng điều kiện variant ACTIVE, sản phẩm ACTIVE/APPROVED, shop ACTIVE/OPEN, danh mục cùng các ancestor hợp lệ và sản phẩm có variant ACTIVE/OUT_OF_STOCK. Có test so sánh với hàm kiểm tra đơn lẻ và xác nhận chỉ đọc danh mục một lần. Hàm kiểm tra đơn lẻ và các đoạn dùng chung của tác giả khác được giữ nguyên.

## Các điểm còn cần đo trước khi tối ưu sâu

| Mức độ | Điểm phát hiện | Tình trạng / hướng xử lý |
| --- | --- | --- |
| Cao | Mở checkout chờ gọi tuyến VietMap lần lượt theo shop trước khi render HTML | Chưa thay đổi quy tắc báo giá. Đã thêm đo thời gian. Nếu xác nhận là điểm nghẽn, cần thiết kế tái sử dụng báo giá có thời hạn/điều kiện rõ ràng hoặc tải phí riêng; không tự dùng lại báo giá cũ vô điều kiện. |
| Cao | Query trạng thái lịch sử đơn có CASE và nhiều EXISTS/NOT EXISTS, cùng truy vấn phân trang/count | Giữ nguyên chính sách trạng thái. Cần lấy SQL và execution plan từ SQL Server để xác định query, scan/index và thời gian compile/thực thi. Không suy ra hiệu năng production từ H2. |
| Trung bình | Lịch sử đơn lấy đầy đủ các dòng hàng rồi chỉ xem trước ba dòng | Giữ tổng số món/số lượng và các query theo nhóm đang có. Nếu dữ liệu thực lớn, bước tiếp theo là projection/aggregate riêng; không LIMIT 3 rồi tính sai tổng. |
| Trung bình | Quan hệ product.categories có thể lazy-load theo từng sản phẩm khác nhau | Gom query danh mục chung chưa đồng nghĩa loại hết lazy query. Cần đếm SQL thực tế trước khi sửa fetch plan; không join-fetch nhiều collection tùy tiện. |
| Trung bình | Xác nhận/hủy đơn có thể chờ row lock | Cần đo lock wait SQL Server. Không bỏ PESSIMISTIC_WRITE hay tách transaction để làm nhanh. |
| Tùy dữ liệu | Tracking quét lịch sử cho từng shop | Phần có code Khong Hung, không sửa. Cần thống nhất phạm vi nếu kết quả đo chỉ ra đây là điểm nghẽn. |
| Tùy môi trường | Kiểm tra tài khoản/quyền, thông báo trên header, CDN/font/script | Có thêm công việc ngoài luồng TV3. Không thay đổi cơ chế bảo mật hoặc code chung của người khác. Phân biệt thời gian chờ server với tải tài nguyên qua Network. |

## Các phần đã rà và giữ nguyên

- Chi tiết đơn dùng projection và các query theo nhóm, badge đã dùng aggregate.
- Lịch sử thanh toán có phân trang và projection; không tải toàn bộ lịch sử vào Java.
- Gợi ý sản phẩm đã giới hạn ứng viên và đọc variant theo nhóm.
- Polling có chu kỳ, dừng ở tab ẩn, backoff và chống phản hồi cũ. Không tăng tần suất hoặc thêm cache tồn kho.
- Thêm giỏ tiếp tục POST MVC → redirect → GET; không khôi phục API POST AJAX.
- Checkout, allocation, release và payment lifecycle giữ nguyên.

## Xác minh trên ứng dụng chạy thật

Theo yêu cầu giữ code gọn, lớp interceptor đo thời gian tạm thời và các wrapper ghi log đã được bỏ. Các tối ưu đếm badge, gom danh mục và tra giao hàng bằng map vẫn được giữ.

Dùng DevTools Network để phân biệt Waiting/TTFB với tải tài nguyên. Query lịch sử đơn và các lần gọi VietMap cần đo bằng profiler/SQL Server trên môi trường chạy thực tế trước khi sửa sâu. Không công bố mức tăng tốc khi chưa có benchmark.

## File chính

- CartRepository / CartService / CartServiceImpl: aggregate badge và batch eligibility.
- CatalogService / CatalogServiceImpl: kiểm tra khả năng mua theo nhóm trong một lần đọc giỏ.
- CustomerCartViewController / CheckoutController: dùng badge nhẹ.
- CustomerOrderServiceImpl: tra giao hàng bằng map cho lịch sử.
- Test badge, batch policy và các test render/HTTP cập nhật để kiểm tra đúng loại truy vấn mới.

## Kết quả kiểm thử

Toàn bộ suite: 379 test, 352 đạt, 0 lỗi, 27 test SQL Server bỏ qua do chưa có cấu hình database test riêng. BUILD SUCCESS. Test badge xác nhận không đọc catalog/inventory; test batch xác nhận cùng kết quả chính sách và chỉ đọc danh mục một lần. Log nằm ở `.document-analysis/performance-verified-tests.log`. Các test SQL Server cần database test riêng; mock hoặc H2 không chứng minh được hiệu năng/lock trên SQL Server.
