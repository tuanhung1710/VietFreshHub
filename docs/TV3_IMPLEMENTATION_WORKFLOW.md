# Workflow triển khai TV3 — VietFresh Hub

Ngày lập: **20/10/2026**. Baseline: working tree hiện tại tại `D:/S5/SWP391/FinalProject`, bao gồm các thay đổi chưa commit. Đây là kế hoạch thực hiện, không xác nhận các task chưa làm đã hoàn thành.

Cập nhật thực thi: Task 02 (unit test draft/checkout COD, phần A2/B2) đã có 29 test PASS; xem [TV3_TASK_02_RESULTS.md](TV3_TASK_02_RESULTS.md). Các nhận xét “chưa có test” bên dưới mô tả baseline khi lập kế hoạch, không phải trạng thái sau Task 02. Chưa nghiệm thu DB hoặc đo coverage.

Cập nhật Task 03: đã sửa phục hồi lỗi persistence/commit ở POST checkout và kiểm tra UI. Sau khi nhận schema/seed mới ở `D:/Download`, đã nghiệm thu **87/87 tests (79 hồi quy + 8 SQL Server thật)** trên DB tách biệt, gồm rollback, confirm trùng, tranh tồn, FEFO và đa shop. Task 03 đóng trong phạm vi Cart/Checkout này; không đồng nghĩa đã làm thu COD/giao hàng/notification/coverage. Xem [TV3_TASK_03_RESULTS.md](TV3_TASK_03_RESULTS.md). Chuẩn bị tiếp: [Task 04](TV3_TASK_04_PREPARATION.md).

## 1. Căn cứ và nguyên tắc đối chiếu

Cập nhật mới nhất Task 08: đã triển khai D1 lịch sử thanh toán Customer tại `/customer/payments`, bộ lọc/phân trang/ownership, nhãn COD chờ thu và whitelist dữ liệu. **195/195 PASS**, xem [Task 08](TV3_TASK_08_RESULTS.md). Theo xác nhận mới của người dùng, nhóm chưa chốt thu COD: chỉ chuẩn bị [hợp đồng tích hợp](TV3_COD_INTEGRATION_CONTRACT.md), chưa triển khai phần ghi C3 hoặc hook TV4. Online/coupon vẫn chưa bật. Đây là phạm vi Task 08 đã hoàn thành, không phải toàn bộ FE-06/COD hoàn chỉnh.

Cập nhật mới nhất Task 07: đã bổ sung HTTP integration dùng SecurityConfig/filter/controller thật; kiểm tra role, session bị khóa/đổi role, CSRF, ownership đơn/địa chỉ/draft và luồng Cart → COD checkout → history/detail/tracking → reorder/cancel với SQL thật. **179/179 PASS**, xem [Task 07](TV3_TASK_07_RESULTS.md). Các nhận xét “chưa có security HTTP” ở mốc trước là lịch sử; phạm vi hiện tại không bao gồm login/OIDC thật hoặc browser/render toàn bộ UI. Tiếp theo là phần payment/COD reconciliation và lịch sử thanh toán theo giai đoạn C, giữ các giới hạn phối hợp module.

Cập nhật mới nhất Task 06: hoàn thiện/kiểm chứng history và reorder (B3/B6 trong phạm vi này), bỏ hai filter cũ và thêm “Đang giao” theo dữ liệu đơn con/delivery, sửa biên ngày DATETIME2 và mapping UTC `Order.placedAt`, làm rõ các bước mua lại. Nghiệm thu **157/157 PASS**, gồm 26 unit/controller và 4 SQL integration mới. Xem [kết quả Task 06](TV3_TASK_06_RESULTS.md). Phần security HTTP, coverage và các hợp đồng COD/payment/stock/notification trong giai đoạn C vẫn còn; không coi hoàn tất customer/TV3 chỉ từ số test này.

Cập nhật mới nhất Task 05: đã đưa lý do giao thất bại tới DTO/UI, bổ sung nhãn hẹn giao lại/hủy giao, xử lý timeline thiếu thời gian và thứ tự xác nhận. Bộ hồi quy **127/127 PASS**, gồm 18 tracking unit/template và 1 ca SQL tracking mới; xem [kết quả Task 05](TV3_TASK_05_RESULTS.md). Tiếp theo: history/reorder customer theo UC-24/GBR-26. Notification và snapshot lý do từng lần thử còn phụ thuộc TV1/TV4; không coi timeline trạng thái là bằng chứng đã gửi thông báo.

