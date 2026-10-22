# Phase 6 — Customer Order & Payment integration review

Ngày rà soát: 2026-10-22. Workspace: `D:/S5/SWP391/FinalProject`.

## Kết luận

**Các kiểm tra Customer read/UI thực thi đã đạt. Chưa đủ điều kiện xác nhận toàn hệ thống sẵn sàng production.** Không phát hiện hồi quy ứng dụng phải sửa trong những trường hợp đã chạy. Phát hiện và sửa hai kỳ vọng SQL Server test còn theo contract trước Phase 2; các test SQL Server này vẫn **CHƯA THỰC THI**, không được tính là passed.

Chỉ sửa 4 file test và thêm báo cáo này. So sánh hash trước/sau xác nhận **không đổi source ứng dụng, template, CSS, entity, schema hoặc workflow** trong Phase 6. Giữ mọi thay đổi chưa commit có sẵn; không reset/stash/discard, không commit/push. Không truy cập DB ứng dụng/production.

## A. Test summary

| Lượt kiểm tra | Tổng test | Passed | Failures/errors | Skipped / chưa thực thi |
|---|---:|---:|---:|---:|
| Baseline đầu Phase 6 | 338 | 313 | 0 / 0 | 25 SQL Server |
| Targeted regression/security/JPA | 123 | 123 | 0 / 0 | 0 |
| Full suite cuối Phase 6 | **351** | **326** | **0 / 0** | **25 SQL Server** |

Không cộng kết quả của các lượt chạy để tính số test duy nhất. XML Surefire cuối cùng khớp log Maven: 351 = 326 passed + 25 skipped. Full suite BUILD SUCCESS; số test không chứng minh coverage hoặc tải production.

Môi trường: Windows, JDK `21.0.12.1`, Maven Wrapper của dự án, Hibernate/JPA và H2 `2.4.240` trong bộ test; Mockito javaagent tường minh như các phase trước. Không cài thêm dependency hay đổi pom. Security tests dùng SecurityConfig thực và MockMvc. Template tests dùng Thymeleaf thực. Dữ liệu JPA đặt trong H2 riêng của test; không gọi gateway/VietMap thật.

Logs:
- `.document-analysis/phase6-baseline.log`
- `.document-analysis/phase6-targeted.log`
- `.document-analysis/phase6-full.log`
- `target/surefire-reports/TEST-*.xml`

### Hồi quy test đã sửa

1. `CheckoutSqlServerIntegrationTest.shippingFilterUsesChildFulfillmentEvenWhenParentIsStale`: trước Phase 6 còn dùng AWAITING_DELIVERY cho OUT_FOR_DELIVERY, loại FAILED và tin parent CANCELLED hơn shop đang giao. Contract Phase 2 đã chốt: OUT_FOR_DELIVERY/FAILED chưa đóng fulfillment thuộc SHIPPING; trạng thái con chi phối display; mọi shop CANCELLED mới thuộc CANCELLED. Test sửa để kiểm tra đúng những quy tắc đã có, không đổi nghiệp vụ ứng dụng.
2. `paymentHistoryHttpShowsOnlyOwnedRecordsWithStatusDateAndStablePagination`: trước Phase 6 gửi `status=SHIPPING` và chờ toàn bộ 12 payment records. Đổi sang `PENDING` để lấy đúng 1 payment pending trong fixture; thêm kiểm tra SHIPPING bị validation từ chối. Không ánh xạ order-stage sang payment status để làm test pass.

Đây là lỗi kỳ vọng trong test opt-in được xác nhận bằng source/contract, **không phải bằng chứng test đã chạy trên SQL Server**. Các test compile thành công trong full suite; khả năng thực thi trên SQL Server vẫn chờ DB test.

## B. Cross-page consistency và customer journey

### Một fixture qua các controller thật

