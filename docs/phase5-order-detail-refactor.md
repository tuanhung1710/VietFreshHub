# Phase 5 — Customer Order Detail

Ngày: 2026-10-22. Refactor trang chi tiết hiện có, bao gồm route confirmation dùng chung template. Không tạo module mới, không sửa schema hay workflow nghiệp vụ.

## Files thay đổi trong riêng Phase 5

UI:
- `src/main/resources/templates/customer/order-detail.html`
- `src/main/resources/static/css/customer-orders.css` — chỉ nối thêm block `.order-detail-page`.

Backend đọc:
- `src/main/java/VietFreshHub/order/controller/CustomerOrderDetailsController.java`
- `src/main/java/VietFreshHub/order/dto/OrderDetailsResponse.java`
- `src/main/java/VietFreshHub/order/dto/OrderTrackingResponse.java`
- `src/main/java/VietFreshHub/order/repository/CustomerOrderReadRepository.java`
- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderQueryServiceImpl.java`
- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderServiceImpl.java` — chỉ thêm provenance cho mốc confirmation trong tracking đọc.

Tests:
- `src/test/java/VietFreshHub/order/CustomerOrderDetailViewTest.java` — mới.
- `src/test/java/VietFreshHub/order/CustomerOrderQueryServiceTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrderHistorySummaryTest.java`
- `src/test/java/VietFreshHub/order/CustomerOrderTrackingTest.java`
- `src/test/java/VietFreshHub/payment/PaymentOrderStageQueryTest.java` — thêm case multi-shop PENDING + PREPARING, không đổi mapping Phase 2.

Tài liệu: file này. Hash trước/sau xác nhận các source khác giữ nguyên so với đầu Phase 5. Không sửa My Orders/Payment History UI, OrderLabels/stage query, enum entity, global header/footer, checkout, gateway, thu COD, tồn kho, delivery transitions hoặc cấu hình bảo mật. Không commit/push.

## Giao diện

- Tổng quan: mã đơn, ngày giờ đặt Việt Nam, fulfillment stage Phase 2, trạng thái thanh toán tổng hợp đã lưu và tổng giá trị lúc đặt.
- Phần chính theo từng shop: tên, ShopOrder ID thật, badge shop, delivery status/timestamps nếu có, sản phẩm snapshot, số tiền đã lưu và lịch sử cập nhật riêng.
- Sidebar: OrderAddress snapshot, tài chính đơn và thao tác có sẵn. Mobile chuyển thành một cột.
- Payment records nằm trong section riêng, không trộn vào tiến trình giao hàng. Giữ từng ID/method/status/amount/createdAt/paidAt/reference.
- Header/footer và fragment assets giữ nguyên; giao diện xanh, nền sáng, thẻ trắng, badge có chữ, CSS chỉ áp dụng Order Detail.
- Confirmation route vẫn dùng template chung, có thông báo đặt hàng thành công, thông tin thật và actions hiện có.

## Trạng thái và tiến trình

- Overall gọi cùng `OrderRepository.findCustomerStage` với My Orders; không dùng parent status để tự tính lại trên HTML/JS.
- ShopOrder thể hiện đúng trạng thái đã lưu qua OrderLabels.shop: PENDING, CONFIRMED, PREPARING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, COMPLETED, CANCELLED.
- Delivery độc lập: OUT_FOR_DELIVERY → Đang giao; FAILED → Giao hàng thất bại; REDELIVERY_SCHEDULED → Chờ giao lại; DELIVERED → Đã giao hàng; UNASSIGNED/ASSIGNED/ACCEPTED/PICKED_UP/CANCELLED dùng nhãn OrderLabels.delivery hiện có.
- DELIVERED không tự đổi ShopOrder thành COMPLETED. FAILED không suy ra trả kho/hoàn tiền. Với một shop hoàn thành, shop còn lại đang xử lý, primary stage vẫn theo phần chưa hoàn tất.
- COMPLETED + CANCELLED: hiện rõ cả hai trạng thái; overall theo chính sách Phase 2 hoàn tất phần không bị hủy. Hiển thị ghi chú bảo toàn giá trị lúc đặt, không ngầm coi hàng hủy đã giao hoặc đã hoàn tiền.
- Không vẽ stepper tô hoàn tất các bước chỉ từ trạng thái hiện tại. Dùng tổng quan hiện tại + trạng thái từng shop + sự kiện thật có sẵn.

## Tái sử dụng timeline và dữ liệu giao hàng