Cập nhật Task 04: hủy đơn Pending/Confirmed trước Preparing và hướng dẫn UI đã đồng nhất UC-22/GBR-25. Nghiệm thu **108/108 test PASS**, gồm 7 ca SQL hủy đơn mới và 14 unit test hủy đơn; xem [kết quả Task 04](TV3_TASK_04_RESULTS.md). Task tiếp theo là Task 05 tracking customer, trước hết lý do giao thất bại và timeline. Coupon/refund/online vẫn chưa mở theo phạm vi đã thống nhất.

| Nguồn | Dùng để xác định |
| --- | --- |
| `C:/Users/Admin/Downloads/Group 1 - Feature Tree  - VietFresh Hub (2).docx` | Phạm vi FE-04, FE-05.1, FE-05.2, FE-06 và phần FE-013.3–013.4 được giao TV3 |
| `C:/Users/Admin/Downloads/SE2056-JV - Group 1  - SRS.docx` | Use case, business rule, thông báo, phân quyền và yêu cầu kiểm thử |
| `C:/Users/Admin/Downloads/SE2056-JV - Group 1  - SDS.docx` | Kiến trúc nhiều lớp, VNPAY, mô hình orders/shop_orders/payments/payment_transactions |
| `C:/Users/Admin/Downloads/VietFreshHub_Coding_Standards.md` | Java 21/Spring Boot hiện có, DTO, BigDecimal, transaction, session/security và chuẩn code |
| `D:/S5/SWP391/Database/VietFreshHub_Schema.sql` | Cột, constraint, trạng thái DB và kiểu dữ liệu thực tế |
| `D:/S5/SWP391/Database/VietFreshHub_SeedData.sql` | Dữ liệu tham chiếu; payment PAID có sẵn trong seed không chứng minh ứng dụng biết thu tiền |
| Source hiện tại và `docs/TV3_WORKFLOW_TASKS.md` | Chức năng đang tồn tại và các điểm tích hợp cần tiếp tục xác minh |

SRS có chỗ đánh số use case không đồng nhất giữa bảng tổng quan và phần mô tả chi tiết. Workflow dùng số của **phần mô tả chi tiết kèm tên use case**: UC-17 Checkout; UC-18 Apply Promotion/Coupon; UC-19 Make Payment; UC-20 View Payment History; UC-21 View Order Details; UC-22 Cancel Order; UC-23 Track Order; UC-24 View Order History/Re-order.

Nếu code khác tài liệu, ghi nhận chênh lệch; không tự coi code là yêu cầu nghiệp vụ mới. Nếu nhóm đã có quyết định mới được người dùng xác nhận, cập nhật căn cứ trước khi triển khai. Tài liệu không phải lệnh chạy SQL hay sửa module khác.

## 2. Ranh giới công việc

- Ưu tiên **Customer trước, admin sau**, theo yêu cầu người dùng.
- TV3 làm cart, checkout, customer order và payment; chỉ đọc/gọi hợp đồng product, inventory, address, delivery, notification hiện có.
- Không tự sửa auth/security, product/inventory, luồng cửa hàng/giao hàng, coupon/promotion hoặc module khác. Điểm cần hook ở module khác được ghi thành đầu việc bàn giao cho người phụ trách, chỉ tích hợp sau khi có thống nhất.
- Giữ Java 21, Spring MVC/Thymeleaf, HTTP session, SQL Server và cấu hình môi trường. Không coi tên package là bằng chứng duy nhất về quyền sở hữu.
- Coupon tạm hoãn theo chỉ đạo hiện tại; FE-04.2/FE-05.1 liên quan coupon vẫn là yêu cầu chưa hoàn thành.
- Online chưa bật. Không dùng cấu hình VNPAY còn trong properties để đánh dấu FE-06 đã làm.
- Gợi ý sản phẩm và bộ lọc “Đang giao” là yêu cầu UX bổ sung trong hội thoại; không thay thế nghiệp vụ bắt buộc trong SRS.