Bổ sung test trong CustomerOrderHistorySummaryTest, sử dụng repositories/services thật trên H2 và CustomerHttpHarness với production SecurityConfig. Mocks chỉ ở tra cứu identity/role, dịch vụ ngoài phạm vi như staff-name/cart và checkout; view resolver của harness kiểm tra model, còn render HTML được kiểm tra riêng bằng Thymeleaf.

Một Order COD do fixture lưu với 4 dòng hàng, subtotal 400.000, shipping 20.000, grandTotal 420.000 và payment PENDING 420.000 được đọc qua:

`GET My Orders → Detail → Confirmation → Tracking → Payment History`.

Đã assert:
- Cùng order code, shop grouping, product snapshots, số lượng, grandTotal và thời điểm đặt đã chuyển giờ Việt Nam.
- My Orders và Detail/Confirmation đều Chờ xác nhận, không thành Chờ thanh toán chỉ vì COD PENDING.
- Payment History PENDING, paidAt null, nhãn Chờ thu COD; aggregate order vẫn Chưa thu tiền.
- Summary.cancellable khớp lifecycle.actions() ở trang detail; đơn đang chuẩn bị/xử lý không được coi là hủy được chỉ vì UI đang ở stage nào đó.
- Nhiều shop với COMPLETED + OUT_FOR_DELIVERY/FAILED vẫn primary SHIPPING; phần hoàn thành và phần đang xử lý tách riêng, delivery gắn đúng shop.
- Giá trị shop/order fixture khớp snapshot đã lưu; template không dùng giá catalog hiện tại.
- Điều hướng GET không insert/update/delete entity; số Order và Payment không tăng.

**Giới hạn Scenario A:** fixture được tạo trực tiếp trong test, không phải một giao dịch checkout commit thật trên SQL Server. Checkout/cart/draft/idempotency/reservation vẫn được review source và chạy unit tests có sẵn; các kiểm tra transaction/locking/concurrency SQL Server thuộc 25 test chưa thực thi. Không tuyên bố đã hoàn thành end-to-end checkout trên database đích.

### Ma trận nhất quán

| Dữ liệu | Kết quả kiểm tra |
|---|---|
| Order code/link | List, detail, confirmation, tracking và payment cùng trỏ Order đã có; không tạo order mới khi GET |
| Primary order stage | List/Detail/Confirmation dùng CustomerOrderStageQuery Phase 2, không tính lại trong template |
| Tracking | Hiển thị từng shop/delivery; không lấy Payment.Status làm fulfillment; parent status trong TrackingResponse vẫn là raw persisted field và template hiện không dùng nó làm overall badge |
| Payment History | WHERE Payment.Status, mỗi attempt/record riêng; không phụ thuộc CustomerOrderStageQuery |
| Nhiều payments cùng order | Không gộp, không nhân bản theo items/shops; pagination/count theo payment rows; không cộng thành doanh thu |
| COMPLETED + OUT_FOR_DELIVERY | Chưa hoàn tất toàn đơn; nhãn shop/delivery độc lập |
| COMPLETED + CANCELLED | Hoàn tất phần không bị hủy theo Phase 2; thể hiện shop đã hủy và giữ tiền lịch sử, không nói hàng hủy đã giao/đã hoàn tiền |
| PENDING + PREPARING | WAITING_CONFIRMATION theo giai đoạn sớm nhất còn chưa hoàn tất |
| FAILED | Không thành REFUNDED/RETURN_REFUND; không suy ra Payment.PAID hoặc hàng đã về kho |
| DELIVERED khi ShopOrder chưa COMPLETED | Chỉ giao hàng đã ghi nhận; không suy ra toàn đơn COMPLETED |
| Payment semantics | COD/online PENDING, PROCESSING, PAID, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED đều có unit/JPA/render tests với fixture |
| Ngày/tiền | Ngày lọc Việt Nam → UTC [from, next-day); biên ngày, min/max năm, tiền lẻ và timestamp tùy chọn được test |
| Timeline | Detail chỉ render Event.verified; không tạo timestamp; event thật thiếu timestamp hiện chưa ghi nhận thời gian |

