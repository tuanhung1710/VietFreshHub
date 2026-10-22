# Rà soát tiến trình cart checkout và payment ngày 21 tháng 10 năm 2026

Phạm vi theo xác nhận mới nhất của người dùng: cart, checkout và payment phía khách hàng, loại trừ online payment đã bàn giao. VNPay, khởi tạo giao dịch online, xử lý gateway, xác minh callback/webhook và retry online không được tính là phần thiếu của người dùng. Chưa có thông tin người nhận nên không tự gán tên. Báo cáo này cập nhật tiến trình theo source, không sửa phân công của thành viên khác hoặc ghi đè các tài liệu nguồn.

## Đối chiếu từng tài liệu

1. **Feature Tree** (`C:/Users/Admin/Downloads/Group 1 - Feature Tree  - VietFresh Hub (2).docx`): FE-04 gồm 5 chức năng giỏ và 4 validation; FE-05.1 gồm địa chỉ, phương thức, coupon, review, tách shop, xác nhận; FE-06 gồm COD, online và lịch sử/trạng thái. FE-05.2 được ghi riêng để thấy các chức năng liên quan đang có, không mở rộng nhiệm vụ chỉ vì bảng cũ giao TV3.
2. **SRS** (`C:/Users/Admin/Downloads/SE2056-JV - Group 1  - SRS.docx`): UC-15/16 yêu cầu kiểm tra lại giá, tồn và tính khả dụng; UC-17 giữ kho và tách shop; UC-18 chỉ ghi usage coupon khi đặt thành công; UC-20 chỉ đọc payment của chính khách. UC-22 cho hủy Pending/Confirmed, chặn từ Preparing; UC-24 đặt lại chỉ lấy món còn bán và còn tồn. NFR yêu cầu transaction, bảo mật sở hữu, tiếng Việt, màn hình 360–1920 px, hiệu năng 50 người đồng thời và unit coverage tối thiểu 60% logic service.
3. **SDS** (`C:/Users/Admin/Downloads/SE2056-JV - Group 1  - SDS.docx`): đối chiếu phân lớp, quy ước Service/ServiceImpl, Request/Response và các bảng carts, cart_items, orders, order_addresses, shop_orders, order_items, inventory, discounts, payment_methods, payments. Mô tả thiết kế không chứng minh tích hợp đã chạy.
4. **Coding standard** (`C:/Users/Admin/Downloads/VietFreshHub_Coding_Standards.md`): dùng làm tiêu chí đánh giá; nội dung hướng dẫn AI trong tài liệu không thay thế yêu cầu hiện tại của người dùng. Giữ Java 21/Boot 4.1.1/MVC/Thymeleaf/JPA/SQL Server/session.
5. **DDL** (`D:/S5/SWP391/Database/vietfresh_hub_script.sql`): có filtered unique index một cart ACTIVE/user, unique cart/variant, DECIMAL tiền, constraints payment, reserved stock, allocations, vị trí nhận/giao và shipping distance. Payment.Status đang khớp CHECK của payments. Không chạy script lên DB dùng chung trong lần rà soát này.
6. **Seed** (`D:/S5/SWP391/Database/data.sql`): có dữ liệu demo nhiều trạng thái đơn/payment, tồn và allocations; có kiểm tra tổng số demo và reserved khớp allocations. Bản ghi PAID được seed không chứng minh luồng ứng dụng đã thu COD hoặc chuyển PAID thành công.
7. **ProjectTracking** (`C:/Users/Admin/Downloads/SE2056-JV - Group 1  - ProjectTracking.xlsx`): sheet XML thứ nhất, I58:I77 và I84:I90 vẫn là To Do; G85:G88 vẫn giao TV3 cho online payment. Đây là trạng thái/phân công cũ so với source và chỉ đạo mới. Các sheet iteration cũng còn liệt kê online. Không tự chuyển các dòng đó sang Done, không tự chọn người nhận bàn giao.

## Tiến trình cập nhật theo bằng chứng source

“Đã có code” nghĩa là đã có triển khai và test liên quan, chưa đồng nghĩa nghiệm thu UI, SQL Server hoặc toàn bộ NFR.

