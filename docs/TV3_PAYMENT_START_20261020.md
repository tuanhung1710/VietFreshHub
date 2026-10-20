# Mốc bắt đầu thử triển khai payment — 2026-10-20

Mốc theo yêu cầu: “làm thử coi sao ... nếu tôi có bảo xóa thì sẽ xóa từ đoạn này để làm lại”.
Chỉ gỡ các thay đổi thuộc mốc này khi được yêu cầu; không git reset/clean toàn dự án.

## Lịch sử bước đầu Task 13.1 (đã được mở rộng ở dưới)

- Thêm VnpayProperties, VnpayPaymentRequest, VnpayGateway, VnpayGatewayImpl và VnpayGatewayTest.
- Gateway tạo URL sandbox, số tiền nhân 100 chính xác, thời gian GMT+7, ký HMAC-SHA512.
- Kiểm tra chữ ký bằng so sánh constant-time; từ chối tham số VNPAY trùng.
- Xác minh chữ ký chỉ xác nhận tính toàn vẹn, không xác nhận đã trả tiền.
- Không controller, không endpoint công khai, không ghi DB, không thay COD/checkout/security.
- Chưa lưu attempt, đối chiếu merchant/reference/amount với DB, IPN, Return UI, retry, timeout hoặc payment gate.
- Chưa thử giao dịch thật trên sandbox; test dùng secret giả.
- Bổ sung tiếp theo trong cùng mốc: checkout hiển thị VNPAY ở trạng thái disabled,
  có thông báo chưa khả dụng. Không bật chỉ dựa vào credential/config flag khi chưa có IPN và payment gate.

Kiểm chứng: Java 21, `./mvnw.cmd -o -Dtest=VnpayGatewayTest test`: 10 test PASS,
không skip. Có vector chữ ký tính độc lập bằng Python hmac/sha512.
Toàn bộ source và test source biên dịch thành công; chưa chạy lại toàn bộ suite hay SQL.

## Cấu hình của bước đầu (xem cập nhật hiện tại ở cuối)

Giữ VNPAY_TMN_CODE và VNPAY_HASH_SECRET trong Run Configuration IntelliJ.
VNPAY_ENABLED mặc định false. Chưa bật trước khi hoàn thành endpoint và lưu payment attempt.
Return URL hiện có chưa được xác nhận hoạt động, chưa có handler.
Gateway nhận createdAt/expiresAt từ service; không áp đặt chính sách thời hạn chờ.
Không log secret hoặc URL thanh toán đầy đủ.

Tài liệu giao thức: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html

## Gỡ để làm lại

Các file mới thuộc mốc này:

1. src/main/java/VietFreshHub/payment/config/VnpayProperties.java
2. src/main/java/VietFreshHub/payment/dto/VnpayPaymentRequest.java
3. src/main/java/VietFreshHub/payment/service/VnpayGateway.java
4. src/main/java/VietFreshHub/payment/service/impl/VnpayGatewayImpl.java
5. src/test/java/VietFreshHub/payment/VnpayGatewayTest.java
6. docs/TV3_PAYMENT_START_20261020.md

File cũ sửa thêm: src/main/resources/templates/customer/checkout.html — gỡ label VNPAY
và đoạn p có id vnpay-unavailable để trở về trước mốc; giữ nguyên COD và các phần khác.

File cấu hình cũ sửa: src/main/resources/application.properties, đúng ba dòng:

```properties
# Trước mốc
app.payment.vnpay.enabled=true
app.payment.vnpay.tmn-code=${VNPAY_TMN_CODE}
app.payment.vnpay.hash-secret=${VNPAY_HASH_SECRET}
# Sau mốc
app.payment.vnpay.enabled=${VNPAY_ENABLED:false}
app.payment.vnpay.tmn-code=${VNPAY_TMN_CODE:}
app.payment.vnpay.hash-secret=${VNPAY_HASH_SECRET:}
```

Đọc diff hiện tại trước khi khôi phục để bảo toàn chỉnh sửa mới của người dùng.
Không xóa recommendation, cart controller/templates/tests, SQL script hoặc các tài liệu workflow/status có trước mốc.
Trạng thái Git trước mốc lưu cục bộ ở .codex/payment-start-20261020/status-before.txt.
Không thay đổi biến môi trường IntelliJ của người dùng.

## Mở rộng cùng mốc: luồng sandbox Customer