Khác biệt có chủ đích/đã tồn tại: tracking template cũ vẫn hiển thị cả mốc confirmation suy từ current state với thời gian trống; detail Phase 5 chỉ dùng mốc verified. Không coi timeline là nhật ký đầy đủ khi dữ liệu thiếu. Nếu muốn thống nhất toàn bộ tracking UI/provenance cần task read/UI riêng; không thay đổi lịch sử nguồn hoặc chuyển trạng thái trong Phase 6.

### Cancellation, delivery và inventory

- Unit tests xác nhận chỉ PENDING/CONFIRMED trước chuẩn bị mới đủ điều kiện hủy; PREPARING/READY/OUT/COMPLETED, đơn có payment cần đối soát, discounts hoặc shop đã hủy bị chặn. Backend kiểm tra độc lập UI.
- Cancel đổi Order/ShopOrder và Payment sang CANCELLED; không tạo REFUNDED. Hủy lặp không gọi release lần hai trong test service.
- Test release kiểm tra trừ reserved đúng allocation, chuyển RELEASED, ghi RELEASE, không tăng quantity_on_hand và không giải phóng lại lần hai. Đây là unit/mock ledger tests, không phải kiểm chứng locking SQL Server.
- MarkFailed không nhả kho; xác nhận goods return mới gọi release trong transaction hiện có. GoodsReturnStockReleaseTest và ReturnedInventoryReleaseTest được chạy lại. SOLD/ledger không hợp lệ bị chặn ở source/test liên quan.
- Đọc UI không tự PAID, không gọi sell(), không đổi trạng thái delivery. Delivery.DELIVERED và ShopOrder.COMPLETED giữ độc lập trong read model; thao tác staff hiện có mới điều phối cập nhật source.

## C. Security results

Đã chạy production SecurityConfig qua MockMvc cùng tests ownership trên repositories/services thật:

| Tình huống | Kết quả đã kiểm tra |
|---|---|
| Anonymous vào 5 trang customer (kể cả confirmation) | Redirect /login trước business calls |
| ADMIN/STORE_MANAGER/DELIVERY_STAFF vào trang customer | 403; cancel/reorder với CSRF hợp lệ vẫn bị chặn role |
| Direct detail/tracking/confirmation của khách khác | 404 qua controller/service thật trên H2 |
| Cancel/reorder đơn khách khác, có CSRF hợp lệ | 404, backend ownership trước khi đọc/ghi dữ liệu của order |
| Payment History thêm customerId=99 | Không đổi identity server; keyword đơn khách khác trả trang rỗng |
| Thiếu/sai CSRF | 403 cho cancel/reorder; tests các mutation cart/checkout cũ cũng chạy |
| GET vào cancel/reorder | 405, không thực thi action |
| Tài khoản BLOCKED/stale role | Guard đưa về login; không gọi business service |
| Filter sai/trang sai/ngày sai/order-stage trên payment filter | Validation chặn hoặc trả lỗi giao diện, không âm thầm đổi nghĩa status |
| XSS qua tên sản phẩm/shop/recipient/method/reference/failure reason | Thymeleaf escaping được kiểm tra; không render script từ dữ liệu |
| Optional fields/resources | Không bịa reference/paidAt/ảnh; missing/foreign order không lộ detail |

Payment projections không chọn provider_payload hoặc credentials. Detail không render raw internal event.note; tracking cũ còn render note của OrderStatusHistory theo thiết kế có sẵn (các writer hiện được review chủ yếu là note khách hủy/đặt đơn). Không kết luận về nội dung arbitrary được nhập trực tiếp vào DB ngoài test.

Đây không phải penetration test đầy đủ, không bao gồm bypass DB account/network hoặc session production thật.

## D. Database / SQL Server