## 3. Baseline: giữ lại những gì đã có

| Phần | Hiện có trong source | Chưa thể kết luận |
| --- | --- | --- |
| Cart | CRUD, cộng số lượng, tổng tiền, kiểm tra giá/tồn/catalog và khóa user khi mutation | Đúng trong mọi tình huống cạnh tranh DB |
| Checkout | Chọn món, địa chỉ thuộc khách, VietMap theo shop, draft/fingerprint/token, COD, tách shop, snapshot | Rollback và chống tạo trùng đã nghiệm thu SQL Server |
| Kho checkout/hủy | FEFO, RESERVED/RESERVE; đối chiếu allocation/ledger khi RELEASE | Pickup đã xuất bán SOLD/SALE xuyên suốt |
| Customer order | History, detail, ownership, cancel, reorder, tracking | Hoàn toàn khớp SRS và đồng bộ đơn tổng/đơn con |
| Payment | Payment/PaymentMethod, COD PENDING, thông tin payment trong detail, `PaymentService.isCodOrder` | Thu COD, payment history riêng hoặc online đã chạy |
| Notification | Phát OrderPlacedEvent/OrderCancelledEvent | Consumer đặt/hủy đơn đã hoạt động |
| Test | Không có file test trong `src/test` tại baseline | Các số test của đợt trước còn áp dụng hoặc đã đạt coverage |

Các điểm bắt đầu đọc code:

- `src/main/java/VietFreshHub/cart/service/impl/CartServiceImpl.java`
- `src/main/java/VietFreshHub/checkout/service/CheckoutServiceImpl.java`
- `src/main/java/VietFreshHub/checkout/service/CheckoutShippingQuoteService.java`
- `src/main/java/VietFreshHub/checkout/service/InventoryReservationService.java`
- `src/main/java/VietFreshHub/order/service/CustomerOrderServiceImpl.java`
- `src/main/java/VietFreshHub/order/service/CustomerOrderQueryServiceImpl.java`
- `src/main/java/VietFreshHub/order/service/OrderInventoryReleaseService.java`
- `src/main/java/VietFreshHub/payment/service/PaymentService.java`

## 4. Luồng nghiệp vụ đích và các điểm bàn giao

```text
Customer xem sản phẩm → giỏ → chọn món → chọn địa chỉ → báo phí từng shop
  → review giá/tồn/phí/phương thức → confirm trong transaction
  → Order + ShopOrder + snapshot + giữ kho + Payment PENDING
  → commit → consumer thông báo đặt hàng
  → cửa hàng xác nhận → chuẩn bị → sẵn sàng giao
  → pickup + xuất bán đúng một lần [phối hợp TV2/TV4]
  → giao thành công/thất bại + lịch sử [TV4]
  → ghi nhận thu COD theo bằng chứng thu tiền [TV3 phối hợp TV4]
  → đối chiếu payment và trạng thái tổng → Customer theo dõi/lịch sử

Nhánh hủy đủ điều kiện:
  ownership → khóa/kiểm tra lại trạng thái và payment
  → release đúng allocation + lịch sử → commit → thông báo hủy

Nhánh online sau này:
  attempt có reference/amount/deadline → gateway → callback xác minh
  → cập nhật idempotent → PAID hoặc FAILED/timeout
  → thất bại/hết hạn: xử lý đơn và giải phóng kho theo GBR-16/29
  → kết quả đến muộn: đối soát, không tự hồi sinh đơn/hàng đã giải phóng
```

Stock được **giữ khi đặt đơn** theo UC-17/GBR-16. Không trừ vật lý hai lần vì mô tả BF-04 dùng từ “deducted”. “Verified” trong SRS cần ánh xạ sang trạng thái xác minh/PAID phù hợp schema, không thêm enum VERIFIED vào DB tùy tiện.

## 5. Backlog có thứ tự, đầu ra và điều kiện nghiệm thu

Trạng thái của các task dưới đây là **cần thực hiện/hoàn thiện**, trừ phần baseline đã nêu. “Độc lập” nghĩa có thể làm trong phạm vi TV3 mà không sửa code thành viên khác.

### Giai đoạn A — Chốt hợp đồng và dựng kiểm thử nền

