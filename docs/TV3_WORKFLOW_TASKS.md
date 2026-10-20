# Phạm vi và trạng thái công việc TV3

**Đánh số chính thức: dùng [bảng gốc 01–17](TV3_TASK_STATUS_01_17.md).** Các “Task 06–08” trong báo cáo cũ là tên lượt bị lệch; xem bảng ánh xạ, không coi pickup/COD/notification đã xong. Lượt hiện tại thực hiện task gốc 12.

Task gốc 12 đã hoàn thành: gợi ý khả dụng dưới product detail, 10/10 test gợi ý PASS sau sửa fixture SQL; xem [Task 12](TV3_TASK_12_RESULTS.md). Tiếp tục dùng bảng gốc để theo dõi phụ thuộc và phần nghiệm thu còn thiếu.

Rà soát ngày 20/10/2026 theo tài liệu gốc và source hiện tại. Bản này thay bảng theo dõi cũ vốn mô tả nhiều file và test không còn tồn tại. Có source không đồng nghĩa đã nghiệm thu với SQL Server.

Cập nhật sau rà soát: Task 02 và Task 03 đã được triển khai; nghiệm thu trên schema `D:/Download/vietfresh_hub_script.sql` và seed `D:/Download/data.sql` mới đạt **87/87 tests (79 hồi quy + 8 SQL Server thật)** trên DB test tách biệt. Xem [Task 03](TV3_TASK_03_RESULTS.md), [workflow thực thi](TV3_IMPLEMENTATION_WORKFLOW.md) và [chuẩn bị Task 04](TV3_TASK_04_PREPARATION.md). Các mô tả tinh gọn source bên dưới là lịch sử baseline, không có nghĩa các test mới chưa tồn tại.

## Tài liệu đối chiếu

Cập nhật mới nhất Task 08: **195/195 PASS**, đã có lịch sử thanh toán Customer chỉ đọc và hợp đồng COD đề xuất. Xem [Task 08](TV3_TASK_08_RESULTS.md). Người dùng xác nhận chưa chốt quy trình thu COD nên phần ghi tiền/đối soát/hook TV4 tiếp tục chờ; chưa mở online hoặc coupon. Các mốc Task 03–07 phía dưới là lịch sử.

Cập nhật mới nhất Task 07: **179/179 PASS**, đã có security filter HTTP và 3 ca HTTP + SQL cho luồng Customer/ownership. Xem [Task 07](TV3_TASK_07_RESULTS.md). Chưa đo coverage, chưa nghiệm thu login/provider/browser và các nghiệp vụ thu/đối soát COD còn theo workflow. Các số liệu Task 03–06 phía dưới là mốc trước đó.

Cập nhật mới nhất Task 06: **157/157 PASS**, history có bộ lọc đang giao, biên ngày/UTC đã kiểm chứng trên SQL Server; reorder dùng giá/tồn hiện tại, cộng giỏ và báo món bị loại, kiểm tra thao tác đồng thời không mất số lượng. Xem [Task 06](TV3_TASK_06_RESULTS.md). Các cập nhật Task 03–05 dưới đây là mốc lịch sử; security HTTP/coverage và tích hợp COD/payment còn theo workflow.

Cập nhật mới nhất Task 05: **127/127 PASS**, tracking đã hiển thị failureReason, nhãn hẹn giao lại/hủy giao, timeline sắp theo giờ Việt Nam và không lỗi khi thiếu thời gian. Xem [kết quả Task 05](TV3_TASK_05_RESULTS.md). Tiếp theo: history/reorder; các cập nhật Task 03/04 bên dưới là mốc trước đó.

Cập nhật mới nhất Task 04: **108/108 test PASS**, đã cho hủy Pending/Confirmed trước Preparing, đồng nhất UI và kiểm thử trả kho/rollback/cạnh tranh chuẩn bị hàng. Xem [kết quả Task 04](TV3_TASK_04_RESULTS.md). Tiếp theo: Task 05 tracking customer. Các số 87 bên dưới ghi nhận mốc Task 03.

- Feature Tree: `C:/Users/Admin/Downloads/Group 1 - Feature Tree  - VietFresh Hub (2).docx`.
- SRS: `C:/Users/Admin/Downloads/SE2056-JV - Group 1  - SRS.docx`.
- SDS: `C:/Users/Admin/Downloads/SE2056-JV - Group 1  - SDS.docx`.
- Coding Standards: `C:/Users/Admin/Downloads/VietFreshHub_Coding_Standards.md`.
- Schema và seed: `D:/S5/SWP391/Database/VietFreshHub_Schema.sql`, `VietFreshHub_SeedData.sql`.
- Phân công đã đối chiếu trong chat “Phân tích tài liệu dự án” và `D:/S5/SWP391/Project/VietFreshHub/docs/TV3_IMPLEMENTATION_PLAN.md`.

