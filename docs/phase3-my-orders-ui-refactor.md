# Phase 3 — Customer My Orders UI

Ngày: 2026-10-22. Phạm vi: trang Đơn hàng của tôi và dữ liệu đọc phục vụ riêng trang này.

## Giao diện

- Giữ header/footer và các fragment hiện có. Trang dùng màu xanh VietFresh Hub, nền sáng, thẻ trắng, viền bo tròn; CSS mới có prefix `.my-orders-page` để không tác động Payment History/Order Detail.
- Header thẻ: thời điểm đặt theo Việt Nam, mã đơn có link và badge stage từ Phase 2.
- Sản phẩm: tối đa 3 phân loại đầu tiên, tên/variant/số lượng từ OrderItem snapshot, thumbnail hiện tại của variant nếu có; thiếu ảnh có placeholder. Hiển thị số phân loại, tổng số lượng và số shop; link chi tiết khi có nhiều hơn 3 phân loại.
- Tiến trình: hiển thị tên và trạng thái từng shop. Khi có delivery, secondary label gắn ngay cạnh đúng shop, không áp trạng thái một shop cho cả đơn. OUT_FOR_DELIVERY có delivery dùng nhãn tổng quát “Vận chuyển” bên cạnh nhãn giao hàng cụ thể, tránh hiện “Đang giao” cạnh lỗi giao thất bại.
- Thanh toán: phương thức và trạng thái của tối đa 3 payment record gần nhất (paymentId giảm dần), có mã bản ghi; nhiều hơn 3 có thông báo xem đầy đủ tại chi tiết. Không coi bản ghi gần nhất là tổng thu, không cộng attempts. Tổng thanh toán của đơn luôn lấy Order.grandTotal đã lưu.
- Tìm kiếm mã đơn, dropdown stage, từ/đến ngày đặt, reset, lỗi validation, empty state, số kết quả và phân trang giữ nguyên. Tất cả filter được giữ trong URL khi đổi trang.
- Xem chi tiết luôn có. Theo dõi khi có shop (trang timeline nội bộ hiện có hỗ trợ cả trước giao). Mua lại khi có dòng hàng cũ; khả dụng/giá/tồn kho vẫn kiểm tra tại endpoint reorder. Hủy chỉ khi cancellationHint hiện có trả về null; dùng details/summary để mở form xác nhận có lý do tùy chọn và POST endpoint hiện có, CSRF qua Thymeleaf/Spring Security. Backend vẫn kiểm tra lại và có thể từ chối nếu trạng thái đổi sau khi render.

## Stage và nhãn

Không sửa CustomerOrderStageQuery hoặc enum entity. Thứ tự dropdown trên trang:

| Giá trị | Nhãn |
|---|---|
| rỗng | Tất cả |
| WAITING_PAYMENT | Chờ thanh toán |
| WAITING_CONFIRMATION | Chờ xác nhận |
| PREPARING | Đang chuẩn bị |
| AWAITING_DELIVERY | Chờ giao hàng |
| SHIPPING | Vận chuyển |
| COMPLETED | Hoàn thành |
| CANCELLED | Đã hủy |

Primary badge gọi `OrderListRow.statusLabel()` đã có. Thymeleaf/JS không tính lại stage. Trường hợp mixed shop tiếp tục theo quy tắc Phase 2.

Secondary delivery: OUT_FOR_DELIVERY → Đang giao; FAILED → Giao hàng thất bại; REDELIVERY_SCHEDULED → Chờ giao lại; DELIVERED → Đã giao hàng. Các trạng thái khác dùng OrderLabels.delivery hiện có. Primary stage vẫn có thể SHIPPING khi giao thất bại/chờ giao lại/đã giao chưa hoàn tất; secondary label cho biết tình hình thật của từng shop.

Payment dùng OrderLabels.paymentTransaction(status, methodCode) của Phase 2: COD PENDING → Chờ thu COD; online PENDING → Chờ thanh toán; PROCESSING → Đang xử lý; PAID → Đã thanh toán; FAILED → Thanh toán thất bại; CANCELLED → Đã hủy giao dịch; REFUNDED → Đã hoàn tiền; PARTIALLY_REFUNDED → Hoàn tiền một phần. Không thêm action hoàn tiền/retry.

## DTO và truy vấn hỗ trợ

Thêm OrderHistorySummary (nested Product, Shop), sử dụng lại OrderDetailsResponse.PaymentInfo. Không thay cấu trúc OrderListRow hoặc query stage/pagination Phase 2.

Controller lấy Page<OrderListRow> trước, sau đó gọi historySummaries cho tối đa 10 ID của trang. Service readOnly dùng đúng 4 truy vấn bổ sung:

1. Orders theo customerId + orderIds (không nhận dữ liệu của chủ khác).
2. ShopOrders + shop + items + variant trong một truy vấn batch; chỉ fetch một collection, không phân trang trên collection fetch.
3. Payments + paymentMethod theo batch.
4. OrderDeliveryView theo batch.

