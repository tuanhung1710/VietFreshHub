# Cập nhật điều kiện hủy theo nghiệp vụ nhóm

Ngày 2026-10-22. Xác nhận mới của người dùng: khách được hủy khi mọi ShopOrder còn PENDING/CONFIRMED; từ PREPARING trở đi không được tự hủy. Quyết định này thay thế các mô tả chặn hủy do payment/discount trong báo cáo Phases 1–6 trước đó; các báo cáo cũ là lịch sử rà soát, không phải chính sách hiện hành.

## Thay đổi

- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderServiceImpl.java`: dùng chung cancellationHint cho actions(), historySummaries() và cancel(). Bỏ chặn do non-COD, PAID/processing/refund state, discounts hoặc parent aggregate có thể chậm/lệch. Mọi shop phải PENDING/CONFIRMED; có shop PREPARING/READY/OUT/COMPLETED hoặc đã hủy riêng thì không hủy toàn bộ. Đơn đã hủy gọi lại không nhả kho lần hai.
- Giữ ownership, account ACTIVE, lý do tối đa 500 ký tự, khóa/transaction và guard delivery đã lấy/giao hàng để tránh hủy dữ liệu mâu thuẫn dù shop status cũ. Stock release vẫn kiểm tra allocation/audit/SOLD như trước, không sửa lifecycle kho.
- Khi hủy: Order/ShopOrder chuyển CANCELLED và nhả phần giữ kho trong transaction hiện có. Giữ giá trị tiền/giảm giá lịch sử.
- Chỉ Payment PENDING hoặc FAILED chưa có paidAt được chuyển CANCELLED. PROCESSING, PAID, REFUNDED, PARTIALLY_REFUNDED và bằng chứng paidAt được giữ nguyên. Order.paymentStatus không bị tự đổi thành REFUNDED. Hủy fulfillment không đồng nghĩa hủy giao dịch gateway hoặc hoàn tiền thành công.
- Online callbacks/cancel-provider/refund và đối soát khoản đang xử lý hoặc đã thu cần chủ payment/gateway xử lý theo hợp đồng riêng; không viết gateway/refund giả trong thay đổi này. Coupon usage nếu module khác triển khai cũng cần hợp đồng xử lý riêng, không tự sửa monetary snapshots.

## Kiểm thử

Cập nhật `src/test/java/VietFreshHub/order/CustomerOrderCancellationTest.java`: cho hủy pending online, paid nhưng chưa chuẩn bị, đơn có discount, parent stale; giữ bằng chứng payment qua mọi enum status và paidAt; mixed PENDING/CONFIRMED cho hủy, chỉ cần một PREPARING thì chặn. Giữ tests ownership/account/CSRF/repeated cancellation và release.

Full Maven: **361 tests, 336 passed, 0 failures/errors, 25 SQL Server skipped (chưa có DB test biệt lập), BUILD SUCCESS**. `git diff --check` đạt. Log: `.document-analysis/cancellation-policy-full.log`.

Không sửa schema, template/CSS, gateway, thu COD hoặc thuật toán giữ/nhả kho. Không commit/push.
