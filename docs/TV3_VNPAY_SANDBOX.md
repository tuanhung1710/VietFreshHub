# VNPAY sandbox — hướng dẫn chạy và review TV3

## Kết quả kiểm chứng ngày 20/10/2026

- Java 21, `./mvnw.cmd -o test` với TV3_TEST_JDBC_URL trỏ DB có marker:
  **246 test PASS, 0 failure/error/skip** trong lần chạy cuối.
- Bao gồm 30 test SQL Server, 10 gateway, 14 nghiệp vụ VNPAY, 6 HTTP/security,
  4 render UI VNPAY và toàn bộ các test TV3 còn lại.
- Các lần chạy trước tìm thấy lỗi thiếu cartBadge ở header và tranh chấp khóa SKU;
  đã sửa rồi mới chạy lại toàn bộ. Không bỏ hoặc nới assertion concurrency.
- Audit DB `VietFreshHub_TV3_Test_36cfed1852624fb790a2a128ad9fcbac`:
  reserved/allocation, on-hand/ledger, tổng đơn/shop và receipt trùng đều 0 sai lệch;
  không còn constraint fault-injection.
- Kiểm tra cú pháp Node cho vnpay-payment.js và git diff --check đạt.
- Ngrok local API xác nhận domain được cung cấp trỏ http://localhost:8080;
  cả localhost và HTTPS IPN trả HTTP 200 JSON RspCode 97 khi thiếu chữ ký.
- Chưa thanh toán trực tiếp trên màn hình VNPAY bằng merchant của người dùng;
  chưa xác nhận IPN đã được đăng ký trong merchant portal; chưa đo coverage.

Kết luận review: luồng sandbox đã có code và kiểm thử tích hợp, nhưng chưa đánh dấu
nghiệm thu provider thật/production. Mặc định online vẫn tắt; script chỉ được chạy trong DB test.

## Luồng đã triển khai

Checkout chọn VNPAY → tạo Order/ShopOrder và giữ kho trong cùng transaction →
Payment PENDING có reference ngẫu nhiên → audit VNPAY_INIT lưu hạn 15 phút →
trang thanh toán Customer → POST mở VNPAY → Return hiển thị thông báo →
IPN có chữ ký hợp lệ cập nhật Payment/Order và lưu receipt → Customer đọc lại trạng thái.

Return không cập nhật tiền. IPN không phụ thuộc session đăng nhập. Trang Customer luôn kiểm tra ownership.
Một payment cho toàn Order nhiều shop; số tiền do backend lấy từ grandTotal.
Không gọi VNPAY khi đang giữ transaction chờ network: gateway chỉ tạo URL để browser mở.

## Chạy sandbox

1. Dùng DB sandbox riêng. Script test hiện đã tạo DB trong `target/tv3-sqlserver/db-name.txt`.
   Nếu muốn chạy giao diện trên DB này, đặt `SPRING_DATASOURCE_URL` trong Run Configuration
   trỏ tới đúng tên DB đó, giữ username/password ở môi trường. Không chạy app/scheduler trên DB nhóm chỉ để thử.
   Đặt `SPRING_JPA_HIBERNATE_DDL_AUTO=none`; script không cần thêm bảng hoặc cột.
2. Với DB sandbox khác: kiểm tra `SELECT DB_NAME();` trong SSMS, sau đó chạy
   `scripts/tv3-vnpay-sandbox.sql` trên đúng DB. Script thêm method VNPAY nếu chưa có,
   hai unique index và trigger chặn xử lý đơn online chưa thanh toán. Chạy lại được.
   Không tự sửa/trộn vào hai SQL gốc của nhóm.
3. IntelliJ → Run → Edit Configurations → VietFreshHubApplication → Environment variables:

   ```text
   VNPAY_ENABLED=true
   VNPAY_TMN_CODE=<merchant sandbox>
   VNPAY_HASH_SECRET=<secret sandbox, không commit>
   VNPAY_RETURN_URL=https://buffed-commence-duh.ngrok-free.dev/payments/vnpay/return
   ```

   Không đổi secret bằng giá trị test-secret trong test. Code khóa URL gateway ở sandbox,
   không hỗ trợ production trong lượt này. Nếu thiếu method/index/trigger hoặc cấu hình sai,
   VNPAY trên checkout vẫn disabled.
4. Chạy ứng dụng trên 8080, terminal riêng chạy `ngrok http 8080` và giữ mở.
   URL hiện người dùng cung cấp là `https://buffed-commence-duh.ngrok-free.dev`.
   Nếu URL thay đổi, cập nhật cả Return URL và cấu hình IPN với VNPAY.