Controller gọi tracking() hiện có một lần cho order có shop, ghép ShopTracking theo shopOrderId. Không tạo truy vấn lịch sử theo từng shop trong controller/template. Trạng thái parent của TrackingResponse không dùng làm primary stage, tránh parent chậm cập nhật.

Thêm `Event.verified` với constructor 4 tham số cũ giữ compatibility:
- Sự kiện đọc từ OrderStatusHistory/DeliveryHistory: verified=true, kể cả khi bản ghi có timestamp null.
- Confirmation lấy từ confirmedAt đã lưu: verified=true.
- Confirmation chỉ suy ra từ current state, không có confirmedAt/history: verified=false.

Trang detail chỉ render verified events. Event thật thiếu timestamp hiện “Chưa ghi nhận thời gian”. Event chỉ suy diễn không được trình bày như lịch sử đã xảy ra. Không tạo record DB hay timestamp mới. Endpoint/template tracking cũ vẫn giữ cách hiển thị trước đây; flag mới bổ sung provenance cho consumer detail.

Không hiển thị event.note để tránh đưa ghi chú vận hành nội bộ lên detail. Nhãn sự kiện dùng logic tracking hiện có; tên nhân viên nếu đã nằm trong nhãn lấy từ nguồn tracking có ownership, không thêm số điện thoại/email nhân viên.

Delivery projection đọc các field hiện có từ OrderDeliveryView: status, assignedAt, pickedUpAt, deliveredAt, redeliveryScheduledAt và failureReason. Chỉ hiện reason khi delivery hiện tại FAILED. Timestamps đổi UTC sang giờ Việt Nam tại service. Không có tracking number/provider/ETA đáng tin cậy trong các field đọc nên không tạo hoặc hiển thị giả các thông tin đó. Link tracking là endpoint nội bộ thật.

## Sản phẩm và tài chính

Tên sản phẩm, variant, SKU, số lượng, unitPrice, discountAmount, totalAmount lấy từ OrderItem snapshot. Thumbnail lấy từ variant hiện tại; thiếu/blank có placeholder. Không dùng tên/giá catalog hiện tại để sửa thông tin mua trước đây.

Giá trị shop lấy trực tiếp subtotal/discountTotal/shippingFee/totalAmount đã lưu; giá trị order lấy subtotal/discountTotal/shippingFee/grandTotal đã lưu. Không recompute giá trị từ giá catalog, không cộng payment attempts, không tự trừ shop CANCELLED. Chỉ hiện category giảm giá tổng mà model có; không chia voucher/shipping discount khi dữ liệu không phân biệt.

Các nhãn tài chính ghi rõ “lúc đặt” để phân biệt snapshot với số tiền còn phải thu hoặc khoản hoàn tiền. Khi có shop CANCELLED, trang nhắc rằng giá trị này không xác nhận đã hoàn tiền. Không tự tạo refund amounts/transactions.

## Thanh toán và actions

Nhãn payment dùng OrderLabels.paymentTransaction(status, methodCode) tại service. COD PENDING → Chờ thu COD và ghi chú chưa xác nhận thu; online PAID → Đã thanh toán. Các status khác giữ Phase 2/4. Không tự chọn payment mới nhất làm trạng thái toàn đơn. Aggregate paymentStatus trên overview vẫn là dữ liệu Order đã lưu, không thay thế hoặc ghi lại từ các attempts.

Mỗi PaymentInfo thêm createdAt/transactionReference; paidAt có sẵn được giữ. Chuỗi reference blank không render. Mọi timestamp chỉ hiển thị khi có; không dùng paidAt làm thời điểm hoàn tiền.

Theo dõi hiện khi có shop (timeline nội bộ hỗ trợ cả trước khi giao). Mua lại hiện khi có items; backend reorder vẫn kiểm tra giá/tồn kho hiện tại. Hủy dùng lifecycle.actions()/cancellationHint cũ, không lặp eligibility trong HTML. POST dùng th:action để giữ CSRF. Khi không được hủy, hiện hint hiện có. Không thêm retry/refund/return hoặc shipping action.

## DTO/query tối thiểu

