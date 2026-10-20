# Task 01 — Unit test nghiệp vụ giỏ hàng TV3

Ngày thực hiện: 20/10/2026. Phạm vi: A1/A2 cho cart, chuẩn bị B1 trong workflow; không đánh dấu toàn bộ A2/TV3-06 hoàn thành. “Task 01” là yêu cầu hiện tại của người dùng, không đồng nhất với mã backlog TV3-01 đối chiếu tài liệu.

## Baseline và nguồn

Đã đọc `TV3_IMPLEMENTATION_WORKFLOW.md`, `TV3_WORKFLOW_TASKS.md`, Coding Standards tại Downloads, Feature Tree bản `(2).docx` FE-04.1/04.2 và SRS UC-15 Add Product to Cart, UC-16 Manage Shopping Cart, UC-17 Checkout, mục 4 Shopping Cart Management, GBR-21. Đọc DOCX bằng trích xuất XML, không chỉnh sửa tài liệu gốc. Không tìm thấy AGENTS.md ở workspace, src hoặc các thư mục cha đã kiểm tra.

Git status ban đầu có thay đổi checkout/order/application.properties, ShippingQuoteService bị xóa, ApplicationTests bị xóa và docs chưa tracked. Giữ nguyên tất cả; không khôi phục test cũ. Không có file test còn tồn tại trong src/test ở thời điểm bắt đầu. Không sử dụng số liệu test các đợt trước.

Đã đọc CartServiceImpl, CartService, DTO/entity/repository cart, CatalogVariant/Product, CatalogServiceImpl/CatalogCategoryPolicy, truy vấn stock CheckoutInventoryBatchRepository, UserRepository, CurrentCustomerService, controller cart và phần cảnh báo giá của cart.html. Stock query chỉ tính batch ACTIVE, chưa hết hạn theo ngày Asia/Ho_Chi_Minh, lấy on-hand trừ reserved; mock kết quả query không chứng minh SQL thực thi đúng.

## Đối chiếu yêu cầu và bằng chứng

| Yêu cầu | Hành vi / bằng chứng unit test | Kết luận trong phạm vi |
| --- | --- | --- |
| FE-04.1, UC-15 thêm variant | Tạo cart của user truyền vào, lưu variant, quantity, giá hiện tại, subtotal và thông báo; thêm lại cập nhật cùng entity/ID, không tạo cart hoặc dòng trùng | Đã kiểm chứng với mock persistence |
| UC-15 AF1, GBR-21 kiểm tra tồn | Reject quantity null/0/âm, variant ID thiếu/không hợp lệ; tổng quantity cũ+mới vượt stock, stock 0/null; cộng vượt int không tràn thành số âm | Đã kiểm chứng; exception chứa tồn hiện tại, không save item hoặc đổi snapshot khi reject |
| FE-04.2 khả năng mua | Dùng CatalogServiceImpl thật, repository giả; variant inactive, product inactive/chưa APPROVED, shop inactive/đóng, category inactive đều chặn add và update | Đã kiểm chứng những điều kiện nêu trên; không suy ra toàn bộ catalog đã được test |
| Variant không tồn tại | Add bị từ chối trước truy cập giỏ/kho | Đã kiểm chứng |
| UC-16 cập nhật/xóa/clear | Update tới đúng stock dùng giá mới; vượt stock/âm bị chặn; về 0 xóa kể cả variant inactive; remove giữ dòng khác; clear sạch collection | Đã kiểm chứng collection và lời gọi delete, chưa chứng minh DELETE/orphan removal thực tế |
| Giỏ rỗng / món không có | Không có cart trả summary 0; remove/clear không lỗi; update cart/món không có trả ResourceNotFoundException; cart tồn tại nhưng rỗng trả tổng 0 | Đã kiểm chứng |
| UC-16, GBR-21 giá hiện tại | Giá tăng/giảm trả priceChanged, previousPrice và subtotal/tổng theo giá mới; khác scale cùng giá trị không báo đổi; đọc không ghi snapshot | Đã kiểm chứng DTO; cart.html có hiển thị cảnh báo giá, chưa render/browser test |
| UC-16 AF1 món không khả dụng | Giữ dòng, thông báo tiếng Việt riêng cho hết hàng, thiếu số lượng, catalog không hợp lệ; cờ hasUnavailableItems; tổng chỉ cộng món purchasable, totalItems gồm cả món invalid | Đã kiểm chứng baseline; công thức tổng giỏ hỗn hợp chưa được tài liệu chốt chi tiết |
| Chỉ giỏ customer từ xác thực | Controller unit test kiểm tra cả 4 mutation truyền ID do CurrentCustomerService trả; lỗi xác thực không gọi cart. Service test 5 đường add/update/remove/clear/read dùng lookup userId+ACTIVE và cartId+variantId, không lookup item trực tiếp theo ID | Đã kiểm chứng ranh giới truyền ID; chưa chứng minh HTTP security/CSRF/session hoặc query SQL ownership |
| User không tồn tại/không ACTIVE | Cả 4 mutation bị AccessDeniedException trước truy cập cart/catalog/stock, với missing/INACTIVE/LOCKED | Đã kiểm chứng; service dựa vào caller kiểm tra role CUSTOMER |
| Không giữ kho/tạo đơn khi thêm/sửa | Test success chỉ cho phép stock read, verifyNoMoreInteractions(stock); source chỉ có 6 dependency user/cart/item/variant/stock/catalog, không gọi checkout/order/reservation | Không có đường reserve/order trong source đã đọc; không phải bằng chứng kiểm thử tích hợp hoặc trigger DB |