5. Khai báo IPN URL trong cấu hình merchant sandbox theo hướng dẫn VNPAY:
   `https://buffed-commence-duh.ngrok-free.dev/payments/vnpay/ipn`.
   Return URL không thay thế IPN. Chỉ cấu hình URL trong properties chưa làm VNPAY tự gửi IPN.
6. Đăng nhập Customer trên localhost:8080 → giỏ → checkout → chọn VNPAY → xác nhận →
   tiếp tục thanh toán. Dùng dữ liệu thẻ/tài khoản thử do VNPAY cung cấp trên tài liệu sandbox,
   không dùng tiền hoặc thẻ thật. Sau khi quay lại, mở lịch sử/chi tiết đơn để xem kết quả chính thức.

GET IPN không tham số phải trả JSON có RspCode 97, không redirect /login.
Đây là kiểm tra kết nối/route, không phải bằng chứng một giao dịch đã thanh toán.
GET Return không tham số hiển thị thông báo không xác minh được; không cập nhật DB.

## Thử các trường hợp

- Thành công: IPN xác minh đủ dữ liệu → Payment PAID, Order.paymentStatus PAID, một VNPAY_RECEIPT.
- Return về trước IPN: trang không tự tuyên bố thành công; trạng thái được đọc từ server mỗi 5 giây.
- Bấm lại khi đang chờ: mở lại cùng reference; không tạo một khoản mới chỉ vì double click.
- VNPAY báo thất bại: Payment FAILED; Customer thử lại tạo reference mới, giữ hạn gốc của đơn.
- Callback trùng: RspCode 02, không thêm receipt hoặc đổi trạng thái lần nữa.
- Chữ ký/merchant sai: 97; mã tham chiếu không có: 01; số tiền sai: 04; lỗi transaction: 99 để VNPAY retry.
- Hủy đơn online chưa thu tiền: dùng luồng hủy Customer và RELEASE hiện có.
- Quá hạn: job quét từng nhóm 100 đơn, khóa và xử lý từng đơn trong transaction riêng;
  chỉ hủy đơn PENDING/UNPAID có tất cả shop PENDING; gọi RELEASE có kiểm tra ledger, không tăng on-hand.
- Callback thành công muộn, lần thử cũ hoặc đơn đã hủy: receipt REVIEW, Payment PROCESSING;
  không phục hồi đơn, không cho trả thêm. Việc hoàn tiền/đối soát cần người phụ trách xử lý.
- Đơn đã PAID không được tự hủy/hoàn tiền qua luồng hủy chưa thu tiền.

Job tiếp tục xử lý các đơn VNPAY đã tạo dù tắt tạo thanh toán mới. Chữ ký callback vẫn được kiểm tra
khi VNPAY_ENABLED=false để không bỏ sót tiền của giao dịch đang chờ; cần giữ merchant/secret cho các giao dịch đó.

## Review kiến trúc và giới hạn

- Gateway chỉ lo giao thức/crypto; VnpayPaymentServiceImpl quản lý nghiệp vụ và transaction;
  controller điều phối HTTP; PaymentTransaction là audit, không lộ payload ở history.
- Dùng lại payments/payment_transactions trong schema nhóm, không sửa SQL nguồn.
- Không sửa SecurityConfig của TV1: filter chain riêng chỉ mở GET của hai callback chính xác,
  giữ CSRF và role trên Customer. Có test qua cả hai chain thật.
- Không sửa source inventory/delivery/shop-operation TV2/TV4. Gate ở DB sandbox bảo vệ transition
  từ PENDING sang CONFIRMED/PREPARING và các trạng thái xử lý khác. Cần nhóm review trigger và
  bổ sung thông báo lỗi thân thiện phía seller trước khi đưa lên DB chung.
- Checkout khóa SKU theo thứ tự ID trước khi tạo snapshot để tránh hai checkout cùng SKU tranh chấp
  qua trigger order_items. Trade-off: các checkout cùng SKU phải chờ nhau.
- Mặc định online tắt; scope này dành sandbox, chưa phải nghiệm thu production.
- Chưa có querydr/reconciliation tự động, refund API, Admin xử lý REVIEW hoặc cơ chế luân chuyển merchant key.
  Không tự giả lập hoàn tiền và không coi PROCESSING là doanh thu đã hoàn tất.
- Chưa nghiệm thu giao dịch trực tiếp bằng merchant của người dùng; các test callback dùng secret giả.

Nguồn giao thức chính thức: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
Hướng dẫn tunnel: https://ngrok.com/docs/getting-started/
