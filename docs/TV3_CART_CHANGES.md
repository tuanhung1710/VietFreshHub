# Hoàn thiện nền tảng giỏ hàng TV3

Ngày: 08/10/2026. Giữ Java 21 và Spring Boot 3.4.1 theo pom.xml hiện tại. Không chạy DDL hoặc ghi vào SQL Server trong quá trình kiểm chứng.

## Hành vi đã thay đổi

- Người mua được xác định từ Authentication/session và email principal, kiểm tra tài khoản ACTIVE; loại userId=1 và header X-User-Id. API cart/order dành cho CUSTOMER; duyệt sản phẩm và stock GET vẫn công khai.
- Bật CSRF cho tất cả mutation. API chưa đăng nhập trả 401, trang HTML chuyển login; tài khoản sai role bị từ chối. Login/registration đổi session ID; login quay lại trang sản phẩm hoặc giỏ với đích được giới hạn để tránh open redirect.
- GET giỏ không tạo dữ liệu. Add/update/remove/clear khóa user bằng PESSIMISTIC_WRITE trong transaction để tuần tự hóa việc tạo giỏ đầu tiên và cập nhật số lượng của cùng người dùng. Việc giữ kho thực sự nằm ở bước checkout chưa triển khai.
- Add/update kiểm tra variant ACTIVE, product ACTIVE/APPROVED, shop ACTIVE/OPEN và seller application APPROVED. Shop đóng vẫn xem được nhưng không mua được; sản phẩm không tồn tại trả HTML 404 thay vì thay thế bằng sản phẩm khác.
- Available stock chỉ lấy các lô có expiry_date và chưa qua hạn theo ngày Việt Nam (Asia/Ho_Chi_Minh), trừ reserved_quantity. Ngày hết hạn vẫn bán được trong ngày đó. SUM dùng bigint; DTO badge dùng long; cộng quantity kiểm tra bằng long trước khi ghi int.
- Giỏ hiển thị giá hiện tại, thông báo khi khác snapshot; đọc giỏ không cập nhật snapshot. Add/update thành công cập nhật snapshot. Hàng không mua được vẫn giữ trong giỏ, có lý do, không tính vào tạm tính.
- Clear cart có bước xác nhận trong giao diện. Số lượng có +/- và nhập trực tiếp tự submit, min=0 để xóa; khi stock giảm, nút giảm đưa về lượng đang có thể mua. Có form cập nhật khi tắt JavaScript. Mọi thay đổi dùng POST/Redirect/Get.
- Nhóm theo shopId, không theo tên shop; có thông báo thành công/lỗi, labels cho assistive technology, focus visible, vùng bấm 44 px và responsive layout.
- Home/product/cart dùng fragment và CSS/JS local chung; thay danh sách và giá mẫu bằng DTO dữ liệu thật. Home phân trang 12 sản phẩm tại DB với thứ tự ổn định; variants fetch theo batch, stock của danh sách/giỏ được truy vấn gộp.
- Checkout hiện chưa triển khai: nút disabled với giải thích rõ thay vì href="#". Không giả lập xác nhận đơn/thanh toán.
- Mapping length/default/nullability ở Cart/Product/Variant/Shop đã đối chiếu DDL được gửi; sản phẩm mới mặc định approval PENDING. Không sửa schema DB tự động.
- Seed chỉ chạy khi profile dev và app.seed.enabled=true; yêu cầu tài khoản mẫu đã đăng ký hợp lệ, không tạo password_hash giả. Không tạo lại batch chỉ vì batch cũ hết hạn.

## Kiểm chứng

Chạy JDK 21 và Maven wrapper offline:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
.\mvnw.cmd -o '-Dtest=CartServiceImplTest,CartControllerTest,CartPersistenceTest,CurrentCustomerServiceTest,AuthLoginFlowTest' test
```

Test HTTP bật security filters thật; mock business services. Test repository dùng H2 trong bộ nhớ với cấu hình datasource riêng, tạo/xóa schema test, không dùng SQL Server. Kiểm tra expiry/reservation, phân trang, shop đóng, orphan removal, hai request thêm đồng thời và arithmetic lớn. Test login kiểm tra đổi session ID và chặn URL quay lại bên ngoài.

Kết quả cuối: BUILD SUCCESS, 45 tests, 0 failures, 0 errors, 0 skipped. Chưa đo coverage.

Render bằng Thymeleaf trong MockMvc, sau đó kiểm tra HTML bằng Edge headless ở 360, 1440, 1920 px: không tràn ngang; kiểm tra thực tế các request tăng số lượng, nhập trực tiếp, về 0 và xử lý thiếu kho, kèm CSRF. Fixture/render/log nằm trong target hoặc .document-analysis (đã ignore).

## Giới hạn và phối hợp

- Chưa chạy ứng dụng với SQL Server thật hoặc đo tải 50 người dùng/coverage 60%. H2 kiểm chứng hành vi JPA, không thay thế kiểm tra khóa/ràng buộc trên SQL Server. CHECK/unique constraints của DDL vẫn phải có trong DB dùng chung.
- Dữ liệu legacy shop.application_id=NULL, application chưa APPROVED, product chưa APPROVED hoặc batch không có hạn sẽ không được phép bán. Không tự sửa dữ liệu để vượt điều kiện; cần TV1/TV2 hoàn thiện dữ liệu shop/catalog phù hợp. Luồng duyệt hiện có của TV1 chưa tự tạo shop trong source đã kiểm tra.
- Quy tắc ngày hết hạn kể trên là cách triển khai hiện tại của GBR-14; cần giữ thống nhất với InventoryService/checkout sau này.
- Product.getImageUrl hiện vẫn dùng fallback ảnh local từ module Product cũ; quản lý ảnh persisted thuộc TV2 chưa được thay thế trong task này.
- Tiếp theo: checkout và reservation theo batch, order tổng/đơn shop, COD, order management, VNPAY/callback/timeout rồi Admin monitoring, theo TV3_IMPLEMENTATION_PLAN.md.

## Tham khảo UX

- [Baymard — quantity controls](https://baymard.com/research-articles/auto-update-users-quantity-changes): nút +/- kết hợp nhập trực tiếp, cập nhật dễ hiểu và hỗ trợ quantity 0.
- [Shopify — checkout](https://help.shopify.com/en/manual/checkout-settings): kiểm tra inventory khi checkout; thêm vào giỏ không phải bảo đảm giữ hàng.

Đây là các hành vi UX được tham khảo, không phải chứng nhận rằng dự án đã đạt toàn bộ tiêu chuẩn của một nền tảng thương mại điện tử.
