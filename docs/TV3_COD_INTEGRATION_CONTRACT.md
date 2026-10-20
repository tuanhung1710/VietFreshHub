# Hợp đồng tích hợp COD — đề xuất để nhóm chốt

Ngày 20/10/2026. **Chưa triển khai ghi nhận tiền.** Người dùng xác nhận nhóm chưa chốt và yêu cầu làm lịch sử + hợp đồng trước. Tài liệu này là đầu vào bàn giao TV3/TV4, không phải quyết định nghiệp vụ đã được duyệt.

## Hiện trạng đã xác minh

- Checkout TV3 tạo một Payment COD PENDING cho toàn Order, amount bằng grandTotal. Đơn có thể tách nhiều ShopOrder và nhiều lượt giao.
- `PaymentService.isCodOrder` chỉ đọc phương thức. Luồng delivery hiện chưa có bước TV3 ghi nhận/bảo đảm tiền thu; không sửa hộ module TV4.
- Schema có `payments` và `payment_transactions`. Transaction có amount/status/event_type/provider_transaction_id/provider_payload; chưa có FK shop_order/collector, unique receipt key hoặc trạng thái Payment PARTIALLY_PAID.
- Customer history hiển thị bản ghi thanh toán, không coi mọi attempt/event là tiền thu hoặc doanh thu. Seed PAID không chứng minh ứng dụng đã có nghiệp vụ thu tiền.

## Các điểm cần nhóm quyết định

| Điểm | Cần chốt |
| --- | --- |
| Người xác nhận | Delivery staff, shop manager, hay bên đối soát; quyền và phạm vi đơn được phân công |
| Đơn vị thu | Theo ShopOrder/lượt giao hay chỉ một lần toàn Order; ai thu phí giao hàng |
| Bằng chứng | Hành động xác nhận tiền thật, chứng từ/mã biên nhận; có yêu cầu hậu kiểm không |
| Thu một phần/thiếu tiền | Có cho phép không; lưu trạng thái/số dư thế nào khi enum hiện tại chưa có PARTIALLY_PAID |
| Hủy/hoàn tiền | Chính sách nếu đã thu một phần, giao thất bại sau thu, hủy hoặc trả hàng; không tự đổi CANCELLED/REFUNDED |
| Lưu trữ/idempotency | Bổ sung receipt có FK/unique key hay dùng transaction theo quy ước; migration thuộc nhóm duyệt |
| Điểm tích hợp | Gọi cùng transaction hay outbox/event bền vững sau commit; cơ chế retry/reconciliation khi mất kết nối |

## Dữ liệu bàn giao tối thiểu được đề xuất

- `orderId`, `paymentId`; nếu thu từng shop phải có `shopOrderId`/`deliveryId` khớp quan hệ thật.
- Actor lấy từ authentication hoặc danh tính nội bộ đã xác minh; không tin `collectorId/customerId` từ form.
- `collectedAmount` dùng BigDecimal, loại tiền VND và scale/quy tắc làm tròn đã chốt; đối chiếu số tiền server và phạm vi thu.
- Receipt/idempotency key ổn định, cùng khóa gửi lại trả cùng kết quả; cùng khóa mà khác actor/đơn/số tiền phải bị từ chối.
- Thời điểm ghi nhận server UTC và tham chiếu bằng chứng cần thiết; không dùng giờ client làm bằng chứng xác nhận.

Đây là hợp đồng dữ liệu dự kiến, chưa phải endpoint hoặc method public đã tồn tại. Không thêm controller cho Customer đánh dấu PAID.

## Bất biến TV3 phải bảo đảm khi được phép triển khai

1. Xác thực actor và ownership/assignment theo dữ liệu server; kiểm tra COD thật, Order/Payment thuộc nhau và trạng thái còn hợp lệ.
2. Khóa theo thứ tự thống nhất Order → ShopOrder (nếu có) → Payment/receipt; chống cạnh tranh thu trùng, hủy hoặc hoàn tiền. Nhóm phải thống nhất với thứ tự khóa TV4 trước khi nối.
3. Không tự coi DELIVERED là đã thu tiền. Chỉ ghi PAID khi đã có bằng chứng hợp lệ và tổng tiền được xác nhận theo chính sách đã chốt.
4. Ghi receipt/payment transaction và cập nhật trạng thái trong cùng đơn vị giao dịch; lỗi rollback không để lại PAID hoặc sự kiện thu tiền thành công.
5. Khi đủ tiền, Payment và Order.paymentStatus/paidAt phải nhất quán. Nếu thu theo shop thì không gán PAID toàn đơn sau lần giao đầu; trạng thái trước khi đủ phải chờ quyết định ở bảng trên.
6. Một lần thu có thể phát nhiều sự kiện/audit; không cộng lại các event lặp thành tiền thu. Refund phải là nghiệp vụ riêng, không âm thầm trừ hay đảo trạng thái.
7. Kết quả commit không rõ vì mất kết nối cần tra receipt/idempotency trước retry; không tạo biên nhận mới để “thử lại”.
8. Notification phát sau commit thông qua hợp đồng TV1; không gọi mạng khi giữ transaction/khóa.

## Phân công bàn giao

- **TV3:** validation tiền, receipt/payment transaction, idempotency, tổng hợp trạng thái payment, lịch sử Customer/admin theo quyền.
- **TV4:** UI/action xác nhận/bằng chứng của actor được phân công và điểm gọi nội bộ theo hợp đồng được duyệt; luồng delivery/return.
- **TV2:** nghiệp vụ xuất kho/SOLD/SALE của inventory; việc ghi tiền không tự thay allocation hoặc tồn kho.
- **TV1:** session/role và notification dùng chung, phối hợp nếu có endpoint/hook mới.

## Nghiệm thu bắt buộc khi triển khai phần ghi

Thu đúng tiền, sai/thiếu/thừa tiền, sai actor/assignment, khác shop/order, retry cùng receipt, khóa trùng khác payload, hai request đồng thời, rollback, commit không rõ, race hủy/thu, đa shop chưa thu đủ, sự kiện lặp và refund cần đối soát. Kiểm thử bằng SQL Server riêng; không thử thu tiền trên DB nhóm hoặc Customer endpoint.