**Đã chạy H2; CHƯA chạy SQL Server.** Không có biến TV3_TEST_JDBC_URL trong environment. Cả 25 test CheckoutSqlServerIntegrationTest bị điều kiện opt-in bỏ qua, không phải pass. Không dùng datasource ứng dụng thay thế.

H2 fixture kiểm tra JPQL/projection, ownership, join/count/paging và số query. Nó không tái tạo đầy đủ SQL Server FK/CHECK/triggers/isolation. Các quan hệ User → Order → ShopOrder → Item, Order → Payment, ShopOrder → Delivery và các constraints trong script `D:/S5/SWP391/Database/vietfresh_hub_script.sql` đã được đọc ở mức source; chưa xác nhận schema nào đang áp dụng trên server thực.

Không tìm thấy trigger/procedure trong script cung cấp tự đồng bộ PAID/payment_status hoặc nối delivery completion vào SALE; không lấy seed PAID làm bằng chứng processing runtime.

### Cấu hình chính xác để chạy 25 test an toàn

Chuẩn bị qua quy trình test một DB **riêng, dùng một lần**, có:

1. Tên `VietFreshHub_TV3_Test_` + **32 ký tự hex chữ thường**.
2. Database extended property `TV3_TASK3_TEST`, giá trị đúng bằng tên DB đó.
3. Đúng **60 user tables** theo guard của test hiện tại; schema và seed tương ứng.
4. Seed có ít nhất 2 variant purchasable thuộc 2 shop OPEN khác nhau, đủ stock; COD ACTIVE; địa chỉ có tọa độ hợp lệ; role CUSTOMER. Test tạo fixture stock riêng nhưng vẫn cần seed ban đầu này.
5. SQL login/quyền chỉ dành cho DB test. Schema/seed phải được chuẩn bị trên đúng DB test, không dùng nguyên lệnh tạo/drop nếu chúng trỏ DB ứng dụng.

Đặt 3 biến môi trường trong phiên PowerShell chạy test (không ghi credentials thật vào repo/báo cáo):

```powershell
$env:TV3_TEST_JDBC_URL = 'jdbc:sqlserver://<TEST_HOST>:1433;databaseName=VietFreshHub_TV3_Test_<32hex>;encrypt=true;trustServerCertificate=true;'
$env:TV3_TEST_SQL_USER = '<test-only-login>'
$env:TV3_TEST_SQL_PASSWORD = '<test-only-secret>'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd '-DargLine=-javaagent:C:\Users\Admin\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar' '-Dtest=CheckoutSqlServerIntegrationTest' test
```

Host/DB/login ở trên là placeholder, không phải cấu hình đang tồn tại. Với DB test có certificate hợp lệ có thể dùng trustServerCertificate=false. Guard kiểm tra URL rồi db_name(), extended property và table count; **không sửa guard để cho chạy trên DB app**. Bộ opt-in có INSERT/UPDATE/concurrency tests và không tự dọn sạch toàn bộ dữ liệu, nên DB này phải disposable.

Các kiểm tra quan trọng còn chưa thực thi trên SQL Server: checkout commit/rollback, duplicate confirm/concurrent last-stock, cancellation/reorder concurrency, rollback sau release, locks và execution thực của JPQL/read pages. Không thay chúng bằng khẳng định H2 đã chứng minh cùng hành vi.

## E. Performance review

Đo cold persistence context trên H2 qua controller thật, auth identity/role mock, view resolver model-only. Fixture 2 owned orders, 1 COD order có 4 items và 1 order nhiều shop:

| Page/read | Prepared statements đo được | Phạm vi và diễn giải |
|---|---:|---|
| My Orders + summaries + cart badge | 6 | 1 page query + 4 batch summary + 1 badge; ít hơn page size nên Spring Data bỏ count; trang đầy có thể thêm count |
| Payment History + cart badge | 2 | 1 projection page + 1 badge; trang đầy có thể thêm count |
| Order Detail HTTP data assembly | 17 | 7 detail queries, các reads tracking/actions có sẵn và badge; có lặp đọc order/children/payment/delivery |
| Tracking | 5 | order, shop graph, delivery, delivery history, order history; chưa tính staff-name service/cart thật |

