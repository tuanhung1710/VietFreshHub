# Review và hoàn thiện danh sách và chi tiết đơn hàng khách hàng

Ngày kiểm chứng: 13/10/2026. Phạm vi TV3: phần đọc của FE-05.2, gồm lịch sử/danh sách và chi tiết/xác nhận đơn. Không thay đổi nghiệp vụ cancel, release kho, tracking, reorder hoặc VNPAY.

## Căn cứ thực tế

Đã đọc Controller → Service → Repository → Entity/DTO → template, kiểm tra `pom.xml`, coding standards trong Downloads, trích xuất lại SRS/SDS từ DOCX nguồn và đối chiếu SQL mới của nhóm. Không tìm thấy AGENTS.md trong project hoặc các thư mục cha áp dụng.

- SRS `SE2056-JV - Group 1 - SRS.docx`: UC-21, UC-24, GBR-05, màn hình Order List/Order Detail. UC-21 yêu cầu khách sở hữu đơn, đọc sản phẩm/giá/trạng thái và không thay đổi dữ liệu; UC-24 chỉ lấy đơn của khách hiện tại và xử lý danh sách rỗng.
- SDS: lớp controller/service/repository, DTO và mapping các bảng order, shop order, item, address, payment.
- `VietFreshHub_Coding_Standards.md`: phân trang tại DB, service giữ nghiệp vụ/quyền sở hữu, projection/fetch plan để tránh N+1, validation/Thymeleaf và UI 360–1920 px.
- `D:/S5/SWP391/Database/VietFreshHub_Schema.sql`: snapshot, DECIMAL(18,2), DATETIME2 UTC và CHECK trạng thái order/shop/payment. Hash vẫn là `268150776c87638e2986bc84092231b2b2bbc9be31eab50eaf734f96e83bc938`.

Coding standards ghi Boot 4.1.1, nhưng pom và ứng dụng chạy thực tế là Boot **3.4.1 / Java 21**. Theo yêu cầu giữ phiên bản hiện tại, không sửa pom/dependency. Không dùng báo cáo PASS trước đó làm bằng chứng nghiệm thu lần này.

## Phần đã đúng trước khi sửa

- `OrderRepository.findHistory` trả Page<OrderListRow> bằng projection, điều kiện owner trong query; không tải cả bảng rồi phân trang bằng Java.
- Customer ID được lấy bằng `CurrentCustomerService.requireCustomerId(Authentication)` của lớp xác thực hiện có, không lấy từ request.
- Query service kiểm tra `orderId + customerId` trước khi đọc chi tiết; đơn không thuộc khách và đơn không tồn tại cùng trả 404.
- Khoảng ngày bình thường được đổi từ ngày Việt Nam sang UTC, dùng đầu ngày inclusive và đầu ngày tiếp theo exclusive.
- Tên sản phẩm, tên phân loại, SKU, giá, lượng, tiền dòng và địa chỉ lấy từ snapshot; không dùng giá catalog hiện tại để tính lại đơn cũ.
- Enum order/shop/payment khớp CHECK schema. COD tạo đơn vẫn UNPAID/PENDING, không tự thu tiền.

## Lỗi và bằng chứng đã sửa

| Vấn đề | Bằng chứng trong code trước task | Kết quả |
| --- | --- | --- |
| Bộ lọc sai nhưng hiển thị dữ liệu khác | Controller gọi `orders.history(customerId, errors.hasErrors() ? new OrderFilterRequest() : filter)` | Không truy vấn danh sách khi binding/validation sai; giữ form, thông báo tiếng Việt; không hiển thị kết quả không khớp bộ lọc |
| Trang ngoài phạm vi gây hiểu nhầm không có đơn | Service trả ngay query với PageRequest của client, không xử lý tổng trang | Trang vượt tổng trang hiển thị trang cuối bằng query DB và thông báo; tập rỗng chuẩn hóa về trang đầu; page <=0, >10000 hoặc không phải số là input sai |
| Ngày ngoài miền DB có thể gây lỗi khi tính/bind | DTO không giới hạn năm; service gọi `toDate.plusDays(1)` trực tiếp | Validate năm 0001–9999, xử lý cận UTC năm 0001; ngày kết thúc vẫn inclusive theo Việt Nam |
| Thanh toán bị che mất khi đơn hủy | Template dùng `order.statusLabel == 'Đã hủy' ? 'Đơn đã hủy' : order.paymentStatusLabel` | Luôn hiển thị payment_status thực tế độc lập order.status; refund/paid/failed không bị che |
| Mặc định COD và bỏ qua các payment record | Template fallback COD; phương thức lấy từ actions, thiếu thông tin từng payment | Đọc tất cả payment record có owner filter, hiển thị phương thức/status/amount/paid_at, mới nhất trước; không có dữ liệu thì nói rõ chưa có bản ghi |
| Thiếu thông tin từng shop/dòng | DTO/template chỉ thể hiện phí ship và tiền dòng, SKU có trong DTO nhưng không render | Hiển thị mã đơn shop, subtotal/discount/ship/total của shop, SKU và discount dòng từ DB |
| Mất phần thập phân | Hai template dùng formatInteger cho tiền DECIMAL | Hiển thị chính xác tối đa 2 chữ số thập phân, bỏ `,00` khi tiền nguyên; không làm tròn số tiền đã lưu |