| Task | Việc thực hiện | Căn cứ | Điều kiện xong / phụ thuộc |
| --- | --- | --- | --- |
| A1 — Bảng đối chiếu yêu cầu | Đối chiếu từng chức năng với route/service/query/UI hiện có; ghi mọi khác biệt và chủ sở hữu điểm tích hợp | Feature Tree/SRS/SDS | Có danh sách đã có/một phần/chưa có; không dùng kết quả test cũ. Độc lập |
| A2 — Test hành vi nền | Tạo lại fixture và unit test cart/draft/checkout/cancel/reorder theo API hiện tại; không phục hồi máy móc test cũ | NFR-MAINT-01; FE-04/05 | Test chạy bằng Java 21, không cần app context/DB dùng chung. Test không chỉ mô phỏng lại implementation |
| A3 — Hợp đồng trạng thái/tiền/kho | Gửi bảng đề xuất trong mục 6 cho người phụ trách; xác định ai cập nhật Order tổng, thời điểm SALE và ghi nhận COD | GBR-16/23/24/27; BF-05 | Có quy tắc cho đa shop, giao thất bại, shop bị hủy và số tiền đã thu. Chưa sửa module khác khi chưa thống nhất |

A2 chạy song song về mặt công việc với việc chốt A3, nhưng không đánh dấu logic hiện tại “đúng SRS” chỉ vì test baseline pass. Test phải tách hành vi đang có khỏi yêu cầu đích còn thiếu.

### Giai đoạn B — Hoàn thiện Customer, có thể làm trước

| Task | Việc thực hiện | Căn cứ | Nghiệm thu |
| --- | --- | --- | --- |
| B1 — Cart validation | Giữ CRUD; kiểm tra số lượng hợp lệ, giá đổi, hàng hết, shop đóng, variant inactive; thông báo tiếng Việt | FE-04; GBR-21 | Khách không sửa giỏ người khác; giá/tổng tiền tính server; không reserve khi chỉ thêm giỏ. Coupon loại khỏi phạm vi đợt này |
| B2 — Checkout COD | Kiểm tra món được chọn, ownership địa chỉ, dữ liệu vị trí, phí từng shop, draft nhiều tab, xác nhận lặp và snapshot | UC-17; GBR-16/23 | Một shop một ShopOrder; tổng Order khớp các phần đơn; giữ đúng kho; món chưa chọn vẫn ở giỏ; không sinh đơn thành công khi có bước thất bại |
| B3 — History/detail và bộ lọc | Giữ paging/tìm mã/ngày/ownership. Bỏ filter PROCESSING/PARTIALLY_COMPLETED, thêm “Đang giao” theo yêu cầu UX; dùng dữ liệu fulfillment thật | UC-21/24; yêu cầu hội thoại | Lọc đúng cả đơn đa shop, không đổi enum DB để thêm OUT_FOR_DELIVERY; count/paging đúng; không hiện đơn người khác. Hợp đồng trạng thái phụ thuộc A3 |
| B4 — Hủy đúng điều kiện | Đối chiếu Pending/Confirmed của SRS với code đang chỉ nhận Pending; áp dụng quy tắc đã chốt và kiểm tra lại dưới khóa | UC-22; GBR-25 | Nếu chưa có yêu cầu mới được xác nhận, lấy Pending/Confirmed làm đích. Chặn từ Preparing, chặn đơn khác/đã xuất kho; release đúng một lần; giữ lịch sử và trạng thái một chiều |
| B5 — Tracking đầy đủ | Đưa failureReason tới DTO/UI; giữ timeline có thời gian, trạng thái theo shop và thông tin staff hợp lệ | UC-23; GBR-31 | Giao thất bại thấy lý do thật; không bịa thời điểm hoặc thông báo chưa gửi; không lộ dữ liệu staff/customer ngoài phạm vi đơn |
| B6 — Mua lại | Hoàn thiện kiểm tra giá/tồn hiện tại, cộng số lượng có sẵn, báo rõ món bị loại | UC-24; GBR-26 | Không sao chép giá cũ/discount cũ; không tự giảm số lượng âm thầm; không reserve hoặc tạo đơn khi chỉ reorder |

