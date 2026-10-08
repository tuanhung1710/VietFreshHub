# Checkout COD TV3

Triển khai ngày 08/10/2026 theo FE-05.1 và phần tạo đơn COD của FE-06. Giữ Java 21, Boot 3.4.1, MVC/Thymeleaf, JPA và HTTP session. Không chạy migration hoặc đổi schema SQL Server.

## Luồng đã có

1. Chọn từng dòng hoặc Chọn tất cả sản phẩm khả dụng trong giỏ → `/customer/checkout?itemIds=...`.
2. Chọn địa chỉ của tài khoản, xem COD và tổng tiền theo từng shop.
3. POST xác nhận có CSRF và token review do server phát hành.
4. Kiểm tra lại tài khoản, địa chỉ, giỏ, giá, shop/sản phẩm và tồn kho.
5. Giữ kho theo batch, tạo order tổng/đơn shop/item/address snapshot/history/payment trong một transaction. Nếu mua một phần, chỉ loại dòng đã mua và giữ cart ACTIVE; nếu mua hết thì cart CHECKED_OUT.
6. Redirect trang xác nhận, sau đó xem chi tiết đơn thuộc tài khoản.

Phí giao hàng người dùng đã chốt: **20.000₫ mỗi shop**. Cấu hình `app.checkout.shipping-fee-per-shop`, có thể override bằng `CHECKOUT_SHIPPING_FEE_PER_SHOP`. Giá trị âm hoặc không khớp DECIMAL(18,2) bị từ chối; nếu không cấu hình qua adapter thì không xác nhận được đơn. Form không quyết định subtotal, shipping fee, grand total, customerId hoặc trạng thái payment.

Đơn nhiều shop chia một `shop_order` cho mỗi shop. Phí tổng bằng số shop × phí cấu hình; mỗi shop_order lưu phí riêng. Coupon/loyalty chưa được áp dụng khi chưa có service TV5; không nhận discount do client gửi.

COD lúc đặt đơn: `orders.status=PENDING`, `orders.payment_status=UNPAID`, `shop_orders.status=PENDING`, `payments.status=PENDING`, `paid_at=NULL`. Giao hàng/thu COD do TV4 cập nhật ở bước sau, không tự chuyển PAID khi checkout.

## Tính nhất quán và gửi trùng

- Preview không giữ kho, không tạo cart/order. Giá mới nhất được hiển thị để người mua xem lại.
- Draft lưu trong session: customerId/cartId, SHA-256 của item IDs, variant IDs, quantity, giá hiện tại và shop IDs, cùng phí giao mỗi shop. Form chỉ mang token ngẫu nhiên và lựa chọn địa chỉ/COD.
- Nếu giá/quantity/cart hoặc phí vừa thay đổi, yêu cầu xem lại và xác nhận lại. Không âm thầm tính tổng cao hơn giá đã review.
- Các dòng invalid/hết hạn/hết hàng/shop đóng được giữ trong giỏ nhưng không được chọn mua. Theo yêu cầu mới của người dùng, checkout chỉ kiểm tra và giữ kho cho các cart_item_id được chọn; dòng không chọn không làm lần mua này bị chặn.
- Selection được kiểm tra thuộc giỏ ACTIVE của tài khoản, rồi lưu bất biến trong draft server. POST không được tự thêm ID khác vào lựa chọn. Fingerprint chỉ xét các dòng đã chọn; phí chỉ tính cho shop được chọn.
- Checkbox Chọn tất cả có trạng thái chọn một phần; tổng tiền/ship cập nhật theo lựa chọn và giữ lựa chọn khi sửa quantity trong cùng tab. Badge header vẫn đếm toàn bộ giỏ.
- Khách chưa có địa chỉ có thể thêm ngay trong checkout bằng DTO validation tiếng Việt. Owner lấy từ session; địa chỉ đầu tiên tự làm mặc định, các địa chỉ thêm sau không đổi mặc định cũ. Lưu địa chỉ xong vẫn giữ các sản phẩm đã chọn.
- Token được dùng tạo `order_code=VF-<token>` phù hợp VARCHAR(50) và unique constraint hiện có. Lock user → tra order_code → tạo đơn đảm bảo hai POST cùng token cùng trả một order; không giữ kho hoặc tạo payment lần hai.
- Metadata được refresh và khóa khi xác nhận; batches khóa ghi theo thứ tự ổn định. Giữ kho cộng `reserved_quantity`, không giảm `quantity_on_hand` tại checkout.
- Sau khi lock các batches, chọn lô sellable có hạn sớm nhất trước (FEFO). Lô hết hạn/NULL hạn không được dùng. Đây là quy ước adapter hiện tại cần thống nhất với TV2 khi ghép.
- Nhật ký `inventory_transactions`: `transaction_type=RESERVE`, quantity dương, `reference_type=ORDER_ITEM`, reference_id là order_item_id, batch_id là lô được giữ. Một order item có thể có nhiều allocation rows.
- Nếu một shop thiếu kho hoặc thao tác persist lỗi, transaction rollback cả order/payment/history/reservation; cart vẫn ACTIVE. Event `OrderPlacedEvent` chỉ nên được consumer xử lý bằng `@TransactionalEventListener(AFTER_COMMIT)`.
- Snapshot địa chỉ, tên sản phẩm/variant/SKU và giá mua giữ thông tin tại thời điểm đặt; thay đổi catalog/address sau đó không sửa các snapshot.

