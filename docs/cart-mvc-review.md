# Review chuyển thao tác thêm giỏ về Spring MVC

Ngày: 28/10/2026.

## 1. Kết quả

Thao tác thêm giỏ dùng lại form `POST /cart/add` và Post/Redirect/Get. Đã xóa `CartApiController` và mapping `POST /api/cart/items`.

Chỉ giữ endpoint đọc `GET /api/catalog/products/{productId}/availability` để cập nhật tồn kho trên trang đang mở. JavaScript tiếp tục đổi variant tại chỗ, giữ số lượng theo từng variant, cập nhật tình trạng hàng và polling. Không gửi request AJAX để thay đổi giỏ nữa.

Không sửa CartServiceImpl, reservation, khóa kho, điều kiện mua, cách tính giá, transaction checkout hoặc trạng thái order/payment trong thay đổi này. Tám file clean code đang có từ lượt trước được đối chiếu nội dung trước/sau và giữ nguyên.

## 2. Luồng sau khi sửa

### Bước 1 — Mở trang và chọn sản phẩm

1. Controller đọc sản phẩm, variant và giỏ hiện tại.
2. Thymeleaf render ảnh, giá, tồn kho, badge giỏ và form có CSRF.
3. JavaScript đọc dữ liệu HTML ban đầu và gọi GET availability.
4. Khách đổi variant: cập nhật ảnh, giá, URL, mã variant trong form và số lượng đã chọn; không reload ở bước này.

### Bước 2 — Nhấn “Thêm vào giỏ hàng”

1. Trình duyệt kiểm tra form. JavaScript kiểm tra thêm trạng thái nút và việc form có đang gửi hay không.
2. Khóa nút thêm và lựa chọn variant, dừng request polling đang chạy.
3. Ô số lượng chỉ chuyển sang `readOnly`, không dùng `disabled`: input disabled sẽ không được trình duyệt gửi lên server.
4. Không gọi preventDefault cho lần gửi hợp lệ. Trình duyệt gửi POST form thông thường, gồm variantId, quantity, redirect=detail và CSRF.
5. Lần gửi lặp trong cùng trang khi đang xử lý bị chặn. Đây là bảo vệ giao diện, không phải idempotency cho các POST riêng biệt từ nhiều tab hoặc retry thủ công.

### Bước 3 — Backend xử lý

1. Spring Security tiếp tục kiểm tra phiên, tài khoản, vai trò CUSTOMER và CSRF.
2. `CustomerCartViewController.handleAddToCart()` dùng mã khách từ phiên đăng nhập.
3. Controller gọi `CartService.addToCart()` như trước.
4. Service kiểm tra sản phẩm/shop, tổng số lượng cần trong giỏ và tồn khả dụng; lưu giỏ nếu hợp lệ.
5. Không giữ hoặc trừ kho tại bước thêm giỏ. Reservation vẫn thuộc checkout.

### Bước 4 — Thành công

1. Controller đặt successMessage vào RedirectAttributes.
2. Redirect về `/product-detail/{variantId}` đã gửi trong form.
3. GET mới đọc lại giỏ, sản phẩm và tồn kho; Thymeleaf hiển thị badge và thông báo mới.
4. Sau redirect, refresh trang chỉ thực hiện GET, không thêm lại sản phẩm.

### Bước 5 — Thiếu hàng hoặc sai dữ liệu

1. Lỗi nghiệp vụ được controller xử lý bằng flash message, không phải JSON 409.
2. Với số lượng hợp lệ về kiểu dữ liệu nhưng vượt kho, giữ requestedQuantity và redirect về đúng variant.
3. GET mới render tồn kho hiện tại và số khách đã yêu cầu. Ví dụ khách chọn 3, hiện còn 1: vẫn hiển thị 3, cảnh báo và khóa nút thêm cho tới khi khách điều chỉnh.
4. Sai kiểu dữ liệu được trả bằng thông báo validation tiếng Việt. Số nhập không chuyển được thành Integer không được coi là số lượng hợp lệ.
5. Nếu sản phẩm/variant vừa không còn công khai, handler hiện có chuyển về trang mua sắm với thông báo nghiệp vụ.

### Bước 6 — Quay lại trang bằng Back/Forward

Nếu trình duyệt khôi phục trang từ bfcache, sự kiện pageshow với persisted=true xóa trạng thái đang gửi, mở lại input/nút theo điều kiện hiện tại và cập nhật tồn kho ngay. Tránh việc form bị khóa mãi sau khi khách quay lại.

## 3. Polling giữ nguyên

- Một GET cho snapshot các variant của sản phẩm.
- Chu kỳ bình thường: 5 giây sau khi request trước hoàn tất.
- Tab ẩn: dừng; quay lại tab: kiểm tra ngay.
- Hủy request không còn phù hợp và bỏ qua phản hồi cũ.
- GET lỗi mạng: giữ nội dung đang xem, thông báo và giãn thời gian thử lại.
- Không tự đổi variant hoặc âm thầm sửa số lượng.
- Frontend không cam kết giữ hàng. Backend vẫn là nơi xác nhận cuối cùng.

