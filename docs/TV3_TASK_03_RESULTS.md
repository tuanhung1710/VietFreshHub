# Task 03 — Hoàn thiện Cart/Checkout theo bằng chứng

Ngày thực hiện: 20/10/2026. Căn cứ: workflow Task 03, FE-04/05.1, UC-17 và yêu cầu thông báo lỗi rõ ràng/không tạo đơn trùng. **Task 03 đã hoàn thành phạm vi nghiệm thu Cart/Checkout đã liệt kê: sửa lỗi phục hồi, 79 test hồi quy và 8 test SQL Server thật trên DB tách biệt.** Kết quả không thay thế các task hủy/giao hàng/payment/notification và coverage toàn dự án.

## 1. Kiểm tra đầu vào

- Đọc các test Cart đang có, bộ test Task 02 và luồng controller/service hiện tại. Giữ nguyên các thay đổi người dùng đang có trong checkout/order/properties và các file đã xóa.
- Cart hiện có 42 test service (gồm parameterized cases) và 2 test chuyển identity từ controller. Đợt này đã chạy lại, không chỉ dùng số liệu cũ.
- Ở đợt đầu, thử kết nối SQL Server bằng truy vấn chỉ đọc `SELECT 1` nhận lỗi hostname/login timeout. Sau khi người dùng cung cấp hai script mới, đã kết nối được qua `127.0.0.1`; cập nhật nghiệm thu ở mục 6. Không đổi application.properties hoặc ghi dữ liệu lên DB nhóm.

## 2. Lỗi tái hiện và cách sửa

### Nhánh lỗi persistence/transaction của POST checkout

Trước sửa:

- Chỉ bắt `TransientDataAccessException`; lỗi constraint, mất kết nối thuộc nhánh non-transient hoặc lỗi transaction/commit có thể thoát ra trang lỗi.
- Nhánh transient khẳng định “Đơn hàng chưa được tạo”, dù một số lỗi kết nối/commit không đủ thông tin để kết luận transaction đã commit hay chưa.
- Sau lỗi tiếp tục gọi preview và báo giá. Khi DB vẫn lỗi hoặc cart đã CHECKED_OUT, việc khôi phục form có thể thất bại; không nên gọi thêm DB/API trong nhánh này.

Đã viết test trước khi sửa: 5 cases chạy, case thành công pass, 4 cases hạ tầng báo lỗi theo hành vi cũ. Test transient có preview mock không được stub vì yêu cầu hồi phục là không được query lại; failure ở đó chứng minh code đã đi tiếp vào nhánh requery, không phải bằng chứng rằng production luôn gặp NullPointerException.

Sau sửa:

- Bắt `DataAccessException` và `TransactionException` ngay quanh `checkout.confirm`.
- Trả HTTP 503 với trang `customer/checkout-unavailable`, không khẳng định đơn chắc chắn thất bại hoặc chắc chắn thành công.
- Giữ token/draft cũ, không phát sinh token mới, không tự submit lại, không gọi preview/cart/VietMap trong nhánh lỗi.
- Link chính tra cứu đúng mã `VF-<token>` trong lịch sử đơn; link phụ quay lại GET review bằng token cũ. Nếu hệ thống chưa phục hồi, khách vẫn có thể gặp lỗi ở trang tiếp theo; chưa có cơ chế tự khôi phục DB.
- Nhánh lỗi nghiệp vụ CheckoutException và nhánh thành công giữ nguyên hành vi.
- Chỉ log customer ID và loại exception trong nhánh này; không đưa SQL/credential/stacktrace vào UI.

Thay đổi này cải thiện phục hồi khi kết quả chưa rõ, **không thay thế idempotency/rollback thực của service và DB**. Không thêm payment online, coupon hoặc quy tắc kinh doanh mới.

## 3. File thay đổi trong Task 03

| File | Thay đổi |
| --- | --- |
| `src/main/java/VietFreshHub/checkout/controller/CheckoutController.java` | Sửa riêng catch của POST confirm |
| `src/main/resources/templates/customer/checkout-unavailable.html` | Trang phục hồi không phụ thuộc đọc DB, không có form thanh toán |
| `src/main/resources/static/css/checkout-recovery.css` | CSS scope riêng, góc vuông, mobile/desktop |
| `src/test/java/VietFreshHub/checkout/CheckoutFailureRecoveryTest.java` | 4 loại lỗi hạ tầng, redirect thành công/replay, HTTP POST và render template thật |
| `src/test/java/VietFreshHub/checkout/CheckoutSqlServerIntegrationTest.java` | Bổ sung đợt DB mới: 8 kịch bản JPA/SQL Server thật, opt-in, có guard tên và marker DB |
| `scripts/tv3-sqlserver-tests.ps1` | Runner DB mới: xác minh fingerprint nguồn, tạo DB riêng và chạy tổng hợp 87 test |
| `docs/TV3_IMPLEMENTATION_WORKFLOW.md` | Cập nhật trạng thái, không đóng phần integration chưa chạy |
| File báo cáo này | Bằng chứng, giới hạn và backlog nghiệm thu |