## Phạm vi file và hợp đồng ghép nhóm

| Phần | Cách phối hợp |
| --- | --- |
| Checkout | Module TV3 mới: controller, DTO, draft store và service orchestration |
| Order/Payment | Entity/repository đúng bảng hiện có; TV4 dùng chung ShopOrder thay vì tạo model thứ hai |
| TV1 địa chỉ | CheckoutAddressRepository đọc entity Address hiện có; CheckoutAddressService tạo địa chỉ từ checkout, không sửa/xóa địa chỉ. Khi ghép, chuyển bridge sang service Address của TV1 |
| TV2 kho | InventoryReservationPort là điểm thay adapter; JpaInventoryReservationAdapter hiện làm transaction/lock/reserve bằng JPA |
| TV2 audit | CheckoutStockMovement là mapping cục bộ vào inventory_transactions; khi đã có InventoryService/InventoryTransaction chính thức, thay adapter, không tạo bảng mới |
| TV4 fulfillment | Cần giữ đúng reserve allocations trước khi chuyển sale/release. Không tự đánh dấu delivered, gán shipper hoặc thu COD |
| TV1 thông báo | OrderPlacedEvent phát trong transaction; consumer after-commit thực hiện inbox/email. Hiện chưa báo đã gửi email khi chưa có consumer |
| TV5 ưu đãi | Chưa áp dụng coupon/loyalty; tích hợp server pricing/usage trong transaction trước khi mở UI chức năng này |

Các file chung bổ sung tối thiểu: cấu hình phí, seed COD method, link nút checkout trong cart, safe GET return trong login và CSS storefront. Payment method COD chỉ thêm khi bật seed dev và chưa tồn tại; không sửa method đang có/đang INACTIVE.

## Kiểm chứng

Hồi quy cuối: BUILD SUCCESS, **76 tests**, 0 failures, 0 errors, 0 skipped; gồm các test cart/auth/seed trước đó và checkout mới. Chưa đo coverage.

- JPA integration trên H2 riêng: multi-shop, tiền/fee/COD pending, hai POST trùng, hai khách tranh sản phẩm cuối, rollback sau reservation shop đầu, giá/quantity đổi, foreign address/order, shop đóng, batch hết hạn, snapshot và FEFO allocation.
- MockMvc với security thật: login/role/CSRF, token lạ, bỏ qua totals/identity do client gửi, địa chỉ bắt buộc, giữ form khi lỗi và render checkout/receipt.
- SQL Server local: đã tạo **một đơn test order_id=1** bằng `empty.demo@vietfreshhub.example`; hai POST cùng token cùng redirect về đơn này. 2 shop, subtotal 223.000₫, shipping 40.000₫, total 263.000₫, order PENDING/UNPAID và payment PENDING/paid_at NULL.
- Đối soát trực tiếp SQL read-only: 2 shop_orders và 2 RESERVE rows giữ 2 bưởi + 1 xoài theo ORDER_ITEM. Tồn on-hand không bị trừ; giỏ customer.demo vẫn có 7 dòng và không bị sửa nội dung.
- UI checkout/receipt kiểm tra ở 360, 1440, 1920 px, không tràn ngang.

Đơn test được giữ trong DB với stock reservation để có thể tiếp tục thử fulfillment/cancel sau này. Giỏ `empty.demo` đã CHECKED_OUT; thêm sản phẩm mới sẽ tạo cart ACTIVE mới. Đơn xem tại `/customer/orders/1` khi đăng nhập đúng tài khoản; người khác nhận 404.

## Phần tiếp theo

Lịch sử đơn có phân trang, hủy đơn hợp lệ kèm release theo đúng allocation, tracking đọc TV4, reorder và VNPAY/payment lifecycle vẫn chưa triển khai. Chưa đo tải 50 người dùng hoặc coverage 60%; không suy ra các NFR này chỉ từ số test pass.
