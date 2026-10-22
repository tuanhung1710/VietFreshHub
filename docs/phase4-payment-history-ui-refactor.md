# Phase 4 — Customer Payment History UI

Ngày: 2026-10-22. Hoàn thành trang Lịch sử thanh toán dựa trên backend Phase 2.

## Files thay đổi trong riêng Phase 4

Production (4):
- `src/main/resources/templates/customer/payment-history.html`
- `src/main/resources/static/css/customer-orders.css` (chỉ nối thêm rules có prefix `.payment-history-page`)
- `src/main/java/VietFreshHub/payment/dto/PaymentHistoryRow.java`
- `src/main/java/VietFreshHub/payment/repository/CustomerPaymentReadRepository.java`

Tests (3):
- `src/test/java/VietFreshHub/payment/CustomerPaymentHistoryControllerTest.java`
- `src/test/java/VietFreshHub/payment/CustomerPaymentHistoryTest.java`
- `src/test/java/VietFreshHub/payment/PaymentOrderStageQueryTest.java`

Tài liệu mới: file này. Snapshot hash trước/sau xác nhận không thay My Orders, Order Detail, stage mapping, enum entity, service/controller/filter, checkout, gateway, thu COD, kho, delivery hoặc cấu hình authentication. Không sửa schema của DB ứng dụng. Những thay đổi trước Phase 4 vẫn giữ nguyên.

## Giao diện và ý nghĩa dữ liệu

- Title: Lịch sử thanh toán. Description theo yêu cầu: “Theo dõi các khoản thanh toán và trạng thái xử lý thanh toán của đơn hàng.”
- Đổi label sai “Trạng thái đơn hàng” thành “Trạng thái thanh toán”. Giữ dropdown động từ Payment.Status, search mã đơn liên quan, ngày tạo, reset và pagination.
- Mỗi thẻ có Payment ID làm tiêu đề, ngày tạo và badge payment status. Các record cùng order vẫn hiển thị riêng; không gộp, không chọn latest attempt làm kết luận tài chính toàn đơn.
- Thân thẻ có mã đơn/link chi tiết, phương thức thanh toán, paidAt và transactionReference nếu thực sự có dữ liệu. Không có dữ liệu thì không render dòng tương ứng; không tự điền mã/timestamp.
- Footer luôn ghi “Số tiền của bản ghi”, lấy Payment.amount và định dạng tiền Việt Nam. Không tính doanh thu hoặc tổng thu từ các records.
- COD PENDING có chú thích riêng: hệ thống chưa xác nhận đã thu tiền COD cho bản ghi này. Banner đầu trang giải thích payment record có thể chưa phải tiền đã thu.
- Dùng badge có màu và nhãn chữ, card trắng, viền bo, layout desktop/mobile nhất quán My Orders. CSS mới chỉ áp dụng `.payment-history-page`; các rule My Orders/Order Detail không đổi.

## Ánh xạ trạng thái

Nhãn vẫn gọi `OrderLabels.paymentTransaction(status, methodCode)` qua DTO. Không tính lại status trong Thymeleaf/JavaScript.

| Trạng thái | Nhãn |
|---|---|
| PENDING + COD | Chờ thu COD |
| PENDING + non-COD | Chờ thanh toán |
| PROCESSING | Đang xử lý |
| PAID | Đã thanh toán |
| FAILED | Thanh toán thất bại |
| CANCELLED | Đã hủy giao dịch |
| REFUNDED | Đã hoàn tiền |
| PARTIALLY_REFUNDED | Hoàn tiền một phần |

PENDING trong dropdown dùng “Chờ thanh toán / Chờ thu COD”. Tất cả là giá trị filter rỗng, không thêm enum. Không dùng CustomerOrderStage.

## DTO/query thay đổi tối thiểu