`DeliveryAssignmentService.getTrackingStaff` được mock trong phép đo; source của nó dùng một query có fetch staff. CartService.getActiveCart được mock ở tracking; source cũ có entity graph và batch stock nhưng toàn bộ cart/catalog/image loading không được profile trong phép đo này. Vì vậy **không gọi các số trên là tổng query production của cả request bao gồm authentication/navigation**.

- Summary giữ 4 batch queries, giới hạn 10 order IDs; không query từng card/item. Detail projection giữ số query cố định, không lazy-load entity từ Thymeleaf.
- Collection fetch chỉ trên shop IDs thuộc page, không phân trang trực tiếp trên collection fetch. History/count không join items nên không nhân payment/order rows; distinct shop fetch de-duplicate root.
- Per-order items/payments/events vẫn có thể lớn; chỉ giới hạn số order trên page, không giới hạn số con được load. Đây là rủi ro quy mô, chưa đo tải gây lỗi.
- Detail có đọc lặp giữa query/lifecycle/tracking. Đã đo được sự lặp nhưng chưa có latency/load evidence; không rewrite hoặc tối ưu mạo hiểm trong phase validation. Có thể profile và hợp nhất read plan trong task riêng.
- Chưa có SQL Server execution plans, p95/concurrency/load benchmark hoặc coverage report. Không suy hiệu năng production từ số test.

Metric artifact: `.document-analysis/phase6-query-counts.json`.

## F. UI regression

Playwright/Chrome headless tải HTML **được Thymeleaf test render từ source hiện tại**, không phải mockup tự dựng. Kiểm tra 5 trang × 4 viewport = **20 cấu hình**: 1440, 768, 390, 320 px.

- My Orders, Payment History, Detail, Tracking, Confirmation không overflow ngang **phần main** trong fixture.
- Đúng số shop/card, 8 filter options, nhãn payment không thành order stage, COD note đúng record, timestamps/currency.
- Form POST có CSRF hidden input; disclosure hủy mở được; timeline detail chỉ hiện verified events.
- Link order/detail/tracking là đường dẫn endpoint có thật; navigation chính còn được exercise bởi HTTP integration model test.
- Empty/reset, active filter/pagination, optional fields, tiền lẻ, escaping và conditional actions được kiểm tra trong MVC/Thymeleaf tests.
- Đã xem ảnh desktop/mobile, gồm tracking và confirmation.

**Giới hạn:** fixtures UI không phải live session/DB; một số fixture cố ý tách backend eligibility khỏi trạng thái để kiểm tra template tôn trọng quyết định service, không dùng ảnh fixture để chứng minh quy tắc nghiệp vụ. Đồng bộ thực giữa service/eligibility/amount được kiểm tra riêng bằng fixture JPA. Browser chạy offline, external CDN bị chặn; không tuyên bố đã kiểm chứng global header/footer/CDN live hoặc mọi URL ảnh trong dữ liệu thật.

Artifacts: `.document-analysis/phase6-browser-checks.json` và `.document-analysis/phase6-{orders,payments,detail,tracking,confirmation}-{1440,390}.png`.

## G. Business consistency audit — chưa triển khai trong Phase 6

“Đã xác nhận thiếu” dưới đây là theo **source Java + DDL được cung cấp**, không phải kiểm tra server live. Đây là nghiệp vụ chưa hoàn tất có từ trước, không đánh đồng với hồi quy UI Phases 2–5. Người dùng chỉ phụ trách cart/checkout/payment customer; online đã bàn giao, không tự gán lại công việc đó.

