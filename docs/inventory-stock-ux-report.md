# Report: Đồng bộ tồn kho và UX khi nhiều khách cùng mua

Ngày: 26/10/2026.

## 1. Kết quả triển khai

- Product Detail cập nhật tồn kho gần thời gian thực bằng polling, không cần tải lại toàn trang.
- Chuyển variant cập nhật giá, ảnh, số lượng khả dụng, nút mua và URL tại chỗ.
- Variant hết hàng vẫn được hiển thị và xem được. Không tự chọn variant khác.
- Không tự giảm số lượng khách đã nhập khi tồn kho thay đổi.
  - Thêm giỏ qua AJAX có lỗi nghiệp vụ JSON; form POST cũ vẫn hoạt động khi tắt JavaScript.
- Checkout tiếp tục dùng reservation, transaction và token hiện có. Bổ sung đọc lại lô sau khóa và thông báo thiếu hàng cụ thể.
- Không đổi schema, enum, trạng thái order/payment; không thêm Redis, WebSocket hay reservation mới.

## 2. Nguồn dữ liệu tồn kho

Nguồn dùng để tính hàng có thể mua là bảng `inventory_batches`, thông qua `CheckoutInventoryBatchRepository` đang có.

Với mỗi variant:

`availableStock = tổng(quantityOnHand - reservedQuantity)`

Chỉ cộng các lô `ACTIVE` và chưa hết hạn theo ngày tại `Asia/Ho_Chi_Minh`. Lô không có ngày hết hạn được tính. Frontend không tính lại từ các lô và không biết đơn nào đang giữ hàng.

- `quantityOnHand`: lượng hàng thực tế trên lô.
- `reservedQuantity`: lượng đã giữ cho đơn.
- Số lượng trong cart: lựa chọn của khách, không phải reservation.
- Trang hiển thị không âm; việc quyết định đủ hàng vẫn nằm ở backend và transaction giữ kho.

## 3. Trước và sau

| Tình huống | Trước | Sau |
| --- | --- | --- |
| A mua khi B đang mở trang | B tiếp tục thấy số lượng cũ | Tab đang hiển thị cập nhật theo chu kỳ 5 giây, cộng thời gian xử lý request |
| Đổi variant | Tải lại trang | Cập nhật tại chỗ và kiểm tra server ngay |
| Variant hết hàng | Bị lọc bỏ, link có thể gây lỗi | Vẫn hiển thị, không cho mua |
| Số lượng đang chọn vượt kho | Chờ backend từ chối | Cảnh báo cạnh vùng mua, giữ số khách nhập và khóa nút thêm |
| Thêm giỏ bị thiếu hàng | Redirect và thông báo | AJAX trả 409, hiển thị tại chỗ và cập nhật số tồn vừa xác thực |
| Hết mạng khi thêm giỏ | Khó xác nhận kết quả | Thông báo kiểm tra giỏ trước khi thử lại; không tự gửi lại POST |
| Checkout thiếu hàng | Thông báo chung | Thông báo tên sản phẩm, variant, số yêu cầu và số còn lại |

Danh sách mua sắm vẫn áp dụng điều kiện có hàng hiện có. Chỉ trang chi tiết được tách điều kiện xem thông tin khỏi điều kiện mua. Sản phẩm chưa duyệt, shop không hợp lệ, danh mục bị ẩn hoặc variant `INACTIVE` không được công khai thêm qua API mới.

## 4. Luồng mở Product Detail

1. Controller hiện tại gọi `CatalogService.getProduct()` để lấy sản phẩm.
2. Service kiểm tra điều kiện công khai bằng `isCatalogEligible()`, không yêu cầu còn tồn kho mới được xem trang chi tiết.
3. Service đọc tồn khả dụng cho các variant từ repository hiện có.
4. DTO chứa variant `ACTIVE` và `OUT_OF_STOCK`, bao gồm giá, ảnh, tồn khả dụng, khả năng mua và thông báo tiếng Việt.
5. Thymeleaf dựng HTML ban đầu. Mỗi liên kết variant có dữ liệu hiển thị ban đầu và URL thật.
6. `product-stock.js` đọc dữ liệu HTML để dựng trạng thái trang, rồi gọi endpoint availability ngay.
7. Form vẫn dùng action `/cart/add`. Khi có JavaScript, sự kiện submit được xử lý bằng API; khi không có JavaScript, trình duyệt gửi POST thông thường.
8. Nếu khách dùng liên kết thật đúng lúc sản phẩm hoặc variant bị ngừng công khai, controller chuyển về `/home` cùng thông báo nghiệp vụ, không để lỗi không tìm thấy dữ liệu biến thành lỗi 500.