## 4. Kết quả review

| Nội dung kiểm tra | Kết quả |
| --- | --- |
| Còn POST AJAX thêm giỏ trong JavaScript không? | Không. fetch chỉ dùng cho GET tồn kho. |
| Endpoint POST cũ còn handler hoặc class build cũ không? | Đã xóa controller và clean build; kiểm thử POST cũ trả 404. |
| Quantity và CSRF có bị mất do khóa form không? | Không; quantity dùng readOnly, CSRF và hidden variant vẫn được gửi. |
| Có gửi nhầm variant sau khi đổi lựa chọn không? | Test xác nhận chọn variant 12 gửi ID 12 và quay lại URL của variant 12. |
| Thiếu hàng có mất số lượng hoặc lựa chọn không? | Test xác nhận giữ variant và quantity=3 khi tồn mới là 1. |
| Badge có còn cần JavaScript cập nhật không? | Không; badge được render lại từ giỏ trên GET sau redirect. |
| Refresh sau redirect có gửi lại POST không? | Test xác nhận không. |
| Quay lại trang có bị giữ trạng thái đang gửi không? | Đã xử lý khôi phục bfcache và có test. |
| Các thay đổi clean code trước đó có bị ghi đè không? | Không; so sánh nguyên nội dung 8 file trước/sau đều khớp. |
| Có sửa dòng gốc của tác giả khác không? | Đối chiếu git blame và các dòng bị xóa/thay thế: không phát hiện. |

Không phát hiện lỗi chức năng còn mở trong phạm vi kiểm thử đã chạy. Không thay đổi nghiệp vụ để chuyển cách gửi form.

## 5. Phạm vi file

- Xóa `src/main/java/VietFreshHub/cart/controller/CartApiController.java`.
- `product-stock.js`: bỏ fetch POST, xử lý JSON thêm giỏ và cập nhật badge bằng JS; giữ submit guard cho form thường và phục hồi bfcache.
- `product-detail.html`: bỏ data-api-url; thông báo giá dùng data-price-notice thay cho vùng feedback AJAX.
- `header.html`: bỏ thuộc tính data-cart-badge không còn được dùng; cách hiển thị badge Thymeleaf giữ nguyên.
- `SecurityConfig.java`: chỉ bỏ đường dẫn API POST đã xóa khỏi matcher cũ; giữ bảo vệ /cart/**, GET tồn kho và CSRF.
- `CustomerHttpHarness`: bỏ đăng ký controller API đã xóa.
- `ProductStockHttpTest`: chuyển kiểm thử lỗi/thành công thêm giỏ sang form MVC, flash, redirect, refresh và kiểm tra endpoint cũ đã ngừng hoạt động.
- `ProductStockViewTest`: kiểm tra form MVC, CSRF; render fixture thành công, thiếu hàng và variant khác bằng Thymeleaf thật.
- `product-stock.spec.cjs`: kiểm thử trình duyệt theo form POST và redirect, giữ các kiểm tra tồn kho trước đó.

## 6. Kiểm thử và giới hạn

- Kiểm thử tập trung backend: 93 test đạt, không lỗi.
- Chrome/Playwright: 16 kịch bản đạt, không lỗi; dùng HTML Thymeleaf render thật và response server giả lập để kiểm soát race/network.
- Đã chạy clean trước kiểm thử để loại class CartApiController cũ trong target/classes. Không chỉ xóa file Java rồi để bytecode cũ tồn tại.
- Các kiểm tra HTTP dùng security thật và service mock; không coi đây là bằng chứng concurrency SQL Server.
- Toàn bộ Maven suite: **377 test, 350 đạt, 0 lỗi, 27 bỏ qua**; `BUILD SUCCESS`.
- 27 test bỏ qua thuộc suite SQL Server vì chưa có cấu hình database test riêng. Không kết luận concurrency SQL Server đã được kiểm chứng từ các test mock/browser.
- `git diff --check` không phát hiện lỗi whitespace.

Log và audit nằm trong `.document-analysis/mvc-review/`:

- `targeted-tests.log`: kiểm thử tập trung sau clean build.
- `full-tests.log`: toàn bộ suite.
- `browser-final.log`: kiểm thử trình duyệt.
- `ownership-audit.json`: đối chiếu tác giả và giữ nguyên các thay đổi trước.

Đổi lại của form MVC là tải lại trang sau khi thêm giỏ. Khi POST mất kết nối hoặc kết quả chưa rõ, trình duyệt dùng cơ chế lỗi điều hướng thông thường; không có thông báo AJAX tại chỗ và ứng dụng không tự gửi lại POST. Khách nên kiểm tra giỏ trước khi thử lại. Đây không phải bảo đảm idempotency cho thao tác thêm giỏ.

Checkout và các test SQL Server chưa có database test cấu hình riêng vẫn giữ giới hạn xác minh đã nêu trước đó.