| ID | Hiện có và hành vi còn thiếu đã xác nhận | Rủi ro nghiệp vụ | File/module liên quan | Chủ trì cần phối hợp | Khuyến nghị | Ưu tiên | Chặn production? |
|---|---|---|---|---|---|---|---|
| A. Parent/ShopOrder | Parent chưa đồng bộ đầy đủ confirm/prepare; completeDeliveredShopOrder coi cả CANCELLED là chưa complete. Display Phase 2 có thể hoàn tất phần còn lại nhưng persisted parent vẫn PARTIALLY_COMPLETED. | Staff/report/API khác đọc parent có thể lệch customer display; đơn chưa đóng thống nhất. | order/service/impl/OrderServiceImpl.java; CustomerOrderStageQuery.java | Order/Fulfillment | Chốt aggregation theo multi-shop, gồm COMPLETE + CANCEL; cập nhật parent và history atomically tại transitions, không sửa enums chỉ để đổi UI. | P1 | Có, để nghiệm thu full fulfillment; UI đọc hiện có biện pháp tránh hoàn tất sớm. |
| B. Verified COD collection | Checkout tạo payment PENDING/Order UNPAID. markDelivered hoàn tất giao/ShopOrder nhưng không có writer PAID/paidAt; PaymentService chỉ kiểm tra isCodOrder. dailyCod suy từ deliveries và shop totals. | Thiếu bằng chứng thực thu và đối soát; giao xong nhưng payment vẫn chờ, số tiền vận hành có thể bị hiểu là đã thu. | payment/service/PaymentService.java; delivery/service/DeliveryStaffActionService.java; delivery/repository/DeliveryStaffHistoryRepository.java | Payment + Delivery | Chốt người xác nhận thu, số tiền/phần shop, evidence, idempotency, cùng transaction/local event contract; tách phải thu và đã xác nhận thu. | P0 | Có cho COD vận hành đầy đủ. |
| C. Order.paymentStatus | Có aggregate field và nhãn; không tìm thấy writer đồng bộ từ payments/collection/refunds trong Java hoặc trigger DDL cung cấp. | Tổng hợp UNPAID dù có record PAID, hoặc khác trạng thái record; summary/report/eligibility cần reconciliation. | order/entity/Order.java; payment/entity/Payment.java; checkout/service/impl/CheckoutServiceImpl.java; CustomerOrderQueryServiceImpl.java | Payment + Order | Định nghĩa aggregate nhiều attempts/multi-shop/refunds; cập nhật idempotent, không lấy latest attempt hoặc tổng mọi records làm số đã thu. | P1 | Có cho tính nhất quán tài chính production. |
| D. RESERVED → SOLD | Có StockAllocationServiceImpl.sell, giảm physical/reserved và ghi SALE; tìm thấy declaration/implementation nhưng không caller trong main source. Delivery completion không nối bước này. | Tồn vật lý/audit SALE sai hoặc reservation treo sau bán; rủi ro xử lý release về sau sai nếu không rõ lifecycle. | inventory/service/impl/StockAllocationServiceImpl.java; delivery/service/DeliveryStaffActionService.java; OrderServiceImpl.java | Inventory + Delivery/Order | Chốt thời điểm xuất bán và actor, atomicity/rollback/idempotency; phân biệt release hàng giữ với nhập lại hàng đã SOLD. | P0 | Có cho tính đúng tồn kho vận hành. |
| E. Failed delivery/return closure | markFailed chờ hàng về, không release; confirmGoodsReturn đã release RESERVED và đóng assignment, ghi marker, nhưng delivery vẫn FAILED/ShopOrder còn OUT_FOR_DELIVERY và chưa đóng order. | Hàng có thể đã về/đã nhả giữ nhưng customer còn SHIPPING; thiếu kết thúc/đối soát phần đơn. | delivery/service/DeliveryAssignmentService.java; DeliveryStaffActionService.java; order/service/OrderInventoryReleaseService.java | Delivery + Order + Inventory | Giữ quyết định nhả sau xác nhận hàng về; chốt trạng thái đóng/hủy/đối soát, cập nhật history; không tự gắn REFUNDED từ trả hàng vật lý. | P1 | Có cho xử lý ngoại lệ giao thất bại đầy đủ. |
| F. Online callbacks/retries | Không thấy initiate/callback/webhook/retry gateway production; checkout đang require COD. Có enum và read records không đồng nghĩa processing online hoạt động. | Nếu bật online sớm có nguy cơ thu/ghi nhận sai hoặc callback trùng/giả. | checkout/service/impl/CheckoutServiceImpl.java; payment/entity/Payment.java; chủ tích hợp gateway | Thành viên đã nhận online payment | Hợp đồng attempts, signed callback, provider event dedup, transaction/reference, idempotency, retry/aggregate trước khi bật kênh. | P1 | Chặn kênh online; không bắt buộc cho bản COD-only đang tắt online. |
| G. Return/refund | Có REFUNDED/PARTIALLY_REFUNDED để đọc; không có customer request/approval/refund executor/reconciliation hoàn chỉnh. Cancellation paid bị chặn để không hoàn tiền giả. | Không thể cam kết trả hàng/hoàn tiền end-to-end; enum/seed có thể bị nhầm là chức năng đã vận hành. | payment/entity/Payment.java; CustomerOrderServiceImpl.cancellationHint; phần module refund chưa có trong source | Payment/Returns + Order, theo phạm vi sản phẩm đã chốt | Chỉ triển khai khi được chốt scope; phân quyền, evidence, amounts, statuses và reconciliation riêng; giữ disabled/không hiển thị action unsupported. | P2 | Có nếu quảng bá/cho phép refund/return; không tự coi là hạng mục bắt buộc của user hoặc COD-only cancellation chưa thu. |