Phí theo VietMap, công thức phí hiện tại và giới hạn 15 km là **baseline triển khai đang có**. Không trình bày chúng như yêu cầu gốc của SRS nếu chưa có căn cứ bổ sung; không đổi trong workflow này.

### Giai đoạn C — Khép kín COD và bàn giao module

| Task | Việc thực hiện | Căn cứ | Nghiệm thu / phối hợp |
| --- | --- | --- | --- |
| C1 — Đồng bộ trạng thái tổng | Hoàn thiện bảng ánh xạ Order ↔ ShopOrder ↔ Delivery, tránh đơn tổng Pending khi shop đã xử lý | GBR-23/24; UC-23/24 | Một shop/đa shop, các lần hoàn tất đồng thời, một shop hủy và một shop hoàn tất đều có kết quả xác định. Hook ghi trạng thái thuộc người phụ trách; TV3 làm phần đọc/hiển thị của mình |
| C2 — Pickup/xuất bán | Nối hợp đồng giữ kho TV3 với sell/SOLD/SALE của inventory tại thời điểm được chốt | UC-17; GBR-16; FE-07 | Không bán/trả cùng allocation hai lần; batch on-hand/reserved, allocation và ledger khớp; thao tác lặp/cạnh tranh không gây trừ dư. TV2/TV4 thực hiện hook module của họ |
| C3 — Thu và đối soát COD | TV3 xây nghiệp vụ ghi nhận tiền theo bằng chứng thu COD; chốt tiền theo từng ShopOrder và tổng payment với TV4 | FE-06.1/06.2; BF-05 | Không cho Customer tự đánh dấu PAID; không tự coi Delivered là đã thu tiền nếu chưa có hợp đồng ghi nhận. Tiền thu, paidAt, payment status và order payment status khớp; gọi lặp không thu/ghi nhận hai lần |
| C4 — Notification sau commit | Thống nhất consumer cho OrderPlacedEvent/OrderCancelledEvent với TV1; tái sử dụng notification service | BF-04/05; UC-23; phần notification liên quan | Đúng người/đúng shop, một lần theo event nghiệp vụ; transaction rollback không gửi thông báo thành công. Không sửa thay notification service |

Nếu C1–C4 đang chờ thành viên khác, tiếp tục B và phần đọc D1, không đánh dấu “Customer/COD hoàn chỉnh” trước khi các luồng xuyên module được nghiệm thu.

### Giai đoạn D — Hoàn thiện FE-06, không bật online sớm

| Task | Việc thực hiện | Căn cứ | Nghiệm thu / phụ thuộc |
| --- | --- | --- | --- |
| D1 — Lịch sử thanh toán Customer | Triển khai lại danh sách chỉ đọc theo customer, trạng thái, ngày, order, phân trang; giữ payment summary ở detail | UC-20; FE-06.2 | Chỉ payment của người đăng nhập, không trả raw provider payload/secret; phân biệt attempt với số tiền thực thu; không cộng mọi attempt thành doanh thu. Không phụ thuộc sandbox; có thể làm sau B |
| D2 — Khởi tạo online | Tạo adapter VNPAY theo SDS, attempt/reference/amount/expiry lưu server; checkout chọn phương thức chỉ khi đủ điều kiện | UC-19; FE-06.1/06.2 | Không nhận tổng tiền từ client; Customer sở hữu đơn; gửi lặp không tạo nhiều khoản thu; credential chỉ ở môi trường. Mặc định tắt; chưa có sandbox thì dừng ở code/test chuẩn bị |
| D3 — Xác minh kết quả | IPN/callback kiểm tra signature, merchant, reference, amount và kết quả; lưu payment_transactions; xử lý idempotent | UC-19; SDS bảng payments/payment_transactions | Return URL trình duyệt không tự đổi PAID; callback giả/lặp/sai tiền không làm sai đơn. Route công khai hẹp cần phối hợp TV1; không tự mở security toàn ứng dụng |
| D4 — Gate trước Preparing | Cung cấp kết quả payment để TV4 chặn online chưa Verified/PAID; COD được miễn | GBR-27 | Có test online pending/failed bị chặn, online verified được xử lý, COD vẫn đi tiếp. Áp dụng cả thao tác đơn lẻ và bulk |
| D5 — Failure/timeout/retry | Deadline từng attempt, job xử lý hết hạn, release đúng allocation và trạng thái đơn; retry an toàn và đối soát callback muộn | GBR-16/24/29 | Chốt TTL/chính sách trước; một lần release; race success–timeout–cancel không tạo PAID sai/âm kho. Không tự hồi sinh đơn đã hủy hoặc hoàn tiền không có nghiệp vụ |