## Chênh lệch và phần chưa đủ bằng chứng

1. **Coupon**: FE-04.2/GBR-21 yêu cầu promotion eligibility; cart chưa có dependency/validation coupon. Bằng chứng: constructor và các nhánh add/update/view của CartServiceImpl. Đây là phần thiếu so với tài liệu nhưng đã được chỉ đạo hoãn; bàn giao TV5, không triển khai trong Task 01/02 khi chưa được giao.
2. **Thông báo đổi giá sau mutation**: UC-16 AF1 yêu cầu flag/thông báo khi giá đổi. getActiveCart giữ cờ và previousPrice khi chỉ đọc; add/update lại ghi đè snapshot bằng giá mới và trả thông báo thành công chung. Sau mutation, lần đọc tiếp có thể không còn priceChanged. Bằng chứng: setUnitPriceSnapshot trong add/update, so sánh snapshot trong toItem, controller trả success chung. Test update kiểm tra giá mới; test view kiểm tra cảnh báo trước khi snapshot bị ghi đè. Chưa có regression test yêu cầu thông báo xuyên mutation; Task 02/B1 cần chốt và bổ sung test mục tiêu trước khi sửa. Không coi snapshot refresh tự động là đủ đáp ứng toàn bộ AF1.
3. **Tổng giỏ hỗn hợp**: code loại toàn bộ subtotal dòng thiếu stock/không hợp lệ, vẫn đếm quantity của dòng đó trong totalItems; đây là baseline được test, SRS chỉ nói hiển thị totals/current price và flag invalid, chưa quy định rõ hai số này. Task 02/B1 xác nhận quy tắc hiển thị; không tự đổi công thức.
4. **Chưa chứng minh**: revalidation ngay confirm thuộc checkout; locking user, cạnh tranh tạo cart/dòng trùng, rollback cart mới tạo trước khi phát hiện thiếu kho, batch hết hạn/reserved và orphan removal cần SQL Server integration trên DB test riêng. Mockito không chạy transaction proxy, JPQL hay SQL. Chưa kiểm tra UI render, role/filter/CSRF/session thực. Chưa đo coverage; không kết luận đạt 60%.

## Thực thi mới

- Runtime dùng cho Maven và test: JDK `C:/Program Files/Java/jdk-21.0.12.1` (release 21). Java mặc định của máy là 8; chỉ đặt JAVA_HOME/PATH trong process lệnh test, không sửa cấu hình môi trường lưu trên máy.
- Dependency hiện có từ starter test trong pom.xml: JUnit Jupiter, parameterized tests, MockitoExtension/Mockito strict stubs. Không thêm dependency hoặc sửa pom.
- Lệnh chạy thành công (PowerShell, tại workspace):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -o '-Dtest=CartServiceImplTest,CustomerCartIdentityTest' '-DargLine=-javaagent:C:\Users\Admin\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar' test
```

- Kết quả Surefire mới: CartServiceImplTest **42**; CustomerCartIdentityTest **2**; tổng **44 chạy, 44 pass, 0 failure, 0 error, 0 skipped**. BUILD SUCCESS. Maven báo hoàn thành `2026-10-20T01:56:51+07:00`, tổng thời gian 12.976 s. Parameterized invocations được tính riêng theo Surefire, không đếm số method thành số test.
- Lần thử đầu chỉ chọn CartServiceImplTest dừng ở tự attach Mockito, dumpstream báo không mở được Windows attach pipe; không có summary nên không tính là test pass/fail. Đã dừng session đó. Lần thành công dùng Java agent ngay khởi động JVM; còn cảnh báo CDS của JVM, không có test failure. Không giảm assertion/skip để chạy qua.
- Chỉ chạy plain unit tests được chọn, không SpringBootTest/DataJpaTest, ứng dụng, scheduler, DB hoặc gateway. Reports gốc ở target/surefire-reports; bản tóm tắt lưu tại TV3_TASK01_TEST_RUN.log.

## File được thêm trong Task 01

- `src/test/java/VietFreshHub/cart/CartServiceImplTest.java`
- `src/test/java/VietFreshHub/cart/CustomerCartIdentityTest.java`
- `docs/TV3_TASK01_CART_TEST_REPORT.md`
- `docs/TV3_TASK01_TEST_RUN.log`

Đề xuất chuyển **Task 02 — Cart validation/B1**: chốt thông báo giá sau add/update và quy tắc tổng giỏ hỗn hợp; viết regression test cho quyết định đó rồi sửa trong phạm vi TV3. Tiếp tục test checkout ở A2/B2 và chuẩn bị integration SQL Server riêng cho rollback/locking. Task 01 hoàn thành nền unit test cart, không xác nhận toàn bộ FE-04/checkout hay toàn dự án đã đạt nghiệm thu.