Thứ tự phối hợp đề nghị: B/D trước; sau đó C/A/E; F trước khi mở online; G theo scope được phê duyệt. Không thực hiện các workflow này trong Phase 6.

## H. Files sửa trong Phase 6 và readiness

| File | Lý do |
|---|---|
| src/test/java/VietFreshHub/order/CustomerOrderHistorySummaryTest.java | Mở rộng fixture H2, navigation 5 trang qua services/security thật, ownership direct actions, snapshot/money/eligibility consistency, đo query cold context; không thay đổi kỳ vọng nghiệp vụ cũ |
| src/test/java/VietFreshHub/order/CustomerOrderDetailViewTest.java | Render/export confirmation và tracking thật để kiểm tra responsive cùng các trang khác |
| src/test/java/VietFreshHub/customer/CustomerHttpSecurityTest.java | Bổ sung confirmation/roles, invalid CSRF cancel/reorder, GET mutation bị chặn, account inactive khi đọc |
| src/test/java/VietFreshHub/checkout/CheckoutSqlServerIntegrationTest.java | Sửa hai kỳ vọng stale theo Phase 2; tests vẫn opt-in, chưa chạy SQL Server |
| docs/phase6-order-payment-integration-review.md | Báo cáo kiểm thử, audit, ranh giới bằng chứng và cấu hình SQL Server test |

| Phần | Đánh giá readiness |
|---|---|
| Customer UI | Đạt các regression/Thymeleaf/viewport checks với fixtures; cần nghiệm thu live header/assets/session và dữ liệu thật |
| Backend read/query | Đạt H2/JPA/ownership/paging trong trường hợp đã thử; chưa xác nhận SQL Server runtime/plan/load |
| Payment workflow | Chưa sẵn sàng toàn luồng: verified COD và aggregate còn thiếu; online vẫn ngoài scope user và chưa hoạt động |
| Delivery workflow | Có phân công/giao/failure cơ bản; closure sau hàng trả về và parent sync chưa khép kín |
| Inventory consistency | Reserve/release có source + unit checks; SALE integration và SQL Server locking/rollback còn là blocker |
| Overall production | **CHƯA SẴN SÀNG** cho cam kết e-commerce end-to-end. UI/read improvements không thay thế settlement/stock/fulfillment contracts và SQL Server integration |