## 5. Luồng polling

Endpoint: `GET /api/catalog/products/{productId}/availability`.

1. Endpoint dùng lại `CatalogService.getProduct()` và điều kiện công khai hiện có.
2. Trả snapshot cho toàn bộ variant được công khai của sản phẩm, không gọi một request riêng cho từng variant.
3. Payload gồm `productId` và danh sách `variantId`, `variantName`, `price`, `imageUrl`, `availableStock`, `purchasable`, `availabilityMessage`.
4. Không trả SKU nội bộ, batch, khách hàng, allocation hay số lượng giữ theo từng đơn.
5. Header `Cache-Control: no-store` và fetch `cache: no-store` ngăn dùng lại snapshot cũ từ HTTP cache.
6. Khi tab đang hiển thị, lần tiếp theo được lên lịch 5 giây sau khi request hiện tại kết thúc. Không dùng interval tạo request chồng nhau.
7. Request quá 8 giây bị hủy. Lỗi mạng giữ dữ liệu đã có, hiện thông báo và tăng thời gian thử lại 10 giây, 20 giây, tối đa 30 giây.
8. Khi tab bị ẩn: hủy request GET và lịch polling. Khi quay lại: kiểm tra ngay. Khi khôi phục trang từ bfcache: kiểm tra lại.
9. Mỗi request có số thứ tự. Phản hồi cũ bị bỏ qua kể cả khi hủy request không có tác dụng.
10. Nếu sản phẩm không còn đủ điều kiện công khai: API trả 404 có mã `PRODUCT_UNAVAILABLE`, UI ngừng cho mua và giữ lựa chọn đang xem.

Đây là cập nhật gần thời gian thực, không phải cam kết rằng tồn kho sẽ giữ nguyên trong 5 giây. Khi mạng chậm, tab ẩn hoặc lỗi, dữ liệu có thể cũ lâu hơn.

## 6. Luồng chuyển variant

1. Khách bấm một liên kết variant thông thường: JavaScript chặn chuyển trang.
2. Lưu số lượng hiện tại theo mã variant cũ.
3. Đổi `selectedId`, khôi phục số lượng đã nhập cho variant mới; lần đầu xem variant đó dùng 1.
4. Cập nhật giá, ảnh, mã variant trong form, tồn kho, giới hạn số lượng và trạng thái các nút.
5. Dùng `history.pushState` đổi URL để có thể chia sẻ link. Nút Back/Forward phục hồi lựa chọn có trong snapshot; trường hợp không khôi phục được thì tải lại URL thật.
6. Hủy request GET cũ, kiểm tra tồn kho mới ngay và bỏ qua mọi phản hồi không còn phù hợp.
7. Ctrl/Cmd/Shift-click vẫn giữ hành vi mở liên kết thông thường.
8. Khi đang gửi thêm giỏ, khóa thay đổi lựa chọn trong trang để tránh nhầm variant của phản hồi POST.

## 7. Khi tồn kho giảm hoặc tăng

Ví dụ B chọn 3 hộp, tồn kho giảm từ 5 xuống 1:

1. Nhận snapshot mới và cập nhật dòng “Còn 1 sản phẩm”.
2. Giữ ô số lượng bằng 3, không âm thầm đổi thành 1.
3. Hiện cảnh báo cho biết khách đang chọn 3 và hiện chỉ còn 1.
4. Vô hiệu hóa nút thêm giỏ và nút tăng.
5. Khách tự nhập số hợp lệ hoặc bấm giảm. Khi đang vượt kho, một lần bấm giảm đưa về giới hạn hợp lệ.
6. Khi số lượng hợp lệ, nút thêm được mở lại.

Nếu tồn kho bằng 0: hiển thị “Tạm hết hàng”, giữ variant và số lượng, vô hiệu hóa hành động mua. Nếu hàng có lại, snapshot mới cho phép mua trở lại nếu trạng thái variant và shop cũng hợp lệ. Giá thay đổi được cập nhật và có thông báo riêng. Nội dung từ API được đặt bằng `textContent`, không chèn HTML.

## 8. Luồng thêm và cập nhật giỏ

Endpoint mới: `POST /api/cart/items`, nhận form URL-encoded và CSRF token.

