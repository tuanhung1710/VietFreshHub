# Phase 2 — Order & Payment status refactor

Ngày: 2026-10-22. Phạm vi: backend đọc/ánh xạ/lọc trạng thái và test. Không thay enum entity, schema, template/CSS, checkout, thu COD, gateway, chuyển trạng thái fulfillment, kho hoặc cấu hình bảo mật. Các thay đổi có sẵn trước Phase 2 được giữ nguyên.

## Ánh xạ trước/sau

| Dữ liệu | Trước Phase 2 | Sau Phase 2 |
|---|---|---|
| COD PENDING, shop PENDING | SHIPPING | WAITING_CONFIRMATION; payment vẫn PENDING (Chờ thu COD) |
| Online PAID, shop PENDING | SHIPPING | WAITING_CONFIRMATION; payment PAID |
| Online PENDING/PROCESSING/FAILED, chưa bắt đầu fulfillment | WAITING_PAYMENT | WAITING_PAYMENT nếu không có bằng chứng PAID/hoàn tiền |
| Shop CONFIRMED hoặc PREPARING | SHIPPING | PREPARING |
| Shop READY_FOR_DELIVERY, chưa lấy hàng | SHIPPING | AWAITING_DELIVERY |
| Shop OUT_FOR_DELIVERY hoặc đã lấy hàng | AWAITING_DELIVERY | SHIPPING |
| Delivery FAILED, shop chưa kết thúc | RETURN_REFUND | Giai đoạn fulfillment còn mở; không suy ra hoàn tiền |
| Payment REFUNDED/PARTIALLY_REFUNDED | RETURN_REFUND trên cả hai trang | Chỉ đổi nhãn thanh toán; fulfillment tính độc lập |
| Parent COMPLETED nhưng còn shop PENDING | COMPLETED | WAITING_CONFIRMATION |

CustomerOrderStage chỉ là enum hiển thị, không ghi DB. Các giá trị: WAITING_PAYMENT, WAITING_CONFIRMATION, PREPARING, AWAITING_DELIVERY, SHIPPING, COMPLETED, CANCELLED. `Tất cả` là bộ lọc rỗng.

## Quy tắc multi-shop và bằng chứng giao hàng

1. Ưu tiên trạng thái con thay vì tin parent có thể bị chậm cập nhật.
2. Có shop và tất cả CANCELLED => CANCELLED.
3. Có ít nhất một shop COMPLETED và mọi shop khác COMPLETED/CANCELLED => COMPLETED (hoàn tất phần không bị hủy). Không tính shop chỉ DELIVERED là hoàn tất; chờ ShopOrder COMPLETED.
4. WAITING_PAYMENT chỉ áp dụng khi chưa có shop bắt đầu chuẩn bị/giao/hoàn tất, aggregate chưa PAID/hoàn tiền, không có bản ghi PAID/hoàn tiền, và bản ghi payment mới nhất là non-COD PENDING/PROCESSING/FAILED. Dùng paymentId lớn nhất theo cách hiện có. Đây là dấu hiệu hiển thị, không phải hợp đồng retry/thu tiền mới; không cộng tiền các attempts.
5. Bỏ shop COMPLETED/CANCELLED khi chọn giai đoạn còn lại. Lấy giai đoạn sớm nhất: PENDING => WAITING_CONFIRMATION; CONFIRMED/PREPARING => PREPARING; READY_FOR_DELIVERY => AWAITING_DELIVERY; OUT_FOR_DELIVERY => SHIPPING.
6. Bằng chứng Delivery PICKED_UP/OUT_FOR_DELIVERY/DELIVERED/FAILED/REDELIVERY_SCHEDULED cho biết shop đã đi vào xử lý vận chuyển, ưu tiên hơn trạng thái chuẩn bị cũ của shop. UNASSIGNED/ASSIGNED/ACCEPTED chưa chứng minh đã lấy hàng. Lọc trên trạng thái delivery hiện có, không quét lịch sử sự kiện cũ.
7. FAILED/REDELIVERY_SCHEDULED và DELIVERED chưa hoàn tất ShopOrder vẫn nằm trong nhóm SHIPPING nếu không có shop khác ở giai đoạn sớm hơn. Nhóm này bao gồm vận chuyển chưa được đóng nghiệp vụ, **không khẳng định xe đang giao hoặc hàng đã hoàn lại kho**. Chi tiết lỗi thật nằm ở trang theo dõi. Sau xác nhận hàng trả về, module fulfillment hiện chưa đóng ShopOrder; cần phối hợp mục bên dưới, không sửa trong Phase 2.
8. Khi không có shop (dữ liệu legacy/bất thường), dùng parent: CANCELLED/COMPLETED tương ứng; PENDING => WAITING_CONFIRMATION; CONFIRMED/PROCESSING/PARTIALLY_COMPLETED => PREPARING bảo thủ, trừ khi có bằng chứng chờ thanh toán ở bước 4. Không đoán đã giao hàng.