Phân trang giữ keyword, trạng thái và ngày ở URL; ngày URL được định dạng ISO tường minh. Các trạng thái rỗng, lỗi filter và trang được chuyển về phạm vi hợp lệ có thông báo riêng.

## Thiết kế truy vấn và hợp đồng tích hợp

`CustomerOrderReadRepository` dành riêng cho màn hình đọc. Sau kiểm tra owner, đọc item/shop/payment bằng projection; không đi qua Product.getImageUrl hoặc tải đồ thị catalog để render đơn. Query DTO không tạo dữ liệu hoặc sửa state.

Đo bằng Hibernate statistics với persistence context đã clear:

- Chi tiết đơn 1 shop/1 item: **5 query**.
- Chi tiết đơn 12 shop/36 item: **5 query**.
- Badge giỏ: thêm **1 query SUM(BIGINT)**, chỉ giỏ ACTIVE của khách; không gọi CartService để tải cả giỏ cho badge. Kết quả thử 2.147.483.649 đơn vị không overflow Integer.

Đây là số query của query service/badge, không phải tổng query của toàn HTTP request. Xác thực và `lifecycle.actions` còn phục vụ quyền truy cập/nút thao tác. Fetch plan và logic của lifecycle được giữ nguyên.

Không thay đổi file TV1, SecurityConfig, CartService, CartRepository, schema, datasource hoặc pom. Tái sử dụng `cartBadgeTotal` đã được hỗ trợ bởi fragment storefront, nên không cần sửa fragment dùng chung. Hai trang dùng head fragment/CSS riêng của TV3 nhưng giữ header/footer, CSS và JS storefront.

DTO chi tiết bổ sung shop subtotal/discount, item discount và danh sách payment. Giữ constructor cũ để checkout/confirmation và các consumer hiện có không bị vỡ source contract. `CustomerOrderQueryService` thêm hàm đếm badge chỉ đọc. Các nút và endpoint cancel/tracking/reorder vẫn gọi luồng cũ.

## File thay đổi riêng trong task

Các thay đổi chưa commit từ các task trước được giữ nguyên. Danh sách dưới đây chỉ là 18 file được sửa/thêm cho task này.

| Nhóm | File |
| --- | --- |
| Controller | `src/main/java/VietFreshHub/Order/controller/CustomerOrderController.java` |
| Controller | `src/main/java/VietFreshHub/Order/controller/CustomerOrderDetailsController.java` |
| DTO | `src/main/java/VietFreshHub/Order/dto/OrderFilterRequest.java` |
| DTO | `src/main/java/VietFreshHub/Order/dto/OrderListRow.java` |
| DTO | `src/main/java/VietFreshHub/Order/dto/OrderDetailsResponse.java` |
| Service interface | `src/main/java/VietFreshHub/Order/service/CustomerOrderQueryService.java` |
| Service implementation | `src/main/java/VietFreshHub/Order/service/impl/CustomerOrderQueryServiceImpl.java` |
| Service implementation | `src/main/java/VietFreshHub/Order/service/impl/CustomerOrderServiceImpl.java` — chỉ sửa history |
| Repository mới | `src/main/java/VietFreshHub/Order/repository/CustomerOrderReadRepository.java` |
| Nhãn trạng thái | `src/main/java/VietFreshHub/Order/util/OrderLabels.java` — thêm nhãn payment |
| Template | `src/main/resources/templates/customer/orders.html` |
| Template | `src/main/resources/templates/customer/order-detail.html` |
| Head fragment mới | `src/main/resources/templates/fragments/order-pages.html` |
| CSS mới, scope theo trang | `src/main/resources/static/css/customer-orders.css` |
| Test mới | `src/test/java/VietFreshHub/Order/CustomerOrderReadControllerTest.java` |
| Test mới | `src/test/java/VietFreshHub/Order/CustomerOrderReadPersistenceTest.java` |
| Script UI mới | `scripts/check-customer-orders-ui.cjs` |
| Báo cáo | `docs/FE052_CUSTOMER_ORDER_READ_REVIEW.md` |

## Test và check đã thực sự chạy

**BUILD SUCCESS — 131 tests, 0 failures, 0 errors, 0 skipped.**