1. Frontend kiểm tra dữ liệu nhập rồi chụp nội dung form, gồm variant, số lượng và CSRF.
2. Khóa nút và ô số lượng trong lúc gửi. Bấm gửi lặp lúc đang xử lý không tạo POST thứ hai.
3. Spring Security kiểm tra phiên, trạng thái tài khoản, vai trò `CUSTOMER` và CSRF như luồng hiện tại.
4. API lấy customerId từ phiên đăng nhập, không lấy từ form.
5. `CartService.addToCart()` khóa tài khoản, kiểm tra variant/shop, tìm hoặc tạo giỏ và dòng hàng.
6. Tổng cần kiểm tra là số đã có trong giỏ cộng số muốn thêm. Phép cộng dùng `long` trước khi so sánh với tồn kho.
7. Đủ hàng: lưu số lượng và giá hiện tại, trả thông báo cùng tổng số lượng giỏ để cập nhật badge.
8. Thiếu hàng: HTTP 409, mã `INSUFFICIENT_STOCK`, variantId, requestedQuantity, availableStock và message. Message cho biết tổng số lượng cần trong giỏ để tránh nhầm với số muốn thêm.
9. Không còn được bán: HTTP 409, mã `ITEM_UNAVAILABLE`. Sai form: HTTP 400, mã `INVALID_QUANTITY`.
10. Frontend giữ số khách nhập. Nếu nhận được số tồn từ lỗi 409, áp dụng ngay trước lần refresh GET tiếp theo, kể cả GET sau đó mất mạng.
11. Sau khi xử lý POST, kiểm tra snapshot mới. Mất kết nối hoặc timeout POST thì không tự gửi lại vì server có thể đã xử lý; khách được hướng dẫn kiểm tra giỏ trước.

Cart không có idempotency token mới cho những lần khách chủ động bấm thêm riêng biệt. Cơ chế chống đơn trùng vẫn thuộc checkout. Form cập nhật/xóa giỏ cũ được giữ; cập nhật số lượng dùng cùng kiểm tra backend và thông báo thiếu hàng cụ thể hơn.

## 9. Luồng checkout và tranh mua

1. Khách gửi token checkout và thông tin form đã chọn.
2. `CheckoutServiceImpl.confirm()` kiểm tra draft, khóa tài khoản và tìm đơn đã có theo token. Nếu đã tạo, trả đơn cũ.
3. Kiểm tra COD đang hoạt động, quyền sở hữu địa chỉ, giỏ, danh sách sản phẩm, giá/số lượng đã xem trước và báo giá giao hàng.
4. Khóa shop, sản phẩm và variant theo cơ chế hiện có; giữ thứ tự ID ổn định.
5. Tạo đơn tổng, đơn từng shop, các dòng hàng và snapshot trong cùng transaction.
6. `InventoryReservationService` sắp xếp yêu cầu theo variantId rồi orderItemId.
7. Lấy các lô hợp lệ theo batchId với `PESSIMISTIC_WRITE`.
8. Bổ sung `refresh(batch, PESSIMISTIC_WRITE)` sau khi có khóa: tránh dùng số lượng cũ của entity đã nằm trong persistence context trước khi phải chờ khóa.
9. Tính lại lượng khả dụng sau khóa. Nếu thiếu, ném `CheckoutStockException` chứa variantId, số yêu cầu, số còn và thông báo có tên sản phẩm/variant.
10. Nếu đủ, phân bổ theo lô hết hạn trước, tăng reservedQuantity, tạo allocation `RESERVED` và movement `RESERVE`.
11. Tạo payment COD chờ thu; cập nhật giỏ, giữ lại các món chưa chọn.
12. Flush, phát sự kiện theo luồng hiện có và commit transaction. Thông báo thành công chỉ được controller trả sau khi lời gọi transaction thành công.

Nếu bất kỳ bước nào thất bại, transaction phải rollback cả đơn, payment, allocation, movement, giữ kho và thay đổi giỏ. Không tự bỏ variant bị thiếu rồi tạo đơn cho phần còn lại.

Khi A và B tranh cùng hàng, yêu cầu đến sau phải kiểm tra lượng còn sau khi lấy được khóa. UI không phải cơ chế chống overselling; khóa và transaction backend mới là phần quyết định. Việc chứng minh hành vi này trên SQL Server còn phụ thuộc chạy bộ integration test thật ở mục kiểm thử.

## 10. UX sau lỗi checkout

