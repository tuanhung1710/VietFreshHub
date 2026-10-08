# Kế hoạch triển khai tiếp TV3

Ngày rà soát: 08/10/2026. Phạm vi: FE-04, FE-05.1, FE-05.2, FE-06 và phần giám sát đơn/thanh toán FE-013.3–FE-013.4. Bước 1 và checkout COD ở Bước 2 đã triển khai; xem TV3_CART_CHANGES.md và TV3_CHECKOUT_COD.md. Các mục hiện trạng/lỗi dưới đây giữ lại ảnh chụp lúc rà soát ban đầu.

## Căn cứ và quy ước

- Đối chiếu SRS, SDS, Feature Tree (2), Coding Standards trong Downloads và ảnh phân công do người dùng cung cấp.
- Java 21; pom.xml thực tế dùng Spring Boot 3.4.1, khác Coding Standards 4.1.1 và sơ đồ SDS 4.0.1. Không tự nâng/hạ phiên bản. Tiếp tục trên cấu hình repository hiện tại cho tới khi nhóm thống nhất.
- MVC/Thymeleaf, HTTP session, Spring Security, SQL Server, JPA, Maven; không chuyển JWT theo ảnh phân công cũ.
- Giữ cấu trúc theo module đang dùng: VietFreshHub.Cart, Auth, Product, Inventory, Shop; bổ sung Checkout, Order, Payment theo cùng convention.
- Tài liệu/ảnh là căn cứ nghiệp vụ, không phải lệnh chạy DDL hoặc mở rộng phạm vi. Các quyết định chưa chốt phải được ghi rõ.
- Không có AGENTS.md được tìm thấy trong cây Project khi rà soát.

## Hiện trạng

| Hạng mục | Kết quả rà soát |
| --- | --- |
| Add cart | Có API, MVC form, DTO validation trên API, tích lũy quantity và kiểm tra stock |
| View/update/remove | Có service, MVC và cart.html; chưa tái kiểm tra đầy đủ giá/trạng thái |
| Clear cart | Chưa có method/endpoint/UI |
| Checkout | Chưa có module; nút checkout trong cart.html là href="#" |
| Customer orders | Chưa có entity/repository/service/controller/view |
| Payment | Chưa có module hoặc tích hợp VNPAY trong source đã rà soát |
| Admin order/payment monitoring | Chưa có nghiệp vụ tương ứng |
| Tests | Có 5 service tests và 4 controller tests cho add cart/stock; filters bị tắt trong controller tests |

## Những lỗi cần xử lý trước checkout