- 14 test MockMvc mới: login/role; CurrentCustomerService thật với UserRepository mock; bỏ qua customerId/header giả; ownership/confirmation 404; input sai không truy vấn danh sách mặc định; giữ input; URL phân trang giữ filter và ngày ISO; empty/clamp; tiền thập phân; nhiều shop; hủy + refunded; COD pending và trường hợp không có payment.
- 13 test persistence mới trên **H2 riêng `fe052-orders`**: DTO pagination có SQL `FETCH FIRST`/`OFFSET`; sort placed_at/order_id ổn định; lọc kết hợp và owner; keyword `%` là ký tự literal; cận ngày UTC+7; khoảng ngày một đầu; cận năm; trang ngoài phạm vi; invalid input trước query; ownership cả projection; snapshot sau khi catalog đổi; total/discount; nhiều payment attempt; query count; badge owner/ACTIVE và overflow.
- 104 test hồi quy hiện có: auth, cart, checkout, snapshot, duplicate/race, reservation/cancel/release/reorder/tracking đã có. Các test DB này dùng H2; không chạy GroupSchemaSqlServerTest hoặc VietFreshHubApplicationTests kết nối DB mặc định.
- Chrome headless render HTML do MockMvc/Thymeleaf tạo, dùng CSS/JS hiện tại: **66 case**, gồm 6 màn hình tại 11 chiều rộng **360, 390, 430, 600, 760, 850, 960, 1000, 1280, 1440, 1920 px**. Không có tràn ngang hoặc phần tử main vượt viewport. Đã xem ảnh mobile danh sách/invalid/multi-shop, thanh toán refunded và desktop detail, sửa badge shop bị ép hẹp và khoảng cách giao dịch/nút thao tác rồi kiểm tra lại.
- `git diff --check`: không có lỗi whitespace. So sánh với bản chụp đầu task xác nhận controller tracking/cancel/reorder và service từ actions/cancel/tracking/reorder trở đi giữ nguyên byte nội dung sau chuẩn hóa newline.

Hai vòng đầu của test mới có assertion chưa phù hợp HTML escape/SQL page đầu; một vòng sau thiếu import assertion và chưa chạy được. Đã sửa test rồi chạy lại toàn bộ 131 test trên code cuối. Kết quả trước khi sửa test không được coi là PASS.

Lệnh Maven cuối chạy bằng JDK 21, offline:

```powershell
./mvnw.cmd -o test '-DargLine=-javaagent:C:/Users/Admin/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar' '-Dtest=CustomerOrderReadControllerTest,CustomerOrderReadPersistenceTest,AuthLoginFlowTest,CurrentCustomerServiceTest,CartControllerTest,CartPersistenceTest,CartServiceImplTest,DemoSeedTest,CheckoutControllerTest,CheckoutPersistenceTest,SeedDocumentUrlTest'
```

UI script dùng Playwright đã có trong runtime, không bổ sung dependency vào project. Chạy các test MockMvc trước để có fixture, rồi dùng Node với `NODE_PATH` trỏ tới thư viện Playwright và `CHROME_PATH` trỏ tới Chrome headless:

```powershell
node scripts/check-customer-orders-ui.cjs
```

Log, HTML và ảnh tại `.document-analysis/fe052/` (gitignored). Browser chỉ tải fixture/assets local; không mở app đang chạy, không submit giao dịch và không gọi database.

## Giới hạn và phối hợp

- Không truy cập DB hiện tại, không chạy seed/migration trên DB hiện tại. Lần này chưa kiểm thử các query mới trên một SQL Server test riêng; PASS DB là H2, không suy ra đã kiểm chứng SQL Server runtime.
- Kiểm tra browser là render fixture do Thymeleaf tạo, không phải thao tác end-to-end qua server thật. Input ngày native có định dạng tùy browser/OS; giá trị request/URL luôn ISO, thời điểm đơn hiển thị dd/MM/yyyy theo Việt Nam.
- Schema không có snapshot ảnh hoặc tên shop trong shop_orders. Ảnh lấy thumbnail hiện tại của SKU, tên shop lấy shops; tên sản phẩm/variant/SKU/giá/địa chỉ vẫn là snapshot. Nếu cần đóng băng ảnh/tên shop, cần phối hợp schema với TV2/TV4, ngoài task này.
- SRS MSG44 nói thông báo không có quyền. Giữ chính sách 404 chung hiện có cho foreign/missing order để tránh lộ sự tồn tại đơn; nếu nhóm cần chuẩn thông báo khác, thống nhất với TV1.
- `orders.payment_status` và từng `payments.status` được hiển thị riêng đúng giá trị lưu; màn hình không tự đối soát, đổi state hay suy ra thu tiền COD. Việc đồng bộ aggregate payment/fulfillment vẫn do module sở hữu thực hiện.