Ví dụ: COMPLETED + PREPARING => PREPARING; COMPLETED + OUT_FOR_DELIVERY => SHIPPING; PENDING + OUT_FOR_DELIVERY => WAITING_CONFIRMATION; COMPLETED + CANCELLED => COMPLETED. Không cập nhật Order.status hoặc ShopOrder.status bằng quy tắc này.

## Payment History

Repository nhận `Payment.Status` (null cho Tất cả), WHERE trực tiếp `p.status=:status`. Không còn import/phụ thuộc CustomerOrderStageQuery hoặc CustomerOrderStage trong module customer payment. DTO bỏ stage suy ra từ đơn hàng; statusLabel và accessor tương thích transactionStatusLabel đều mô tả chính payment record.

| Payment.Status | Nhãn |
|---|---|
| PENDING | Chờ thanh toán; với methodCode COD: Chờ thu COD |
| PROCESSING | Đang xử lý |
| PAID | Đã thanh toán |
| FAILED | Thanh toán thất bại |
| CANCELLED | Đã hủy giao dịch |
| REFUNDED | Đã hoàn tiền |
| PARTIALLY_REFUNDED | Hoàn tiền một phần |

Dropdown PENDING dùng nhãn kết hợp vì lọc cả hai phương thức. Không đổi PAID thành SUCCESS. Payment History vẫn hiển thị từng bản ghi, không phải tổng thu thực tế.

Giữ customerId lấy từ phiên đăng nhập; WHERE ownership trên mọi history query và truy vấn stage chi tiết. Giữ keyword, thứ tự createdAt/paymentId hoặc placedAt/orderId, page size 10, giới hạn page, chuyển ngày Việt Nam thành UTC [from, until), count và pagination trong database. Chi tiết có thêm một scalar query có ownership; số query cố định, không N+1.

## Files thay đổi trong riêng Phase 2

Backend (12):
- `src/main/java/VietFreshHub/order/dto/CustomerOrderStage.java`
- `src/main/java/VietFreshHub/order/dto/CustomerOrderFilterRequest.java`
- `src/main/java/VietFreshHub/order/dto/OrderListRow.java`
- `src/main/java/VietFreshHub/order/repository/CustomerOrderStageQuery.java`
- `src/main/java/VietFreshHub/order/repository/OrderRepository.java`
- `src/main/java/VietFreshHub/order/service/OrderLabels.java`
- `src/main/java/VietFreshHub/order/service/impl/CustomerOrderQueryServiceImpl.java`
- `src/main/java/VietFreshHub/payment/controller/CustomerPaymentHistoryController.java`
- `src/main/java/VietFreshHub/payment/dto/PaymentHistoryFilter.java`
- `src/main/java/VietFreshHub/payment/dto/PaymentHistoryRow.java`
- `src/main/java/VietFreshHub/payment/repository/CustomerPaymentReadRepository.java`
- `src/main/java/VietFreshHub/payment/service/impl/CustomerPaymentHistoryServiceImpl.java`

Tests sửa (4) và thêm (1):
- `src/test/java/VietFreshHub/order/CustomerOrderHistoryControllerTest.java`
- `src/test/java/VietFreshHub/payment/CustomerPaymentHistoryControllerTest.java`
- `src/test/java/VietFreshHub/payment/CustomerPaymentHistoryTest.java`
- `src/test/java/VietFreshHub/payment/PaymentOrderStageQueryTest.java`
- Thêm `src/test/java/VietFreshHub/order/CustomerOrderQueryServiceTest.java`.