- OrderDetailsResponse.ShopGroup thêm statusCode/DeliveryInfo; PaymentInfo thêm createdAt/reference. Constructors cũ được giữ cho compatibility với My Orders/confirmation/tests.
- CustomerOrderReadRepository thêm một projection query delivery theo orderId + customerId, không theo từng shop. Payment projection thêm hai field đã có trong schema.
- Query service map deliveries theo shopOrderId, group items như cũ, không lazy load trong template. getDetails() đo được **7 prepared statements cố định** với nhiều shop/items; so với trước thêm 1 query delivery. Đây là số query cho DTO detail, không phải tổng HTTP request.
- Controller còn tái sử dụng actions/tracking và cartBadge theo kiến trúc hiện có; các luồng này có thêm tập query riêng cố định, không bị nhân theo mỗi sản phẩm render. Không tuyên bố toàn request chỉ có 7 query.
- Kiểm tra sở hữu order diễn ra trước address/items/payment/delivery. Các projection thêm/hiện có dùng customerId + orderId. Address snapshot vẫn đọc sau kiểm tra owned order.

## Kiểm thử đã chạy

- Targeted suite ban đầu: **112 tests passed, 0 failures/errors/skipped, BUILD SUCCESS**.
- Sau bổ sung test escape/missing fields, Maven full suite: **338 tests, 313 passed, 0 failures, 0 errors, 25 skipped; BUILD SUCCESS**.
- 25 SQL Server integration tests opt-in bị bỏ qua do chưa cấu hình DB kiểm thử riêng. Không chạy trên DB ứng dụng.
- JPA/H2 thực thi query thật: parent lag, multiple shops, PENDING + PREPARING, COMPLETED + OUT_FOR_DELIVERY, COMPLETED + CANCELLED; delivery/payment projections có ownership; financial/address/item snapshots; 7 queries cho DTO detail; thumbnail không làm thay tên/giá lịch sử.
- MVC + Thymeleaf thật: 14 cases, gồm mixed shop statuses, failed/redelivery/delivered chưa completed, COD pending/online paid/multiple payments, thiếu ảnh/delivery/reference/timestamp/SKU, tài chính và định dạng tiền Việt Nam, confirmation, action eligibility/CSRF, 404 cho đơn khách khác, escaping HTML, ẩn ghi chú nội bộ và mốc suy diễn.
- Tracking tests xác minh mốc suy diễn verified=false, confirmedAt thật verified=true; sự kiện thật không có timestamp không bị bịa giờ. Các tests tracking/cancellation/reorder hiện có vẫn chạy trong full suite.
- Playwright/Chrome headless trên template thật render bằng fixture, tại 1440/768/390/320 px: main không overflow, 2 shop và 2 payments giữ riêng, primary stage SHIPPING, mở timeline thấy đúng 4 events có căn cứ và không có inferred event. Đã xem ảnh desktop/mobile.
- Full suite có chạy lại My Orders, Payment History, security harness và các kiểm tra hồi quy hiện có. `git diff --check` đạt.

Logs/artifacts:
- `.document-analysis/phase5-targeted.log`
- `.document-analysis/phase5-full.log`
- `.document-analysis/phase5-visual-checks.json`
- `.document-analysis/phase5-detail-1440.png`
- `.document-analysis/phase5-detail-390.png`

## Giới hạn và phối hợp module khác

- Browser kiểm tra với dữ liệu fixture, không phải session/live DB; bản offline chặn external CDN nên visual xác nhận phần main, chưa xác nhận global shell/CDN live.
- Không thể dựng đầy đủ timeline nếu DB thiếu history/timestamps. Hiện trạng thái đã biết và các sự kiện có căn cứ; không lấp dữ liệu lịch sử còn thiếu.
- Thumbnail không phải snapshot lịch sử; dùng ảnh hiện tại làm tham chiếu. Không có provider/tracking number/ETA nên không bổ sung các field giả.
- SQL Server runtime/execution plan chưa được chứng minh bằng H2; cần chạy opt-in tests trên DB riêng.
- Order/ShopOrder aggregate có thể lệch; Phase 2 mapping đọc tránh báo hoàn tất sớm, việc đồng bộ dữ liệu nguồn vẫn cần chủ fulfillment xử lý.
- Thu COD/paidAt/aggregate payment, callbacks online và refunds vẫn do module Payment phụ trách. Trang chỉ phản ánh từng bản ghi và trạng thái tổng hợp đã lưu, không đối soát hoặc tự sửa khác biệt.
- FAILED rồi xác nhận hàng về cần chủ Delivery thống nhất trạng thái đóng fulfillment; không coi đó là REFUNDED. Giữ quyết định nhả giữ kho sau xác nhận hàng về, và nhả khi hủy hợp lệ.
- Chuyển RESERVED → SOLD, idempotency và stock return thuộc Inventory/Delivery, không sửa trong phase này.