Không đổi database, framework, cấu hình môi trường, quy tắc giao hàng hoặc code của thành viên khác trong đợt này. Tài liệu là căn cứ nghiệp vụ, không phải lệnh chạy DDL.

## Phạm vi trách nhiệm

| Phần | TV3 phụ trách | Giới hạn |
| --- | --- | --- |
| FE-04 | Thêm, sửa số lượng, xóa, clear giỏ; kiểm tra giá, tồn và khả năng mua | Gọi catalog/kho hiện có; không sửa product/inventory |
| FE-05.1 | Preview checkout, chọn địa chỉ/phương thức, review, tách đơn theo shop, xác nhận | Địa chỉ dùng dịch vụ TV1; phí vận chuyển dùng luồng VietMap hiện tại |
| FE-05.2 | Lịch sử, chi tiết, tracking, hủy đơn đủ điều kiện và mua lại | Tracking đọc trạng thái từ luồng xử lý đơn/giao hàng |
| FE-06 | Khởi tạo, xác minh, theo dõi thanh toán và lịch sử | Online/lịch sử đã gỡ theo yêu cầu; giữ nền tảng COD và cấu hình môi trường |
| FE-013.3–013.4 | Giám sát đơn, payment và payment_transactions cho admin | Không tự thêm payout hoặc điều phối giao hàng |

FE-05.3 phía cửa hàng, FE-07 delivery, auth/security, sổ địa chỉ tài khoản, catalog/product, inventory, coupon/promotion và notification là các điểm phối hợp. Hiểu điểm gọi không đồng nghĩa có quyền sửa toàn bộ package. Không suy ra chủ sở hữu chỉ bằng tên thư mục hoặc tác giả commit merge.

## Luồng hiện đang có

1. Khách xem/tìm sản phẩm qua catalog đọc; controller giỏ nhận customer từ Authentication.
2. Cart service kiểm tra product/shop/variant, tồn và giá hiện tại; các mutation khóa user.
3. Checkout lấy món đã chọn, địa chỉ thuộc khách và báo giá từng shop. Draft ở session, fingerprint kiểm tra thay đổi, token chống tạo đơn trùng.
4. Confirm COD chạy trong transaction: kiểm tra lại, tạo Order/ShopOrder, snapshot địa chỉ và dòng hàng, giữ kho FEFO, ghi allocation/RESERVE, tạo Payment PENDING và xử lý món đã chọn trong giỏ.
5. OrderPlacedEvent được phát. Chưa có consumer tương ứng trong source; không coi việc phát event là đã gửi thông báo.
6. Cửa hàng/giao hàng tiếp tục qua code hiện có của thành viên khác. TV3 đọc dữ liệu để hiển thị lịch sử/tracking.
7. Hủy đơn kiểm tra ownership, trạng thái, payment và giao hàng dưới khóa; đối chiếu allocation/ledger, trả đúng kho, ghi lịch sử và phát OrderCancelledEvent. Mua lại dùng hàng/giá hiện tại.

SRS UC-17 quy định giữ kho khi đặt đơn; không hiểu mô tả tổng quan BF-04 là được trừ hàng hai lần. Thời điểm RESERVED chuyển SOLD phải thống nhất với luồng pickup của TV2/TV4.

## Toàn bộ backlog TV3 sau rà soát