Không tự đặt số phút timeout, quy tắc hoàn tiền, phí thanh toán, nhà cung cấp ví hoặc cơ chế chuyển khoản thủ công nếu tài liệu/hợp đồng chưa chốt. Chưa triển khai MoMo/ZaloPay riêng chỉ vì Feature Tree ghi e-wallet/bank transfer; VNPAY là hướng tích hợp đã có trong SDS.

### Giai đoạn E — Admin sau Customer

| Task | Việc thực hiện | Căn cứ | Nghiệm thu |
| --- | --- | --- | --- |
| E1 — Monitor orders | Danh sách/chi tiết chỉ đọc toàn hệ thống, filter/paging, trạng thái tổng và theo shop | Phần FE-013.3 TV3 được giao | ADMIN truy cập; Customer/Store Manager bị chặn; dữ liệu rõ ràng, không thay luồng xử lý shop/delivery |
| E2 — Monitor payment/transactions | Danh sách/chi tiết payment và sự kiện payment_transactions, tìm order/reference, trạng thái và thời gian | Phần FE-013.4 TV3 được giao | Không lộ secret/raw dữ liệu nhạy cảm; theo dõi được callback lặp/lỗi; không nhầm attempt với doanh thu. Chưa có dữ liệu thì hiển thị rỗng đúng |

Seller payout, điều phối giao hàng và xử lý issue thuộc người phụ trách tương ứng, không mặc nhiên mở rộng phạm vi TV3 vì cùng nhóm FE-013.

### Giai đoạn F — Nghiệm thu, không chỉ compile

| Task | Kiểm tra | Điều kiện hoàn thành |
| --- | --- | --- |
| F1 — SQL Server integration | Checkout đa shop, rollback ở bước giữ kho/payment; confirm lặp; hai khách tranh tồn; cancel so với prepare/pickup; COD thu lặp | Chạy DB test tách biệt. Sau mỗi case đối chiếu orders/shop_orders/items/payments/batches/allocations/ledger; không chạy schema/seed phá DB dùng chung |
| F2 — Security và UI | Guest/Customer/Store Manager/Admin; ownership đơn/cart/address/payment; CSRF, validation, dữ liệu được escape; mobile/desktop | Mỗi role chỉ thao tác đúng quyền; giữ dữ liệu hợp lệ khi lỗi; không tràn ngang; link/form hoạt động, không nút giả |
| F3 — Coverage và báo cáo | Đo coverage logic service; ghi unit/integration/browser riêng | Theo NFR-MAINT-01: tối thiểu 60% logic service bằng automated unit tests. Nêu phạm vi TV3 và kết quả toàn dự án riêng; test TV3 không chứng minh toàn dự án đạt 60% |
| F4 — Demo theo kịch bản | Đặt/hủy/mua lại, giao/thu COD đa shop, payment history; online khi đủ điều kiện, admin sau đó | Có bằng chứng thực thi và tồn tại các test trong source; cập nhật task đạt/chưa đạt cùng lý do |

F1–F3 phải được thực hiện dần ở mỗi giai đoạn. Không đợi viết xong toàn bộ mới test; compile không thay thế integration/coverage.

## 6. Những quyết định phải chốt trước task phụ thuộc