Tài liệu thêm: file này. Git diff toàn workspace còn có thay đổi của các lượt trước, không đồng nghĩa chúng thuộc Phase 2.

## Hợp đồng cần phối hợp — không triển khai trong Phase 2

- `order/service/impl/OrderServiceImpl.java`: parent chưa theo kịp xác nhận/chuẩn bị; completeDeliveredShopOrder coi cả child CANCELLED là chưa hoàn tất. Thống nhất aggregate cho đơn nhiều shop, gồm trường hợp COMPLETED + CANCELLED. Mapping đọc hiện tránh báo hoàn thành sớm nhưng không sửa dữ liệu nguồn.
- `delivery/service/DeliveryAssignmentService.java` (confirmGoodsReturn) và `order/service/impl/OrderServiceImpl.java`: sau giao thất bại, xác nhận hàng về giải phóng giữ kho nhưng chưa có trạng thái đóng fulfillment. Cần chủ module xác định kết thúc/hủy/giao lại và ghi history atomically; không tự gắn REFUNDED cho sự kiện trả hàng vật lý. Giữ quyết định đã chốt: chỉ nhả kho giao thất bại sau xác nhận hàng về; hủy đủ điều kiện cũng nhả kho.
- `delivery/service/DeliveryStaffActionService.java`, `payment/service/PaymentService.java`, `payment/entity/Payment.java`: chưa có ghi nhận thu COD đầy đủ (PAID/paidAt/aggregate). Cần xác định người xác nhận thu, số tiền từng shop, chống ghi trùng và thời điểm aggregate PAID; không suy ra PAID từ DELIVERED trong query đọc.
- `delivery/repository/DeliveryStaffHistoryRepository.java`: dailyCod suy từ shop/delivery; cần phân biệt tiền phải thu và tiền đã xác nhận thu.
- `inventory/service/impl/StockAllocationServiceImpl.java` (sell), `delivery/service/DeliveryStaffActionService.java`: chủ module cần thống nhất điểm RESERVED -> SOLD, atomicity và idempotency. Phase 2 không gọi thêm sell/release.
- Online payment thuộc thành viên khác: cần hợp đồng attempts/retries, latest-attempt identity, callback xác thực/idempotent, aggregate và gate chuẩn bị đơn đã trả tiền. Mapping hiện dựa vào dữ liệu đã có, không tạo gateway/retry/refund workflow.
- `templates/customer/payment-history.html`: nhãn tĩnh hiện còn “Trạng thái đơn hàng”; sau backend refactor hai accessor nhãn đều là payment nên hiển thị lặp. Đổi nhãn tĩnh/đơn giản hóa hiển thị ở Phase 3; Phase 2 không sửa template hoặc CSS. Các option động và badge đã nhận dữ liệu mới từ backend.
- URL lọc cũ theo order-stage trên Payment History bị validation từ chối; không âm thầm diễn giải nó thành payment status khác.

## Kiểm thử

Maven full suite (Java 21, Mockito javaagent): **BUILD SUCCESS**. Tổng 284 tests: **259 passed, 0 failures, 0 errors, 25 skipped**. 25 tests SQL Server opt-in bị bỏ qua do chưa cấu hình DB kiểm thử riêng (TV3_TEST_JDBC_URL); không dùng DB thật để thay thế.

Trong full suite, 94 tests thuộc nhóm history/controller/shared-query/detail: 54 tests truy vấn (39 tình huống lifecycle, 7 nhóm order filter, 7 payment status filters, 1 ownership/date-boundary test); 19 payment service/labels; 12 order service/filter; 4 order MVC; 3 payment MVC/render; 2 order detail. Các test còn lại kiểm tra hồi quy checkout, hủy đơn, giỏ hàng, giao hàng và các chức năng có sẵn.

Log: `.document-analysis/phase2-full.log`. `git diff --check` không phát hiện lỗi whitespace. Không commit/push.

 Test JPQL thực thi trên H2 biệt lập, không kết nối DB ứng dụng. Chưa xác nhận execution plan hoặc hành vi runtime SQL Server bằng H2; chỉ sử dụng JPQL tương thích, không thêm DDL/migration hay SQL đặc thù H2 vào production.