Mọi batch query đều có ownership predicate riêng. Sau query đầu chỉ dùng các ID thuộc chủ sở hữu. Group/map tại service, trả DTO thuần; không lazy load trong Thymeleaf. Không gọi actions/getDetails riêng cho từng card. Với trang rỗng không chạy batch; toàn ID của chủ khác chỉ chạy query Orders rồi dừng. Các bộ lọc/count/pagination vẫn ở database, xử lý ngày Việt Nam vẫn ở history() cũ.

Chính sách hủy dùng trực tiếp cancellationHint hiện có, không viết lại điều kiện trong template và không sửa cancel/reorder/stock/delivery workflows.

## Files thay đổi trong riêng Phase 3

UI:
- `src/main/resources/templates/customer/orders.html`
- `src/main/resources/static/css/customer-orders.css`

Code hỗ trợ:
- `src/main/java/VietFreshHub/order/controller/CustomerOrderController.java`
- `src/main/java/VietFreshHub/order/dto/OrderHistorySummary.java` (mới)
- `src/main/java/VietFreshHub/order/service/CustomerOrderService.java`
- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderServiceImpl.java` (chỉ thêm read-only summary và nhãn phụ)
- `src/main/java/VietFreshHub/order/repository/OrderRepository.java`
- `src/main/java/VietFreshHub/order/repository/ShopOrderRepository.java`
- `src/main/java/VietFreshHub/order/repository/OrderDeliveryReadRepository.java`
- `src/main/java/VietFreshHub/payment/repository/PaymentRepository.java` (chỉ thêm batch read phục vụ My Orders)

Tests:
- `src/test/java/VietFreshHub/order/CustomerOrderHistorySummaryTest.java` (mới)
- `src/test/java/VietFreshHub/order/CustomerOrdersViewTest.java` (mới)
- `src/test/java/VietFreshHub/payment/PaymentOrderStageQueryTest.java` (thêm test combined order filters; không đổi kỳ vọng Phase 2)

Tài liệu: file này. Những file đã thay đổi từ trước Phase 3 vẫn được giữ nguyên.

## Kiểm thử đã thực hiện

- Targeted Maven: **86 tests, 86 passed, 0 failures/errors/skipped**.
- Full Maven: **300 tests, 275 passed, 0 failures, 0 errors, 25 skipped; BUILD SUCCESS**. 25 SQL Server integration tests opt-in bị bỏ qua do chưa cấu hình DB kiểm thử riêng; không chạy trên DB ứng dụng.
- Truy vấn H2 thật xác minh ownership trong cả batch và filter. Đo Hibernate statistics: **4 statement** cho summary nhiều đơn/nhiều shop/nhiều dòng; không tăng khi truy cập DTO. Kiểm tra snapshot khác tên catalog hiện tại, giới hạn preview, tổng số lượng, COD PENDING, online PAID, shop hoàn thành kèm shop FAILED và điều kiện hủy hiện có.
- MVC + Thymeleaf render toàn template: 7 primary stages, 4 delivery states, mixed-shop labels, snapshot/ảnh thiếu, escape dữ liệu HTML, tiền Việt Nam, giờ Việt Nam, actions đúng endpoint, ẩn hủy khi không được phép, CSRF hidden input, filter/pagination, empty state và reset.
- Test Phase 2 và regression chạy lại: COD/online statuses, nhiều shop, completed/cancelled, date range/biên UTC, keyword, pagination, ownership, checkout/cancel/reorder có sẵn. Thêm test kết hợp customerId + stage + keyword + dates.
- Playwright/Chrome headless trên HTML thực do test Thymeleaf render với dữ liệu fixture, tại 1440/768/390/320 px: không overflow ngang phần main; 8 options; mở được form xác nhận hủy. Đã xem ảnh desktop/mobile để kiểm tra bố cục.
- `git diff --check`: đạt. Không commit/push.

Log: `.document-analysis/phase3-targeted.log`, `.document-analysis/phase3-full.log`. Kết quả viewport: `.document-analysis/phase3-visual-checks.json`.

## Giới hạn

- Kiểm tra trình duyệt dùng dữ liệu test, không phải session đăng nhập/live DB. Tài nguyên CDN bên ngoài bị chặn trong bản render offline; kiểm tra visual tập trung phần main của trang mới, chưa xác nhận global shell với CDN thực.
- Thumbnail không có snapshot lịch sử; dùng ảnh variant hiện tại, không gọi catalog theo từng sản phẩm. Tên/variant/số lượng và tiền đơn lấy dữ liệu đã lưu.
- Chỉ hiển thị 3 phân loại và 3 payment record trên card để giữ thẻ gọn; dữ liệu đầy đủ xem tại chi tiết. Batch vẫn nạp dòng hàng của các đơn trong trang để tính số lượng và eligibility, chưa phân trang con theo sản phẩm.
- Lỗi đồng bộ parent/ShopOrder, thu COD, đóng đơn sau giao thất bại và xác nhận hàng về là các hợp đồng Phase 2 đã ghi; không thay đổi ở đây.
- Test H2 không chứng minh execution plan hoặc runtime SQL Server. Không sửa schema hay chạy dữ liệu SQL.