Không sửa service/entity/repository Cart/Checkout hoặc module thành viên khác trong đợt này. Các thay đổi production đã tồn tại trước Task 03 vẫn được giữ nguyên.

## 4. Kết quả kiểm chứng đợt đầu, trước bổ sung DB mới

Java 21, Maven offline: **BUILD SUCCESS — 79 tests, 0 failures, 0 errors, 0 skipped**.

| Bộ test | Số test |
| --- | --- |
| CartServiceImplTest | 42 |
| CustomerCartIdentityTest | 2 |
| CheckoutDraftStoreTest | 5 |
| CheckoutServiceImplTest | 16 |
| CheckoutShippingQuoteServiceTest | 8 |
| CheckoutFailureRecoveryTest | 6 |

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
.\mvnw.cmd -o '-Dtest=CartServiceImplTest,CustomerCartIdentityTest,CheckoutDraftStoreTest,CheckoutServiceImplTest,CheckoutShippingQuoteServiceTest,CheckoutFailureRecoveryTest' test
```

- MockMvc POST render template thật, status 503, hai link đúng mã/token, không có form tự gửi lại và không hiện thông báo “Đơn hàng chưa được tạo”. MockMvc này không có security filter chain.
- Headless Chrome kiểm tra trang phục hồi ở 360/768/1440 px: không tràn ngang; panel radius 0; link đúng; không form. Đã xem ảnh mobile.
- HTML/ảnh/script preview ở `target/tv3-task03/`, dùng dữ liệu giả, không commit.
- `git diff --check` đạt. Chưa đo coverage, không kết luận đạt 60% logic service.

## 5. Checklist nghiệm thu SQL Server ban đầu

Chỉ thực hiện trên DB test tách biệt khi kết nối được, không dùng DB đang chạy của nhóm để reset/seed:

| Kịch bản | Bằng chứng cần có |
| --- | --- |
| Rollback do thiếu tồn ở shop thứ hai | Không lưu Order/ShopOrder/Item/Payment nửa chừng; reserved/allocation/ledger shop đầu và cart trở về trạng thái trước transaction |
| Rollback khi persist payment/flush lỗi | Không có đơn/giữ kho mồ côi, không gửi thông báo thành công |
| Hai confirm cùng token | Hai response tham chiếu cùng một order; chỉ một payment và một bộ allocations; không reserve hai lần |
| Hai customer tranh lượng hàng cuối | Tổng reserve không vượt on-hand; một request bị từ chối rõ ràng; không cart/đơn dở dang |
| Checkout một phần/đa shop | Tổng tiền, phí, snapshot và allocation đối chiếu DB; món chưa chọn còn trong giỏ |
| FEFO và batch hết hạn | Chỉ batch hợp lệ, ưu tiên expiry sớm; quantity/reserved/ledger khớp; không trừ on-hand hai lần |
| Service transaction và event | Có rollback thật khi exception; consumer không thông báo transaction thất bại |

Đây là checklist lập trước khi có DB mới. Các ca Cart/Checkout đã được chạy ở mục 6. Notification consumer sau commit thuộc task bàn giao TV1, chưa được triển khai/nghiệm thu bởi bộ test này.

## 6. Nghiệm thu bổ sung với DB mới của nhóm

### Nguồn và tương thích

- Schema: `D:/Download/vietfresh_hub_script.sql`, SHA256 `C6EE34C228E714AD5F80992086B9BD13E6AA320E0EE727D0CDCBE1C2B8D3372D`.
- Seed: `D:/Download/data.sql`, SHA256 `0667C39824C613BA290F18B36AA06E7BEC14C64834FA2A15980F91A793652FE5`.
- Schema có 60 bảng; so với schema trước sau khi chuẩn hóa tên DB, thay đổi DDL thấy được là username nullable/collation/check/unique index ở users. Các bảng/constraint/trigger Cart/Checkout vẫn tương thích với phần source được thực thi trong test.
- Không sửa script gốc. Tạo bản sao dưới target, chỉ thay tên DB sang tên ngẫu nhiên `VietFreshHub_TV3_Test_<32 hex>`. Guard từ chối DB đã tồn tại; gắn extended property riêng; integration test kiểm tra cả tên DB và marker trước mọi thao tác ghi.
- Seed chạy thành công trên DB test rỗng: 19 users, 20 products, 33 variants, 84 batches, 100 orders, 269 order lines, 260 allocations. Không coi payment/ledger đã seed là bằng chứng nghiệp vụ mới chạy đúng.
- SQL Server 15.0.2000.5, READ_COMMITTED mặc định. Không chạy ứng dụng đầy đủ, scheduler, callback hay VietMap thật.

### Cách test

`CheckoutSqlServerIntegrationTest` tạo Hibernate/JPA repository và Spring transaction proxy thật; dùng CartServiceImpl, CatalogServiceImpl, CheckoutServiceImpl, InventoryReservationService, CheckoutShippingQuoteService, CustomerAddressService và các phần đọc ShopService thật. VietMap, AuthService không được sử dụng cho quyền quản lý shop, và nơi nhận event được mock; không bật security filter chain.

Các fixture riêng được thêm vào DB test. Ca payment failure thêm CHECK constraint thử nghiệm chỉ trong DB test, xác minh lỗi SQL 547, rồi bỏ constraint trong finally. Không thay đổi schema nguồn hoặc schema DB nhóm.

| Integration test | Kết quả |
| --- | --- |
| Đặt đa shop: snapshot, COD PENDING, tổng tiền, allocations/RESERVE | PASS |
| Checkout một phần: giữ món shop khác trong cart ACTIVE | PASS |
| Exception sau reserve thật: rollback Order/Payment/cart/stock/ledger | PASS |
| Hai request cùng token đồng thời: cùng order, một payment/reservation | PASS |
| Shop thứ hai thiếu hàng: rollback cả giữ kho shop đầu | PASS |
| Payment bị CHECK constraint thật từ chối: rollback order/reserve | PASS |
| Hai khách tranh 1 sản phẩm cuối: một thành công, một CheckoutException; không vượt tồn | PASS |
| FEFO: expiry sớm → muộn → không expiry, bỏ expired/BLOCKED; không trừ on-hand, preparation snapshot trigger đúng | PASS |

Lần chạy tổng hợp qua runner mới: **BUILD SUCCESS — 87 tests, 0 failures, 0 errors, 0 skipped** (79 hồi quy + 8 SQL Server). Không cộng thêm các lần chạy trước để tăng số test.

Đối chiếu SELECT sau test trên DB riêng:

| Kiểm tra | Số sai lệch |
| --- | --- |
| Reserved so với tổng allocation RESERVED | 0 |
| On-hand so với ledger vật lý | 0 |
| Reserved so với ledger RESERVE/RELEASE/SALE | 0 |
| Tổng Order so với tổng ShopOrder | 0 |
| CHECK thử nghiệm còn sót trên payments | 0 |

DB nghiệm thu cuối giữ lại để review: `VietFreshHub_TV3_Test_1cebe8d617ef4c09baa55196a0085dc7`. Đây không phải DB của ứng dụng. DB thử ban đầu có tên `VietFreshHub_TV3_Test_40767ba8d3794d9eabbdcce1995d5d70` cũng là DB do đợt này tạo, không được cấu hình vào ứng dụng.

### Chạy lại

Runner `scripts/tv3-sqlserver-tests.ps1` yêu cầu Java 21, Maven dependencies đã cache, sqlcmd và SQL Server local; nhận credentials từ `TV3_TEST_SQL_USER`/`TV3_TEST_SQL_PASSWORD` của process, không tự đọc properties production. Runner từ chối SQL nguồn khác fingerprint đã review để tránh vô tình thực thi một phiên bản chưa kiểm tra. Cấp credentials trong terminal riêng, không commit vào file:

```powershell
# JAVA_HOME và hai biến credential cần có trong process trước khi chạy.
.\scripts\tv3-sqlserver-tests.ps1
```

Mỗi lần runner tạo DB test mới, không reset DB cũ hoặc DB nhóm. DB được giữ lại để review; tên và fingerprint nguồn lưu ở `target/tv3-sqlserver/`. Runner không xóa DB tự động.

Integration test opt-in theo `TV3_TEST_JDBC_URL`, mặc định không kết nối DB khi chỉ chạy test thông thường. Việc skip khi không cấp môi trường không được báo thành integration PASS.

### Giới hạn còn lại

- 8 ca tích hợp đã chạy không phải load test dài hạn hoặc bằng chứng không thể deadlock trong mọi cách cập nhật từ module khác. Chưa nghiệm thu RCSI/SNAPSHOT hoặc nhiều instance ứng dụng.
- Chưa kiểm thử live VietMap, HTTP security/CSRF đầy đủ, notification consumer sau commit, thu COD, pickup/SALE, timeout online hoặc cancellation race; các phần này thuộc task tương ứng tiếp theo.
- Không có sửa production mới do schema mới trong đợt bổ sung này; giữ nguyên bản sửa phục hồi checkout đã có. Chưa đo coverage 60%.
- Task tiếp theo đã chuẩn bị trong `docs/TV3_TASK_04_PREPARATION.md`; chưa thay đổi quy tắc hủy.