1. Controller bắt lỗi nghiệp vụ sau khi transaction thất bại, đưa thông báo vào lỗi form.
2. Đọc lại đúng danh sách sản phẩm đã chọn từ cart, giữ địa chỉ đang chọn khi còn hợp lệ.
3. Tạo preview mới theo dữ liệu backend và tính lại báo giá nếu sản phẩm còn đủ điều kiện.
4. Dòng không thể mua hiện số khách chọn, số tồn khả dụng và link điều chỉnh/xóa trong giỏ.
5. Không tự xóa món, không tự giảm số lượng, không tự xác nhận lại đơn.
6. Nút đặt hàng bị khóa khi preview không đủ điều kiện. Khi khách thay đổi giỏ hoặc tổng tiền, cần xem lại và xác nhận theo draft mới.
7. Lỗi mất kết nối/commit không rõ kết quả vẫn dùng trang recovery và token cũ như trước, không coi đó là thất bại chắc chắn và không tự đặt lại.

## 11. COD, hủy đơn, xuất bán và payment

| Nghiệp vụ | Physical | Reserved | Available |
| --- | --- | --- | --- |
| Xem trang/thêm giỏ | Không đổi | Không đổi | Không đổi |
| Tạo đơn COD thành công | Không đổi | Tăng | Giảm |
| Hủy đơn đủ điều kiện còn RESERVED | Không đổi | Giảm đúng allocation | Tăng nếu lô vẫn hợp lệ |
| Nghiệp vụ xuất bán gọi `StockAllocationServiceImpl.sell()` | Giảm | Giảm tương ứng | Không trừ hai lần phần đã giữ |

Giữ nguyên cơ chế release hiện có: kiểm tra allocation và nhật ký, không tự release allocation `SOLD`, xử lý các allocation còn `RESERVED`. Các test hủy đồng thời, hủy lặp và rollback sau release tiếp tục nằm trong suite SQL Server.

COD `PENDING` là đang chờ thu tiền, không tự hết hạn chỉ vì trạng thái payment này. Không thêm scheduled job giải phóng hàng theo `PENDING`. Không thêm gateway, callback, timeout reservation hoặc quy trình hoàn tiền online trong thay đổi này vì phần checkout đang triển khai chỉ nhận COD. Không tự nhập lại hàng đã xuất bán khi refund.

## 12. Các file chính của thay đổi này

- `catalog/controller/ProductAvailabilityController.java`: GET snapshot, no-store, 404 nghiệp vụ.
- `catalog/dto/CatalogProductResponse.java`: thêm ảnh và thông báo cho từng variant.
- `catalog/service/impl/CatalogServiceImpl.java`: chi tiết vẫn xem được khi hết hàng; phân biệt xem và mua.
- `cart/controller/CartApiController.java`: AJAX thêm giỏ, lỗi JSON, badge.
- `cart/controller/CustomerCartViewController.java`: xử lý liên kết trang chi tiết vừa không còn khả dụng bằng redirect và thông báo.
- `cart/service/impl/CartServiceImpl.java`: message thiếu hàng có sản phẩm, variant và tổng cần trong giỏ.
- `checkout/exception/CheckoutStockException.java`: dữ liệu lỗi thiếu hàng tại checkout.
- `checkout/service/InventoryReservationService.java`: refresh sau khóa và lỗi thiếu hàng cụ thể.
- `checkout/service/impl/CheckoutServiceImpl.java`: message cụ thể khi sản phẩm/shop không còn nhận đơn.
- `security/SecurityConfig.java`: bảo vệ hai API mới bằng vai trò CUSTOMER.
- `templates/customer/product-detail.html`: vùng dữ liệu động, form fallback, cảnh báo và lựa chọn variant.
- `static/js/product-stock.js`: polling, chuyển variant, quản lý request, số lượng và AJAX thêm giỏ.
- `templates/customer/checkout.html`: số lượng yêu cầu/tồn khả dụng và link xử lý từng dòng lỗi.
- `templates/fragments/header.html`: thêm điểm cập nhật badge giỏ.

Các refactor checkout/cart từ các lượt trước vẫn được giữ trong workspace; danh sách trên mô tả riêng thay đổi đồng bộ tồn kho.

## 13. Kiểm thử và cách chạy lại

### Kết quả thực tế

| Bộ kiểm tra | Tổng | Đạt | Lỗi | Bỏ qua |
| --- | ---: | ---: | ---: | ---: |
| Toàn bộ Maven/JUnit | 375 | 348 | 0 | 27 |
| Chrome/Playwright, UI với API giả lập | 15 | 15 | 0 | 0 |

