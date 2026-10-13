# Spring Boot MVC và Thymeleaf

Ngày chuyển đổi: 11/10/2026.

Ứng dụng dùng Spring Boot 3.4.1, Spring MVC, Thymeleaf và đăng nhập qua HTTP session. Controller trả tên template hoặc redirect; form POST có CSRF và dùng Post/Redirect/Get khi thao tác thành công. JavaScript hỗ trợ chọn sản phẩm, tính tổng dự kiến và thao tác form; không gọi REST API.

## Các endpoint REST đã được thay thế

| Endpoint cũ (đã bỏ) | Luồng MVC tương ứng |
| --- | --- |
| POST /api/carts/items | POST /cart/add, validation form và flash message |
| GET /api/carts/active | GET /cart, render customer/cart |
| GET /api/products/variants/{variantId}/stock | GET /product-detail/{variantId}, hiển thị tồn khả dụng và giới hạn số lượng |

Giao diện đã dùng các luồng MVC này trước chuyển đổi. Service/DTO nghiệp vụ vẫn được giữ, bao gồm kiểm tra tồn kho khi thêm/cập nhật giỏ và khi xác nhận checkout. Endpoint JSON cũ không còn được cung cấp.

## Phạm vi giữ nguyên

Đăng ký/đăng nhập, hồ sơ người bán và duyệt hồ sơ, catalog, giỏ hàng, checkout sản phẩm đã chọn, địa chỉ, COD, lịch sử/chi tiết/hủy/mua lại/tracking đơn hàng tiếp tục dùng các controller và template hiện có. Không thay đổi entity, repository, service nghiệp vụ, schema, giá, phí giao hàng, transaction, allocation giữ/trả kho hoặc token chống tạo đơn trùng.

GlobalExceptionHandler trả trang Thymeleaf kèm HTTP 400/404 thay cho JSON. Các controller có xử lý validation/flash riêng tiếp tục giữ nguyên hành vi. SecurityConfig giữ phân quyền và CSRF, chuyển người chưa đăng nhập tới /login cho toàn bộ giao diện.

## Kiểm chứng

Chạy Java 21 và Maven offline với các lớp AuthLoginFlowTest, CurrentCustomerServiceTest, CartControllerTest, CartPersistenceTest, CartServiceImplTest, DemoSeedTest, CheckoutControllerTest, CheckoutPersistenceTest. Các integration test giỏ/checkout dùng H2 riêng. Context test kết nối SQL Server mặc định không nằm trong bộ kiểm thử này.

Kết quả: BUILD SUCCESS, 92 tests, 0 failures, 0 errors, 0 skipped. Mockito được nạp trực tiếp bằng `-javaagent` do cơ chế tự attach bị kẹt trong môi trường Windows hiện tại. Đã clean để loại bỏ class REST cũ trong target. Chưa chạy lại kiểm thử trình duyệt hoặc SQL Server live cho lần chuyển đổi này.