| Hạng mục | Trạng thái rà soát | Bằng chứng và phần còn lại |
|---|---|---|
| FE-04.1 thêm, sửa lượng, xóa món, xóa giỏ, tổng giỏ | Đã có code | CartServiceImpl, CustomerCartViewController, cart.html; quantity 0 xóa món, thêm trùng gộp dòng |
| FE-04.2 availability, stock, price | Đã có code | CatalogService kiểm tra khả dụng; đọc tồn theo batch chưa hết hạn, trừ reserved; giá hiện tại và cảnh báo thay đổi giá |
| FE-04.2 promotion/coupon validation | Chưa hoàn thành | Không có luồng áp coupon trong CartServiceImpl/CheckoutServiceImpl |
| FE-05.1 chọn địa chỉ, review và chọn COD | Đã có code | Kiểm tra chủ địa chỉ, vị trí VietMap; quote theo từng shop; chỉ cho COD ACTIVE |
| FE-05.1 áp promotion/coupon | Chưa hoàn thành | Chưa có input và xử lý mã coupon, discount và usage trong transaction checkout; phối hợp TV5 |
| FE-05.1 tách shop, confirm, giữ kho | Đã có code | Tạo Order/ShopOrder/OrderItem và snapshot, reserve FEFO cùng transaction; idempotency bằng token/orderCode; giữ món chưa chọn |
| FE-06 COD khi đặt đơn | Đã có code | Payment PENDING, Order UNPAID; không ghi đã thu ngay lúc đặt |
| FE-06 trạng thái và lịch sử payment của khách | Đã có code | CustomerPaymentHistoryServiceImpl/Controller, projection theo customer, filter và phân trang DB |
| FE-06 xác nhận đã thu COD | Chưa đủ luồng hoàn chỉnh trong source rà soát | Không tìm thấy code ghi payment PAID/paidAt và order paymentStatus sau giao thành công; cần phối hợp người phụ trách delivery, không tính thành thiếu online payment |
| Online payment | Đã chuyển khỏi phạm vi | VNPay và initiate/process/verify online cần ghi bàn giao, không ghi To Do của người dùng |
| FE-05.2 đơn khách hàng liên quan | Đã có code | CustomerOrderServiceImpl/QueryService: history, details, tracking, cancel và reorder; không tự nhận thêm quyền sở hữu module |

## Kết quả coding standard

Đáp ứng qua kiểm tra source: Jakarta, constructor injection, entity Getter/Setter, tiền BigDecimal, ngày hết hạn LocalDate, sự kiện UTC, role CUSTOMER và kiểm tra ownership, DTO nhận request, POST cho mutation, PRG sau confirm, form Thymeleaf dùng th:action, CSRF bật, fragment chung, query có tham số, history phân trang DB. Checkout tính lại giá, khóa catalog/user, giữ kho trong transaction. Gọi VietMap quote nằm ngoài transaction (`Propagation.NEVER`); confirm kiểm tra lại snapshot và phí trong transaction. Không coi những quan sát source này là bằng chứng đã kiểm tra mọi request trình duyệt.

Các điểm chưa tuân thủ hoặc chưa đủ bằng chứng:

- **Thiếu nghiệp vụ coupon**: UC-18/GBR-33/34 và FE-04.2/FE-05.1 chưa được triển khai trọn luồng. Cần validation eligibility, thời hạn, giới hạn tổng/từng khách, snapshot giảm giá, usage đúng một lần sau đặt thành công; phải thống nhất contract với promotion, không tự suy diễn công thức.
- **Vòng đời COD chưa khép kín**: cần thống nhất giao dịch cập nhật payments/order khi thực thu. Đơn nhiều shop cần quy tắc hoàn tất/tổng hợp rõ để không đánh dấu toàn đơn PAID sau khi mới giao một shop. Lịch sử chỉ phản ánh dữ liệu đã lưu.
- **Phí và phạm vi giao hàng chưa có căn cứ trong bộ tài liệu này**: ShippingFeeService hardcode 15.000 cho 2 km đầu, thêm 5 đồng/mét, làm tròn 1.000; CheckoutShippingQuoteService chặn trên 15 km. Chưa tìm thấy các con số này trong SRS/SDS/Feature Tree; cần đối chiếu quyết định nhóm đã chốt trước khi xác nhận đúng nghiệp vụ. Cấu hình app.checkout.shipping-fee-per-shop còn mặc định 20.000 trong application.properties, cần làm rõ còn dùng hay đã cũ.
- **Naming**: CartDto, CartItemDto, CheckoutPreview/Result, PaymentHistoryRow/Filter không theo mẫu Request/Response khi đóng vai trò DTO qua boundary. Các helper nội bộ có thể cần ngoại lệ được thống nhất. PaymentService, InventoryReservationService, ShippingFeeService, CheckoutShippingQuoteService là concrete @Service chưa có cặp interface/Impl theo quy ước SDS. Ưu tiên thấp hơn thiếu nghiệp vụ; không đổi hàng loạt trong lần review.
- **Repository đọc payment** dùng Repository thay JpaRepository để cố ý không lộ save; hợp lý về thiết kế read-only nhưng là ngoại lệ so với wording tiêu chuẩn mục 4.1, cần ghi nhận thay vì tự thêm API ghi.
- **Phân chia controller**: CustomerCartViewController còn chứa home/product detail/recommendation; nên thống nhất boundary với người phụ trách catalog. Đây là vấn đề trách nhiệm module, không đủ căn cứ tự chuyển code.
- **Tài liệu standard đã cũ ở vài mô tả hiện trạng**: pom hiện có mail, Cloudinary, WebSocket và OAuth2; SecurityConfig ở package security. Không dùng mô tả cũ để kết luận các dependency đó chưa tồn tại.
- **NFR chưa chứng minh**: chưa có report đo service coverage >=60%, benchmark 50 concurrent/p95 <=3s, kiểm tra Chrome/Edge và toàn dải 360–1920. Không suy ra coverage từ số test.