| Điểm | Chênh lệch hoặc thiếu hiện tại | Cách xử lý trong workflow |
| --- | --- | --- |
| Hủy Confirmed | SRS cho phép; code chỉ Pending | Ghi rõ quyết định mới nếu có. Nếu không, triển khai theo UC-22/GBR-25, kiểm tra trạng thái từng shop dưới khóa |
| Trạng thái tổng | Enum Order khác trạng thái fulfillment; cập nhật tổng chủ yếu lúc giao hoàn tất | Chốt bảng đa shop với TV4; bộ lọc “Đang giao” không phải enum DB mới; đơn hoàn tất/hủy không được lọc nhầm do delivery cũ |
| Thu COD | Đang tạo một Payment cho Order tổng, giao hàng theo ShopOrder | Chốt cách lưu từng khoản thu, xác thực người thu và khi nào tổng PAID; không tạo dữ liệu “đã thu” chỉ từ phép cộng đơn đã giao |
| Kho | TV3 reserve/release; inventory có sell, chưa thấy hook pickup | Chốt caller và actor; TV3 không truyền role giả hay bypass quyền để gọi sell |
| Online | Chưa có code gateway hiện tại; properties còn cấu hình | Chốt sandbox, public callback URL, TTL và chính sách kết quả muộn trước khi bật; chưa nghiệm thu thì vẫn COD |
| Coupon | Tạm hoãn theo người dùng | Chờ TV5 cung cấp hợp đồng eligibility/discount/usage/release; giữ backlog UC-18, không tính đã xong |

## 7. Các việc UX bổ sung, không chặn nghiệm thu nghiệp vụ lõi

- Bộ lọc “Đang giao” nằm trong B3 vì đã được người dùng yêu cầu; không đổi trạng thái dùng chung để phục vụ riêng dropdown.
- Gợi ý dưới product detail hiện không còn source: tạo task UX-01 sau B1–B6, service/query đọc riêng trong phạm vi được phép; ưu tiên liên quan, bỏ sản phẩm đang xem và không khả dụng. Chỉ gắn nhãn “phổ biến” khi có chỉ số xếp hạng thật.
- Duy trì giao diện ít bo góc, nhất quán trang Customer; không khôi phục CSS/template cũ nếu source hiện tại đã có thiết kế khác mà chưa đối chiếu.
- Không dùng UX thay cho D2–D5: hiển thị phương thức thanh toán không đồng nghĩa có luồng thanh toán.

## 8. Thứ tự thực thi đề nghị và điểm review

1. **A1 → A2**, đồng thời chuẩn bị hợp đồng **A3**. Đầu ra review: test nền và danh sách chênh lệch.
2. **B1 → B2 → B4 → B5 → B3 → B6**. B3/B4 phần phụ thuộc chờ A3 khi cần; phần UI/đọc độc lập vẫn tiến hành được. Đầu ra review: Customer đặt/hủy/theo dõi/mua lại bằng COD.
3. **C1 → C2 → C3 → C4** theo hợp đồng bàn giao; **D1** có thể làm độc lập khi đang chờ hook. Đầu ra review: COD từ đặt đơn tới xuất kho/thu tiền và thông báo.
4. **D2 → D3 → D4 → D5**, chỉ bật online sau kiểm thử sandbox và race condition. Đầu ra review: FE-06 có khởi tạo/xác minh/lịch sử/xử lý lỗi, không chỉ adapter.
5. **E1 → E2**, sau khi Customer và hợp đồng dữ liệu ổn định.
6. **F4** tổng nghiệm thu; F1–F3 đã thực hiện xuyên suốt. Coupon vẫn ghi hoãn nếu chưa nhận bàn giao; UX-01 là task bổ sung có thể làm sau B.

Mỗi task hoàn thành cần có: source/route thực, yêu cầu đối chiếu, test phù hợp, kết quả kiểm tra, hạn chế còn lại và danh sách file thay đổi đúng phạm vi. Không tự sửa module khác để “đóng task”.

## 9. Điều kiện báo cáo hoàn thành

- **Customer COD hoàn thiện:** B + C + kiểm thử tương ứng đạt; nêu riêng coupon còn hoãn. Không chỉ dựa vào việc tạo được đơn.
- **Customer đầy đủ theo tài liệu:** thêm D1–D5 và coupon khi được bàn giao; các yêu cầu chưa được chính thức loại khỏi phạm vi vẫn phải ghi thiếu.
- **TV3 hoàn thành toàn bộ:** thêm E và F, đáp ứng phạm vi FE-04/05/06 và phần admin được giao; có xác nhận các hook phối hợp đã chạy thật.
- Các task chờ sandbox/TV1/TV2/TV4/TV5 phải ghi đúng phụ thuộc, không chuyển thành “đã làm” hoặc “không cần làm”.
