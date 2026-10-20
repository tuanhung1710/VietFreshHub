# Task 07 — HTTP, phân quyền và tích hợp Customer

Ngày thực hiện: 20/10/2026. Phạm vi theo kế hoạch đã thống nhất: xác thực/role/tài khoản, ownership, CSRF và luồng giỏ → checkout COD → history/detail/tracking → reorder/cancel. Căn cứ SRS: quyền customer với dữ liệu của mình (GBR-05), UC-17, UC-21–24; không mở coupon/online hoặc sửa module thành viên khác.

## Cách kiểm tra

`src/test/java/VietFreshHub/customer/CustomerHttpHarness.java` dựng WebApplicationContext nhỏ, dùng **SecurityConfig, AccountSessionGuardFilter, CurrentCustomerService và bốn controller production thật**, cùng Spring MVC/Security filter chain. Không component-scan ứng dụng, không chạy scheduler, không đọc cấu hình kết nối DB nhóm.

- Security matrix dùng repository/service mock để kiểm tra request bị chặn trước nghiệp vụ.
- Các ca tích hợp SQL dùng User/UserRole repository thật, Cart/Checkout/Order lifecycle/read service và transaction thật trên DB test có marker. Customer mới được gán ROLE_CUSTOMER bằng fixture trong DB test.
- Authentication được đưa vào qua Spring Security test support; không kiểm thử mật khẩu/login/OIDC/provider thật. OAuth client giả chỉ đáp ứng dependency của cấu hình, không gọi mạng.
- ViewResolver no-op cho view thường, RedirectView thật cho redirect: nghiệm thu HTTP/filter/binding/model/DB, không giả vờ đã kiểm thử render toàn bộ UI bằng browser. Các template đã có test render riêng ở task trước.
- VietMap và staff provider ngoài đường chạy được stub; tạo/sửa địa chỉ mới không được mô phỏng là nghiệp vụ đã nghiệm thu trong task này.

## Ma trận security

`CustomerHttpSecurityTest` có 19 test (tính cả parameterized):

| Trường hợp | Kết quả yêu cầu |
| --- | --- |
| Anonymous GET cart/checkout/history/detail/tracking | Redirect /login, không gọi nghiệp vụ |
| ADMIN/STORE_MANAGER/DELIVERY_STAFF vào trang Customer | 403; POST với CSRF hợp lệ cũng không qua được role |
| Thiếu CSRF ở 7 endpoint thay đổi cart/checkout/cancel/reorder | 403, không gọi nghiệp vụ |
| CSRF không hợp lệ | 403 |
| CUSTOMER + CSRF hợp lệ, giả customerId trong form | Dùng ID từ tài khoản server, không dùng ID gửi lên |
| Session role CUSTOMER nhưng DB khóa tài khoản | Hủy session, redirect accountStatusChanged |
| Role trong DB đã thay đổi | Hủy session, redirect accountRolesChanged |

Lượt khởi tạo harness đầu có expectation URL tuyệt đối trong khi Security dùng redirect tương đối, và view no-op chưa thực hiện redirect. Đã sửa test/harness cho đúng MVC; không đổi SecurityConfig production để làm test qua.

## Ba ca HTTP + SQL Server

1. **Luồng customer:** POST thêm giỏ có CSRF và ID giả → GET giỏ → GET checkout tạo draft trong session → POST checkout COD → GET history/detail/confirmation/tracking → POST reorder → POST cancel. Kiểm tra giỏ mua lại, payment CANCELLED và kho trở về trước checkout.
2. **Đơn của customer khác:** detail/confirmation/tracking trả 404; POST cancel/reorder có CSRF và ID giả vẫn 404; lọc mã đơn không thấy dữ liệu; không sinh giỏ cho người lạ, không đổi đơn/kho.
3. **Địa chỉ/draft khác chủ:** không mở được form sửa địa chỉ khác chủ bằng token hợp lệ; không checkout bằng địa chỉ người khác; chuyển principal sang customer khác vẫn không dùng được draft đã lưu trong session. Không tạo đơn hoặc thay giữ kho.

Các ca này nằm trong `CheckoutSqlServerIntegrationTest` để tái sử dụng harness JPA, DB marker và fixture; script `scripts/tv3-sqlserver-tests.ps1` đã thêm security matrix vào bộ hồi quy.

Luồng HTTP mới phát cả event đặt và hủy đơn, làm lộ matcher Mockito cũ ép mọi event sang `OrderPlacedEvent`. Đã sửa hai matcher sang kiểm tra `instanceof` trước khi so customerId; vẫn giữ assertion không phát sự kiện đặt đơn cho transaction rollback. Đây là sửa test, không phải đổi publisher/notification production.

## Phần còn lại

Kết quả cuối ngày 20/10/2026: **179/179 PASS**, 0 failure/error/skipped trong một lượt Maven, gồm 19 security HTTP và 23 SQL integration (3 ca HTTP + SQL mới), cùng 137 test hồi quy còn lại. DB riêng: `VietFreshHub_TV3_Test_f79893fcdfbf4bafa555726540d43b7a`. Không cộng các lượt chạy lại. Task này chỉ thay đổi test/script/tài liệu, không phát hiện lỗi cần sửa production trong các ca đã nghiệm thu.

Đối soát DB cuối: 0 chênh lệch reserved/allocation, tồn vật lý/ledger, giữ kho/ledger và tổng Order/ShopOrder; 0 constraint test còn sót. DB nhóm không bị ghi dữ liệu.

- Chưa đo coverage, kiểm thử tải hoặc toàn bộ ứng dụng/module khác.
- Chưa nghiệm thu login/OIDC thật, email/notification thật, thanh toán online, thu/đối soát COD, pickup/fulfillment của TV4 hay tự tạo/sửa địa chỉ mới.
- Bước tiếp theo phù hợp: nghiệp vụ ghi nhận/đối soát COD và lịch sử thanh toán trong TV3; chốt bằng chứng thu tiền, idempotency và điểm gọi với TV4 trước khi gắn vào module giao hàng. Customer không được tự đánh dấu PAID.