## Thứ tự tiếp tục

1. Ghi nhận online payment đã bàn giao, giữ các interface/read model payment để phối hợp; chưa có tên người nhận thì để chưa xác định.
2. Hoàn thiện contract coupon với TV5 và transaction usage/discount; chưa được đánh dấu checkout hoàn tất toàn bộ khi phần này còn thiếu.
3. Chốt contract thu COD với delivery và tổng hợp đơn nhiều shop; kiểm tra idempotency và đồng bộ trạng thái.
4. Đối chiếu chính sách phí/15 km với quyết định nhóm, làm rõ cấu hình phí còn sót.
5. Hoàn tất kiểm thử SQL Server, UI và coverage, sau đó mới đóng trạng thái nghiệm thu. Chuẩn hóa naming theo thay đổi nhỏ, tránh ảnh hưởng phần người khác.

## Quy tắc cập nhật sau rà soát

Người dùng xác nhận sản phẩm hết tồn khả dụng hoặc ngừng khả dụng không được trưng bày. Tồn khả dụng là lượng thực tế trừ lượng đang giữ, chỉ tính batch ACTIVE chưa hết hạn. Danh sách và count phân trang được lọc ngay trong query; trang chi tiết không cung cấp lựa chọn biến thể hết hàng hoặc ngừng bán. Sản phẩm ACTIVE có hàng trở lại sau nhả giữ kho được xuất hiện lại, không tự kích hoạt lại mặt hàng đã ngừng bán.

Hủy đơn hợp lệ giữ luồng release hiện có. Với giao thất bại, người dùng chọn **chỉ nhả khi cửa hàng xác nhận đã nhận lại hàng**. Đã nối bước xác nhận trả hàng hiện có vào OrderInventoryReleaseService, cùng transaction với hoàn tất phân công và lịch sử. Giảm reserved_quantity theo allocation RESERVED, chuyển RELEASED, ghi RELEASE; không cộng quantity_on_hand, không nhả trùng khi gọi lặp. Nếu allocation/audit không khớp hoặc đã SOLD, chặn tự động thay vì cộng trả sai. Không đổi luồng online payment.

Kiểm tra sau thay đổi: **BUILD SUCCESS, 217 trường hợp, 192 chạy qua, 25 SQL Server integration skipped, 0 failures/errors**. Bổ sung 10 test cho ẩn sản phẩm/biến thể, hiện lại khi có tồn, chặn hàng ngừng bán, xác nhận trả hàng, lỗi kho, xác nhận lặp và release thực tế không tăng tồn vật lý hoặc ảnh hưởng phần giữ của đơn khác. Chưa chạy xác minh SQL Server hay trình duyệt thực tế cho thay đổi này.

## Kiểm chứng chạy thực tế

- Build sạch bằng JDK 21 đã biên dịch 337 source files và 20 test source files thành công. Chạy suite sau đó bằng JDK 21 với Mockito agent tường minh: **BUILD SUCCESS, 207 trường hợp được báo cáo, 182 chạy qua, 25 skipped, 0 failures, 0 errors**.
- 25 skipped đều thuộc CheckoutSqlServerIntegrationTest vì test chỉ bật khi có TV3_TEST_JDBC_URL trỏ tới database test tạm có marker riêng. Không có bằng chứng chạy tích hợp SQL Server trong lần này, không dùng DB chung để thay thế.
- Trong 182 trường hợp chạy qua có cart, draft, checkout, shipping quote, failure recovery, HTTP security, đơn khách hàng, payment history và recommendation. Đây không phải phép đo coverage.
- Hai lượt đầu chưa cho kết quả kiểm thử hợp lệ: Maven ban đầu dùng Java 8; sau khi đổi JDK 21, Mockito tự attach vướng quyền pipe Windows. Đã khắc phục cho riêng lệnh kiểm tra bằng `-javaagent` tới mockito-core 5.23.0 có sẵn, không sửa pom hoặc cấu hình máy lâu dài.
- Lệnh tái hiện trong PowerShell, sau khi đặt JAVA_HOME tới JDK 21 và thêm bin vào PATH: `./mvnw.cmd '-DargLine=-javaagent:C:\Users\Admin\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar' test`.
- Module coupon quản trị đã tồn tại (CouponService có create/update/activate/deactivate/disable theo shop); phần thiếu là tích hợp áp dụng coupon phía khách hàng và ghi usage trong checkout, không phải toàn bộ module coupon.
- Không sửa mã ứng dụng hoặc tài liệu nguồn trong lần rà soát này. Tiến trình đã cập nhật trong báo cáo này; workbook gốc giữ nguyên để không ghi Done khi chưa nghiệm thu.
