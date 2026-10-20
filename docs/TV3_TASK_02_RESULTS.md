# Task 02 — Unit test draft và checkout COD

Ngày thực hiện: 20/10/2026. Căn cứ: Task 02 trong hội thoại; A2/B2 của `TV3_IMPLEMENTATION_WORKFLOW.md`; FE-05.1, UC-17 Checkout, GBR-16/21/23 trong SRS.

## Phạm vi thay đổi

Chỉ thêm ba file test trong `src/test/java/VietFreshHub/checkout/`. Không sửa production code, test Cart có sẵn, pom.xml, application.properties hoặc module thành viên khác. Không bật online/coupon; không chạy Spring application context, scheduler, DB hoặc VietMap thật.

| File test | Số test | Nội dung đã kiểm chứng |
| --- | --- | --- |
| `CheckoutDraftStoreTest.java` | 5 | Token tái sử dụng cho cùng review; tách customer/session; thay giá trị review/địa chỉ/báo giá tạo draft khác mà giữ tab cũ; giới hạn 8 draft; dữ liệu selection/quote bất biến |
| `CheckoutServiceImplTest.java` | 16 | Preview món chọn; chặn lựa chọn rỗng/ngoài giỏ; tách hai shop; snapshot và tổng tiền; COD PENDING/UNPAID; checkout một phần giữ món còn lại; xác nhận đã có đơn không tạo lại; chặn sai customer/token/địa chỉ/tài khoản/method/catalog; chặn món bị xóa, giá/số lượng/báo giá thay đổi; lỗi reserve/flush không phát event thành công |
| `CheckoutShippingQuoteServiceTest.java` | 8 | Phí tính theo shop thay vì dòng hàng; khoảng cách đường bộ từ boundary VietMap; ngưỡng 15 km; lỗi provider; ownership giỏ; xác nhận lại tọa độ lấy/giao hàng, danh sách shop và số tiền phí; giới hạn snapshot khoảng cách |

Các test phí dùng `CheckoutShippingQuoteService` và `ShippingFeeService` thật, mock các boundary address/shop/VietMap. Công thức phí và ngưỡng 15 km được kiểm tra như baseline source hiện tại, không khẳng định là yêu cầu gốc trong SRS.

## Kết quả thực chạy

Java 21, Maven offline: **BUILD SUCCESS — 29 tests, 0 failures, 0 errors, 0 skipped**.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
.\mvnw.cmd -o '-Dtest=CheckoutDraftStoreTest,CheckoutServiceImplTest,CheckoutShippingQuoteServiceTest' test
```

Đây là kết quả riêng Task 02. Không cộng kết quả test Cart chưa chạy trong đợt này và không dùng số liệu test lịch sử để kết luận toàn dự án.

## Bằng chứng và giới hạn

- Test thành công đa shop đối chiếu tổng Payment với tổng ShopOrder, snapshot địa chỉ/tên hàng, danh sách reservation và event chứa ID các ShopOrder.
- Khi reserve ném lỗi, test kiểm tra chưa persist Payment, chưa hoàn tất cart và chưa phát OrderPlacedEvent. Các mock persist trước đó vẫn đã được gọi; chỉ test DB mới xác minh chúng thực sự rollback.
- Khi flush ném lỗi, test kiểm tra exception được truyền ra và không phát OrderPlacedEvent; không kiểm tra mock entity trở về trạng thái trước transaction.
- Kiểm tra replay là tình huống repository trả lại đơn đã có. Chưa chứng minh hai request thật đồng thời chỉ tạo đúng một đơn trên SQL Server.
- Kiểm tra ownership ở đây là service nhận ID; không thay thế kiểm thử HTTP Authentication/CSRF của controller.
- Catalog, inventory reservation, persistence và các dịch vụ liên quan trong test checkout là mock. Chưa chứng minh FEFO, khả năng mua thực tế, số tồn cạnh tranh, trigger hoặc constraint DB.
- Event đã được phát đúng chỗ không chứng minh consumer chạy sau commit hoặc notification đã được gửi.
- Chưa đo coverage và không kết luận đạt ngưỡng 60% service logic.

## Việc còn lại và bước tiếp theo

Trong các tình huống được test chưa phát hiện failure nghiệp vụ cần sửa production code. Không suy rộng thành checkout hoàn chỉnh.

**Task 03 — Hoàn thiện Cart/Checkout theo bằng chứng:** đối chiếu kết quả Task 01 hiện có, bổ sung kiểm chứng các ranh giới còn thiếu trước khi quyết định sửa; ưu tiên integration trên DB test tách biệt cho rollback, confirm lặp/cạnh tranh tồn và selection đa shop. Mọi lỗi được phát hiện cần có test tái hiện; không sửa module khác để làm test pass.

Các khoảng trống đã review trước vẫn là backlog riêng: trạng thái đơn tổng, điều kiện hủy Confirmed, failureReason trong tracking, hook xuất kho, thu COD, notification consumer, lịch sử thanh toán, online và admin monitoring. Task 02 không thay đổi trạng thái hoàn thành của các phần đó.