Theo yêu cầu tiếp theo “payment gateway hoạt động ... cả sandbox”, đã nối checkout,
attempt audit/receipt, IPN/Return, trang trạng thái, polling, retry, expiry và hủy chưa thu tiền.
Người dùng chốt **15 phút sandbox**, cung cấp domain `https://buffed-commence-duh.ngrok-free.dev`.
Các đoạn mô tả “chưa có handler/không ghi DB” ở trên chỉ ghi lại bước 13.1 ban đầu.
Hiện trạng và hướng dẫn: TV3_VNPAY_SANDBOX.md.

### File mới bổ sung thuộc mốc (ngoài danh sách ban đầu)

- src/main/java/VietFreshHub/payment/config/VnpayCallbackSecurity.java
- src/main/java/VietFreshHub/payment/controller/CustomerVnpayController.java
- src/main/java/VietFreshHub/payment/controller/VnpayCallbackController.java
- src/main/java/VietFreshHub/payment/dto/VnpayPaymentPage.java
- src/main/java/VietFreshHub/payment/entity/PaymentTransaction.java
- src/main/java/VietFreshHub/payment/repository/PaymentTransactionRepository.java
- src/main/java/VietFreshHub/payment/service/VnpayPaymentService.java
- src/main/java/VietFreshHub/payment/service/VnpayExpiryJob.java
- src/main/java/VietFreshHub/payment/service/impl/VnpayPaymentServiceImpl.java
- src/main/resources/templates/customer/vnpay-payment.html
- src/main/resources/templates/customer/vnpay-return.html
- src/main/resources/static/js/vnpay-payment.js
- src/test/java/VietFreshHub/payment/VnpayPaymentServiceTest.java
- src/test/java/VietFreshHub/payment/VnpayHttpSecurityTest.java
- src/test/java/VietFreshHub/payment/VnpayViewTest.java
- scripts/tv3-vnpay-sandbox.sql
- docs/TV3_VNPAY_SANDBOX.md

### File có sẵn sửa thêm trong mốc

- checkout/controller/CheckoutController: thêm dependency vnpay, model khả dụng và redirect online.
- checkout/dto/CheckoutRequest: pattern COD thành COD|VNPAY.
- checkout/dto/CheckoutPreview: overload canConfirm(boolean).
- checkout/service/impl/CheckoutServiceImpl: dependency vnpay, requirePaymentMethod, prepare và khóa SKU WRITE.
- order/service/impl/CustomerOrderServiceImpl: cho phép hủy VNPAY provider đã khởi tạo nhưng chưa thu tiền.
- payment/repository/PaymentRepository: truy vấn reference và job expiry.
- application.properties: ba dòng ban đầu, thêm timeout-minutes=15 và Return URL theo biến VNPAY_RETURN_URL.
- templates/customer/checkout.html, order-detail.html, payment-history.html: lựa chọn/link/text VNPAY.
- tests CheckoutServiceImplTest, CheckoutFailureRecoveryTest: dependency mới và 2 ca online checkout.
- tests CheckoutSqlServerIntegrationTest: dependency mới, cài script test sau kiểm tra marker,
  các helper bắt đầu `vnpay()` và 5 test bắt đầu `vnpay...` trước AfterAll.
  **Giữ nguyên các helper/test recommendation có trước mốc.**
- tests CustomerHttpHarness: dependency online, overload constructor, đăng ký callback/controller và import chain.
  **Giữ nguyên dòng productRecommendations có trước mốc.**
- docs/TV3_TASK_STATUS_01_17.md: cập nhật hàng 13/14 và thứ tự tiếp tục.
- scripts/tv3-sqlserver-tests.ps1: nếu thêm danh sách test VNPAY, chỉ gỡ các tên test VNPAY,
  giữ ProductRecommendationTest/ProductRecommendationViewTest có trước mốc.

Các đường dẫn package bên trên tương đối với src/main/java/VietFreshHub hoặc src/test/java/VietFreshHub.
Khi gỡ, so diff theo từng phần, không phục hồi toàn bộ file đang có thay đổi từ trước.
DB test đã thêm VNPAY method, hai index UX_TV3_* và TR_TV3_VnpayPaymentGate.
Không xóa audit/receipt hoặc payment data như một bước gỡ code; cần review dữ liệu riêng.
DB chung chưa được tự áp dụng script. Không sửa secret hoặc Run Configuration IntelliJ.

Kiểm chứng cuối lượt mở rộng: toàn bộ 246 test PASS, 0 skip; 30 ca SQL Server.
Ngrok và IPN public đã kiểm tra kết nối/97; chưa tạo giao dịch trực tiếp với merchant thật.