## I. Phạm vi source Phases 2–5 đã rà soát

Danh sách tổng hợp 36 paths từ các báo cáo Phase 2–5; đối chiếu Git diff, untracked files và source hiện tại. Không coi tất cả Git diff của workspace là thay đổi của Phases 2–5. Thay đổi cart/catalog/auth/stock có sẵn từ lượt khác được giữ nguyên.

- `src/main/java/VietFreshHub/order/controller/CustomerOrderController.java`
- `src/main/java/VietFreshHub/order/controller/CustomerOrderDetailsController.java`
- `src/main/java/VietFreshHub/order/dto/CustomerOrderFilterRequest.java`
- `src/main/java/VietFreshHub/order/dto/CustomerOrderStage.java`
- `src/main/java/VietFreshHub/order/dto/OrderDetailsResponse.java`
- `src/main/java/VietFreshHub/order/dto/OrderHistorySummary.java`
- `src/main/java/VietFreshHub/order/dto/OrderListRow.java`
- `src/main/java/VietFreshHub/order/dto/OrderTrackingResponse.java`
- `src/main/java/VietFreshHub/order/repository/CustomerOrderReadRepository.java`
- `src/main/java/VietFreshHub/order/repository/CustomerOrderStageQuery.java`
- `src/main/java/VietFreshHub/order/repository/OrderDeliveryReadRepository.java`
- `src/main/java/VietFreshHub/order/repository/OrderRepository.java`
- `src/main/java/VietFreshHub/order/repository/ShopOrderRepository.java`
- `src/main/java/VietFreshHub/order/service/CustomerOrderService.java`
- `src/main/java/VietFreshHub/order/service/OrderLabels.java`
- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderQueryServiceImpl.java`
- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderServiceImpl.java`
- `src/main/java/VietFreshHub/payment/controller/CustomerPaymentHistoryController.java`
- `src/main/java/VietFreshHub/payment/dto/PaymentHistoryFilter.java`
- `src/main/java/VietFreshHub/payment/dto/PaymentHistoryRow.java`
- `src/main/java/VietFreshHub/payment/repository/CustomerPaymentReadRepository.java`
- `src/main/java/VietFreshHub/payment/repository/PaymentRepository.java`
- `src/main/java/VietFreshHub/payment/service/impl/CustomerPaymentHistoryServiceImpl.java`
- `src/main/resources/static/css/customer-orders.css`
- `src/main/resources/templates/customer/order-detail.html`
- `src/main/resources/templates/customer/orders.html`
- `src/main/resources/templates/customer/payment-history.html`
- `src/test/java/VietFreshHub/order/CustomerOrderDetailViewTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrderHistoryControllerTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrderHistorySummaryTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrderQueryServiceTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrderTrackingTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrdersViewTest.java`
- `src/test/java/VietFreshHub/payment/CustomerPaymentHistoryControllerTest.java`
- `src/test/java/VietFreshHub/payment/CustomerPaymentHistoryTest.java`
- `src/test/java/VietFreshHub/payment/PaymentOrderStageQueryTest.java`

Báo cáo đối chiếu: docs/phase2-order-payment-status-refactor.md, docs/phase3-my-orders-ui-refactor.md, docs/phase4-payment-history-ui-refactor.md, docs/phase5-order-detail-refactor.md và docs/tv3-progress-standard-review.md (bối cảnh tài liệu/Phase 1 trước đó; không có file Phase 1 riêng). Snapshot hash đầu Phase 6 lưu tại `.document-analysis/phase6-before.json`.

**Dừng sau Phase 6.** Không triển khai tiếp các nghiệp vụ trong bảng audit.