Maven báo `BUILD SUCCESS`. 27 test bị bỏ qua thuộc suite SQL Server chưa có cấu hình database test. `git diff --check` không phát hiện lỗi whitespace.

Log: `target/stock-ux-final-tests.log` và `target/stock-ux-browser-final.log`. HTML render phục vụ kiểm tra: `target/stock-ux/product-detail.html`; ảnh kiểm tra mobile: `target/stock-ux/product-detail-mobile.png`. Preview browser không tải đầy đủ CDN/font và dùng ảnh giả lập; ảnh này phục vụ kiểm tra vùng thao tác tồn kho, không dùng làm bằng chứng toàn bộ giao diện production.

### Backend

Chạy bằng Java 21 và Maven Wrapper:

```powershell
./mvnw.cmd test
```

Trong môi trường Windows sandbox của lượt chạy này, Mockito được nạp bằng tham số `-DargLine=-javaagent:<đường-dẫn-mockito-core.jar>` để tránh lỗi attach pipe. Không thay đổi cấu hình production hoặc pom chỉ để né lỗi môi trường này.

Bổ sung:

- `CatalogAvailabilityTest`: hết hàng vẫn xem được chi tiết, variant hết hàng vẫn chọn để xem được, 5→1→0→2, OUT_OF_STOCK không mua được, sản phẩm chưa duyệt không công khai.
- `ProductStockHttpTest`: role, CSRF, identity, dữ liệu công khai, no-store, 400/409/404, badge.
- `ProductStockViewTest`: render Thymeleaf thật, form có CSRF và xuất fixture cho trình duyệt.
- `InventoryReservationServiceTest`: kiểm tra đọc lại sau khóa và lỗi thiếu hàng có dữ liệu cụ thể. Đây là unit test, không dùng làm bằng chứng concurrency.
- `CheckoutSqlServerIntegrationTest`: bổ sung tranh mua 4 và 3 khi còn 5; chi tiết sau khi sản phẩm cuối đã được giữ. Giữ các test tranh sản phẩm cuối, retry, rollback và hủy đơn trước đó.

### Trình duyệt

Sau khi test render đã tạo `target/stock-ux/product-detail.html`, chạy:

```powershell
# NODE_PATH trỏ tới thư mục node_modules có Playwright nếu không cài tại dự án.
node --test src/test/js/product-stock.spec.cjs
```

Test dùng Chrome headless, HTML được render bởi Thymeleaf thật và các API giả lập có kiểm soát. Đây là bằng chứng hành vi frontend, không phải kiểm thử end-to-end với database production.

Các tình huống: tồn giảm dưới số đang chọn, hết hàng/có lại, đổi variant và Back, phản hồi cũ đến muộn kể cả bỏ qua abort, mất mạng, dừng ở tab ẩn, 409, chống gửi hai POST đang xử lý, không tự retry POST, sản phẩm không khả dụng, đổi giá, chèn tên an toàn, form không JavaScript, kích thước mobile, áp dụng 409 khi GET sau đó mất mạng, polling/backoff.

### Giới hạn xác minh SQL Server

Chưa có `TV3_TEST_JDBC_URL`, `TV3_TEST_SQL_USER`, `TV3_TEST_SQL_PASSWORD` cho database test riêng ở lượt chạy này. Suite chỉ chấp nhận database tạm có tên `VietFreshHub_TV3_Test_<32 ký tự hex>` và marker `TV3_TASK3_TEST`; không tự chạy các test ghi dữ liệu trên database ứng dụng.

Không thể kết luận “đã chứng minh không overselling trên SQL Server” khi các test này chưa chạy. Cần chạy suite trên database test đạt điều kiện trước khi coi phần concurrency/rollback thực tế đã được nghiệm thu.

## 14. Giới hạn vận hành

- Polling có độ trễ; khoảng 12 lần cập nhật/phút/tab đang hiển thị trong điều kiện bình thường, mỗi lần còn qua kiểm tra tài khoản/quyền hiện có.
- Không thêm cache hay nguồn tồn kho phụ. Nếu lượng truy cập tăng lớn, cần đo tải query trước khi đổi sang SSE hoặc cơ chế khác.
- Browser test cô lập không kiểm chứng latency thật, nhiều máy ứng dụng, toàn bộ giao diện CDN hoặc tải đồng thời lớn.
- UI có thể hiển thị thông tin cũ khi mạng lỗi; backend tiếp tục kiểm tra khi thêm giỏ và xác nhận đơn.
- Không hứa giữ hàng chỉ vì khách đang xem trang hoặc vừa thêm giỏ.