- DTO thêm `transactionReference` vì trường này đã có trên Payment và trong SQL Server DDL. Constructor 9 tham số cũ được giữ cho compatibility; constructor projection hiện có thêm reference. Chuỗi reference rỗng/blank được chuẩn hóa null để không hiện dòng trống.
- Thêm helper `pendingCod()` chỉ để quyết định có hiện giải thích COD; nhãn trạng thái vẫn dùng mapping Phase 2.
- JPQL SELECT thêm đúng `p.transactionReference`. Không đổi join, WHERE ownership/status/keyword/date, sort, page size, count hoặc quy tắc ngày UTC. Không fetch thêm entity, không truy vấn từng card, không đọc provider payload/credentials.
- Link order lấy orderId của chính payment row đã bị giới hạn bởi customerId. Query join các quan hệ ManyToOne, không join collection; test xác nhận hai attempts cùng order vẫn trả đúng hai IDs không nhân bản.
- paidAt chỉ hiển thị là “Thời điểm ghi nhận thanh toán”, không biến thành thời điểm hoàn tiền, giao hàng hoặc xác nhận đơn.

## Kiểm thử thực tế

Maven dùng Java 21 và Mockito javaagent như các phase trước.

- Targeted suite: **94 tests, 94 passed, 0 failures/errors/skipped, BUILD SUCCESS**.
- Full suite: **317 tests, 292 passed, 0 failures, 0 errors, 25 skipped, BUILD SUCCESS**.
- 25 tests SQL Server opt-in bị bỏ qua vì chưa cấu hình DB kiểm thử riêng. Không chạy test/migration trên DB ứng dụng.
- MVC + Thymeleaf thật: đủ 8 trường hợp payment (COD/online PENDING và 6 statuses còn lại); badge từng card đúng status; COD note không áp nhầm cho online; không có order-status filter hoặc POST/retry/refund action; không giả timestamp/reference.
- Kiểm tra nhiều payments cùng một order giữ ID/label/link độc lập; field optional được ẩn khi thiếu; phương thức/reference HTML được escape; tiền có/không có phần lẻ và thời gian Việt Nam; empty/reset và selected filter giữ khi chuyển trang; binding sai không gọi payment service; identity bị từ chối trước khi đọc dữ liệu.
- Hibernate/H2 thực thi JPQL: filter Payment.Status, customer ownership, keyword và date boundaries, combined payment filters; pagination size 1 với hai attempts cùng order vẫn count=2, page khác nhau trả ID khác; reference đọc đúng từ DB và paidAt chuyển giờ Việt Nam đúng. Giữ nguyên tests Phase 2/3, chỉ bổ sung fixture column reference trong H2 biệt lập.
- Playwright/Chrome headless trên HTML do test Thymeleaf render, với fixture COD pending + online paid + online failed: tại 1440/768/390/320 px phần main không tràn ngang, có 8 options và 3 thẻ riêng, đúng 1 COD note và 1 optional reference. Đã xem ảnh desktop/mobile để kiểm tra bố cục.
- Full suite có chạy lại test My Orders, Order Detail và các kiểm tra hồi quy có sẵn.
- `git diff --check`: đạt. Không commit/push.

Logs và artifact nội bộ:
- `.document-analysis/phase4-targeted.log`
- `.document-analysis/phase4-full.log`
- `.document-analysis/phase4-visual-checks.json`
- `.document-analysis/phase4-payments-1440.png`
- `.document-analysis/phase4-payments-390.png`

## Giới hạn và phối hợp module khác

- Kiểm tra trình duyệt dùng dữ liệu fixture, không phải phiên đăng nhập/live DB. Bản render offline chặn tài nguyên CDN ngoài; chỉ xác nhận layout phần main của Payment History, chưa xác nhận global shell/CDN live.
- H2 không xác nhận execution plan hoặc runtime SQL Server; cần DB kiểm thử riêng để chạy integration tests còn bỏ qua.
- COD PAID/paidAt/aggregate phụ thuộc workflow xác nhận thu tiền của module phụ trách; trang này chỉ đọc dữ liệu, không đánh dấu đã thu dựa trên trạng thái giao hàng.
- Reference/paidAt online phụ thuộc thành viên phụ trách gateway ghi đúng trường. Trang không tạo gateway, retry, callback hoặc thông tin giao dịch giả.
- REFUNDED/PARTIALLY_REFUNDED chỉ phản ánh status đã lưu; không chứng minh dự án có đầy đủ quy trình yêu cầu/duyệt/xử lý hoàn tiền. Không thêm quy trình này trong Phase 4.
- Những vấn đề aggregate/multi-shop và đóng fulfillment đã ghi ở Phase 2/3 không thay đổi trong phase này.