| Task | Hiện trạng xác minh từ source | Việc còn lại |
| --- | --- | --- |
| TV3-01 Đối chiếu tài liệu | Đã đối chiếu Feature Tree/SRS/SDS/schema và phạm vi | Cập nhật khi hợp đồng tích hợp thay đổi |
| TV3-02 Handoff cửa hàng | Luồng thật dùng OrderServiceImpl; bridge cũ không có caller đã xóa | Phối hợp payment gate/preparation check với người phụ trách; không sửa thay |
| TV3-03 Trạng thái đơn tổng | Code thành viên khác đã cập nhật PARTIALLY_COMPLETED/COMPLETED khi hoàn tất giao | Nghiệm thu trạng thái nhiều shop và cạnh tranh với hủy |
| TV3-04 Pickup/xuất kho | Có giữ/trả kho TV3; inventory có sell/SOLD/SALE, nhưng chưa thấy pickup gọi service này | Phối hợp TV2/TV4 kiểm tra điểm gọi và tính đúng một lần |
| TV3-05 COD | Có Payment/PaymentMethod, COD PENDING và thông tin trong chi tiết đơn | Chưa thấy handler ghi nhận thu COD/PAID; chốt thu theo shop và đối soát đơn tổng |
| TV3-06 Cart/checkout/customer order | Có CRUD giỏ, checkout một phần/đa shop, hủy, mua lại, tracking; Cart/Checkout đã nghiệm thu 8 ca SQL Server gồm rollback, confirm trùng, tranh tồn, FEFO | Tiếp tục Task 04 hủy đơn, các task customer order và security/ownership HTTP tương ứng |
| TV3-07 Coupon/promotion | Chưa tích hợp confirm/preview | Tiếp tục tạm hoãn theo yêu cầu; chờ TV5 |
| TV3-08 Online payment | Đã gỡ VNPay theo yêu cầu, giữ cấu hình môi trường | Không đánh dấu đã tích hợp chỉ vì có credential |
| TV3-09 Timeout/retry/late callback | Không có luồng online đang hoạt động | Làm cùng lần triển khai payment tiếp theo, sau khi chốt chính sách |
| TV3-10 Lịch sử thanh toán | Đã xóa route/service/repository/DTO/template theo yêu cầu | Không đánh dấu hoàn thành FE-06 |
| TV3-11 Admin monitoring | Chưa thấy endpoint TV3 giám sát orders/payments/payment_transactions | Triển khai riêng sau; giữ admin/delivery của thành viên khác |
| TV3-12 Notifications | Có OrderPlacedEvent/OrderCancelledEvent, chưa có consumer tương ứng | Phối hợp TV1 xử lý sau commit |
| TV3-13 Nghiệm thu | Có 79 test hồi quy Cart/Checkout và 8 test SQL Server trong source; chưa đo coverage | Test phần order/payment/admin còn lại, security HTTP và đo coverage theo SRS |

Không dùng số liệu của các đợt trước để kết luận source hiện tại đã được kiểm thử. Số 87 ở cập nhật mới là kết quả chạy tổng hợp thực tế sau Task 03, không phải cộng số lần chạy. Không chạy ứng dụng/full context test hoặc scheduler trên DB dùng chung để kiểm tra việc dọn code.

## Các file đã tinh gọn

| File xóa | Lý do |
| --- | --- |
| `order/service/ShopOrderWorkflowService.java` | Không có caller; trùng vai trò luồng cửa hàng đang chạy. Giữ nguyên OrderServiceImpl của thành viên khác |
| `checkout/service/ShippingQuoteService.java` | Phí cố định cũ không được gọi; checkout dùng CheckoutShippingQuoteService và ShippingFeeService |
| `static/css/customer-home.css` | Không có template tham chiếu |
| `static/css/customer-product-detail.css` | Không có template tham chiếu; recommendation tương ứng không còn trong source |
| `static/css/tv3-commerce.css` | Chỉ được fragment TV3 không sử dụng tham chiếu |
| `templates/fragments/tv3-pages.html` | Không có template sử dụng fragment |

Đường dẫn Java tính từ `src/main/java/VietFreshHub`; UI tính từ `src/main/resources`.

Đã bỏ import thừa trong code TV3, dùng import thay tên lớp đầy đủ ở controller customer, tách nhóm item/định dạng địa chỉ để phần đọc chi tiết đơn dễ theo dõi. Không đổi query, route, DTO trả về, điều kiện hủy/checkout, thứ tự khóa hoặc hợp đồng module.

## Những phần phải giữ

- Projection/entity Checkout*, OrderDelivery*, CustomerOrderReadRepository vẫn phục vụ checkout/tracking; không xóa vì trùng bảng với module khác.
- OrderDetailsResponse của customer khác OrderDetailResponse phía seller; không gộp hợp đồng.
- Payment/PaymentMethod và repository/service COD vẫn được checkout, hủy đơn, delivery sử dụng.
- OrderPlacedEvent/OrderCancelledEvent là điểm bàn giao đã được phát; chưa có consumer không phải lý do xóa.
- StockCheckResponse/getVariantStock ở ranh giới cart/catalog được giữ để không dọn lan hợp đồng module khác.
- Cấu hình môi trường application.properties và IntelliJ giữ nguyên.

## Giới hạn kiểm chứng

Đợt này tinh gọn source và kiểm tra biên dịch. Không kết luận về rollback/locking SQL Server, payment thật, UI responsive hay coverage. Phần nghiệp vụ chưa làm vẫn là backlog, không phải file thừa để xóa.