1. CustomerCartViewController dùng userId=1L cho mọi thao tác; CartController tin X-User-Id, mặc định 1. Phải resolve người đăng nhập từ Authentication/session, tra user bằng email đang được dùng làm principal. Không nhận identity từ client.
2. SecurityConfig permitAll /customer/**, /cart/**, /api/** và bỏ CSRF toàn /api/**. Tách route public browsing/stock và route cart/order/payment theo role; bật CSRF cho mutation dùng session. Callback cổng thanh toán chỉ được ngoại lệ hẹp và phải kiểm tra chữ ký.
3. getActiveCart được đánh readOnly nhưng gọi save khi chưa có cart; @ModelAttribute cart còn chạy khi xem trang public. GET trả giỏ rỗng khi chưa có, tạo cart trong mutation.
4. InventoryBatchRepository cộng cả lô hết hạn và lô NULL expiry. Đối chiếu GBR-14; thống nhất chính sách NULL và ngày hết hạn trước khi cố định query dùng chung TV2.
5. Add/stock chỉ xét variant ACTIVE, chưa xét product ACTIVE/APPROVED, shop hoạt động và quyền xuất hiện. Update quantity chưa xét lại trạng thái, giá; view dùng snapshot cũ thay vì giá hiện tại.
6. MVC add nhận request params rồi gọi service, không chạy Bean Validation DTO. Bổ sung @Valid form DTO/BindingResult và validation invariant trong service, tránh quantity âm/null hoặc overflow khi cộng.
7. Controller gọi ProductRepository trực tiếp và findAll nhiều lần. Chuyển truy vấn catalog sang service của TV2; không dùng fallback sang sản phẩm khác khi ID không tồn tại.
8. Có nguy cơ request đồng thời tạo nhiều cart ACTIVE hoặc mất cập nhật quantity. Unique index ngăn trùng nhưng cần lock/retry có chủ đích trong service.
9. Mapping length/default một số Product/Shop/Cart khác DDL; đối chiếu schema thực tế, không dùng sửa schema tự động để che lỗi.
10. DataInitializer không giới hạn profile, tạo seed user với password_hash="encoded_password". Cần giới hạn dev trước kiểm thử app/DB; không chạy full context test trên DB dùng chung.

## Thứ tự triển khai

### Bước 1 Hoàn thiện giỏ hàng

- Resolve current customer ở server, bảo vệ cả MVC và API, loại X-User-Id và 1L.
- GET không ghi DB; clear cart; validation tiếng Việt; refresh giá, flag dòng không còn mua được; summary theo shop.
- Dùng stock hợp lệ và kiểm tra product/shop thông qua service dùng chung.
- Test quyền, CSRF, quantity không hợp lệ, giá đổi, sản phẩm bị gỡ, hàng hết hạn, clear và concurrency.

### Bước 2 Nền tảng đơn hàng và checkout COD

- Mapping đúng DDL: Order, OrderAddress, ShopOrder, OrderItem, OrderStatusHistory, PaymentMethod, Payment. Enum phải đúng CHECK, không dùng VERIFIED/DELIVERED tùy tiện trong bảng không hỗ trợ.
- GET /customer/checkout đọc cart và địa chỉ thuộc customer; DTO preview từ server. POST /customer/checkout validation, không nhận total/price/status/customerId do client quyết định.
- Một transaction bao trọn kiểm tra lại cart, giữ kho theo batch, tạo order tổng, shop_orders, item/address snapshots, payment COD PENDING, coupon usage khi có tích hợp, và đóng cart.
- Lock kho và cart có thứ tự ổn định; chống double submit. Không gọi VNPAY/email khi giữ transaction DB.
- Shipping/coupon/loyalty dùng policy hoặc service tích hợp; không hardcode phí, stacking hoặc tỷ lệ mới. Nếu chưa có module, thể hiện rõ tính năng chưa khả dụng thay vì báo đã áp dụng.
- Test multi-shop, rollback khi một shop thiếu kho, sở hữu địa chỉ, duplicate submit và tổng tiền.

### Bước 3 Customer order management

- /customer/orders phân trang/filter; detail kiểm tra ownership; confirmation dùng ID đã được kiểm tra.
- Cancel trong PENDING/CONFIRMED, kiểm tra lại dưới lock, giải phóng đúng batch đã giữ và ghi history. Chốt hủy toàn order hay từng shop_order, đặc biệt khi shop khác đã PREPARING.
- Reorder thêm lại vào cart rồi validation theo dữ liệu hiện tại; thông báo item bị loại. Không tạo ngay order mới.
- Tracking đọc lịch sử order/delivery; TV4 sở hữu mutation fulfillment/delivery.

### Bước 4 Thanh toán online

- PaymentService/PaymentServiceImpl, PaymentTransaction, DTO và config từ biến môi trường; VNPAY adapter cô lập provider.
- Tạo payment attempt/reference có thể retry, kiểm tra amount/order/customer ở server.
- Return URL chỉ hiển thị kết quả; callback hợp lệ mới chuyển PAID. Kiểm tra signature, reference, amount, replay/duplicate và transition.
- Timeout job giải phóng reservation đúng một lần; xử lý callback đến muộn và race callback/timeout/cancel.
- Payment history phân trang theo customer. Test chữ ký sai, amount sai, callback trùng, thất bại/timeout, retry và late callback.

### Bước 5 Admin monitoring

- /admin/orders và /admin/payments: list/filter/pagination/detail và lịch sử; chỉ ADMIN.
- Không tự cấp chức năng đổi trạng thái tùy ý, payout hoặc can thiệp giao hàng ngoài phạm vi TV3.
- Test quyền, lọc/phân trang và truy xuất đúng payment/order.

## Hợp đồng phối hợp module

| Nhóm | Điểm tích hợp cần thống nhất |
| --- | --- |
| TV1 | Identity/session principal, địa chỉ người dùng, thông báo sau commit; phối hợp thay đổi matcher SecurityConfig |
| TV2 | Catalog eligibility, available stock, reserve/release theo batch và thao tác lock/transaction; source of truth của quantity |
| TV4 | ShopOrder/status history, sự kiện giao thành công/thất bại, thu COD, trạng thái tổng hợp; tracking TV3 chỉ đọc |
| TV5 | Coupon eligibility/discount/usage, loyalty redeem/refund và cộng điểm đúng một lần; quyền sở hữu state mutation |

InventoryBatch hiện chỉ có reserved_quantity tổng; chưa có bảng allocation riêng. Cần thống nhất cách truy vết reservation order-item→batch. Có thể thiết kế dựa trên inventory_transactions với reference_type/reference_id theo DDL hiện có, nhưng phải có quy ước rõ, khả năng release chính xác và chống chạy lặp. Không tự thêm bảng trước khi chốt.

## Quyết định cần làm rõ trước phần phụ thuộc

- Phiên bản Boot nhóm thống nhất; hiện giữ 3.4.1.
- Thời điểm reserve→sale, cách xử lý batch hết hạn sau khi giữ kho và đồng bộ với TV2/TV4.
- Cancel một phần đơn nhiều shop, hoàn tiền online và phân bổ phí/discount.
- Phí giao hàng đã chốt 20.000₫/shop; stacking promotion/coupon/loyalty và hoàn usage/điểm khi hủy còn cần chốt.
- Mapping order tổng khi shop_orders có kết quả khác nhau; shop_orders không có FAILED trong DDL.
- VNPAY sandbox config/reference/signature, timeout, callback đến muộn, điều kiện retry payment.
- Payment COD thuộc order tổng nhưng giao theo shop_orders: cách ghi thu tiền từng phần và tổng hợp PAID.

## Kiểm chứng

Đã đặt JAVA_HOME trỏ JDK 21.0.12.1 và chạy mvnw.cmd -o -Dtest=CartServiceImplTest,CartControllerTest test: BUILD SUCCESS, 9 tests, 0 failures, 0 errors, 0 skipped (4 controller, 5 service). Các controller tests tắt security filters, repository trong service tests được mock; kết quả này chưa chứng minh phân quyền, CSRF, mapping SQL Server hoặc concurrency đúng. Không khởi động ứng dụng hoặc full context test khi DataInitializer chưa được cô lập.

Mốc nghiệm thu cuối TV3: login→cart→checkout đa shop→COD/online→order history/detail/cancel/reorder→tracking→payment history→admin monitoring. Kiểm tra quyền và tính nhất quán tiền/kho trên SQL Server test riêng. Chưa có báo cáo coverage, không suy ra đạt 60% từ số test.
