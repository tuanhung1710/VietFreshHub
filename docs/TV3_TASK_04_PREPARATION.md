# Chuẩn bị Task 04 — Hủy đơn theo SRS

Ngày chuẩn bị: 20/10/2026. **Đã triển khai Task 04**; tài liệu này giữ kế hoạch ban đầu để đối chiếu. Kết quả mới: 108/108 test PASS, xem [TV3_TASK_04_RESULTS.md](TV3_TASK_04_RESULTS.md). Các mô tả “hiện tại” bên dưới là baseline trước sửa.

## Căn cứ và chênh lệch

- SRS phần mô tả chi tiết **UC-22 Cancel Order**, **GBR-25**: được hủy Pending hoặc Confirmed; không hủy từ Preparing. GBR-16 yêu cầu trả kho, GBR-24 yêu cầu trạng thái một chiều.
- Code `CustomerOrderServiceImpl.cancellationHint` hiện chỉ nhận Order PENDING và mọi ShopOrder PENDING. Trạng thái Confirmed đang bị chặn.
- DB mới giữ enum Order/ShopOrder và allocation RESERVED/SOLD/RELEASED; không cần thêm enum để thực hiện Task 04.
- Không biến workflow thành quyết định tự mở hủy trả tiền: PAID/refund/online chưa có hợp đồng hoàn tiền, discount/coupon chưa tích hợp vẫn cần giữ điều kiện đối soát hiện tại.

## Chia nhỏ phần thực hiện

| Bước | Đầu việc | Đầu ra |
| --- | --- | --- |
| 04.1 | Đọc lại UC-22/GBR-25, trạng thái tổng/đơn con, payment và thứ tự khóa hiện tại; kiểm tra có yêu cầu người dùng mới thay SRS hay không | Bảng điều kiện rõ ràng, không mặc nhiên coi code Pending-only là yêu cầu mới |
| 04.2 | Viết test tái hiện Confirmed chưa Preparing bị chặn hiện tại | Test fail trước sửa, các ca đã chặn có lý do tiếp tục được giữ |
| 04.3 | Sửa điều kiện trong phạm vi customer order TV3 theo căn cứ được xác nhận, dùng chung policy cho UI actions và POST cancel | Frontend/backend đồng nhất; không bypass kiểm tra ownership/payment/delivery |
| 04.4 | Kiểm thử release trên DB riêng với schema/seed mới | Allocation RELEASED, reserved giảm đúng, ledger RELEASE khớp, không tăng on-hand; cancel lặp không trả kho hai lần |
| 04.5 | Kiểm tra rollback và cạnh tranh với Preparing/pickup | Không có trạng thái/hàng sai khi một thao tác thắng; nếu cần hook TV4 thì ghi bàn giao, không sửa thay |
| 04.6 | Hồi quy Cart/Checkout, cập nhật báo cáo và chuẩn bị Task 05 | Không phá kết quả Task 03, có số test thực chạy |

## Ma trận test tối thiểu

1. Customer sở hữu đơn COD chưa thu tiền, Order/ShopOrder Pending: hủy được.
2. Customer sở hữu đơn COD chưa thu tiền, Confirmed và chưa Preparing: xử lý theo UC-22/GBR-25; không tự giữ hạn chế Pending-only nếu chưa có thay đổi yêu cầu được xác nhận.
3. Đa shop còn Pending/Confirmed: đánh giá toàn bộ đơn dưới khóa; không hủy một phần âm thầm.
4. Một shop đã Preparing/Ready/Out for Delivery/Completed hoặc allocation SOLD: từ chối, không thay kho.
5. Gọi cancel lần hai: không thêm RELEASE hoặc trả kho lần hai.
6. Customer khác hoặc không xác thực: không hủy/không đọc dữ liệu đơn.
7. PAID, refund hoặc có giao dịch cần đối soát: không tự đổi payment sang CANCELLED để bỏ qua nghiệp vụ hoàn tiền.
8. Có coupon/discount hoặc một shop đã CANCELLED trước: giữ xử lý thận trọng hiện tại, ghi điểm cần chốt nếu tài liệu chưa định nghĩa; không tự thêm partial cancellation.
9. Allocation/ledger không khớp: rollback, không báo hủy thành công.
10. Cancel và thao tác fulfillment đồng thời: chỉ kết quả hợp lệ được commit; không resurrect trạng thái và không trả kho đã xuất.

## File TV3 dự kiến

- `src/main/java/VietFreshHub/order/service/CustomerOrderServiceImpl.java`
- `src/main/java/VietFreshHub/order/service/OrderInventoryReleaseService.java` nếu test chứng minh cần sửa.
- `src/main/java/VietFreshHub/order/controller/CustomerOrderController.java` và customer order template chỉ nếu cần đồng nhất UI.
- Test mới dưới `src/test/java/VietFreshHub/order/`, tận dụng nguyên tắc DB test có tên/marker riêng của Task 03.

Không sửa `OrderServiceImpl` phía shop, delivery/inventory của thành viên khác, SecurityConfig, application.properties, SQL nguồn hoặc DB dùng chung. Không triển khai payment/coupon/admin trong Task 04.

## Prompt bắt đầu Task 04

```text
Thực hiện Task 04 theo docs/TV3_TASK_04_PREPARATION.md và workflow hiện tại.
Đọc source và tài liệu trước; viết test tái hiện chênh lệch hủy Confirmed.
Lấy UC-22/GBR-25 làm căn cứ nếu không có yêu cầu mới được người dùng xác nhận.
Chỉ sửa customer order TV3; giữ ownership, khóa, kiểm tra payment/delivery,
đối chiếu allocation/ledger và tính idempotent khi release.
Nghiệm thu bằng unit test và SQL Server test riêng từ DB mới; không đổi DB nhóm.
Giữ những thay đổi đang có. Nếu cần sửa module khác, ghi rõ điểm bàn giao.
Kết thúc bằng báo cáo file thay đổi, test thực chạy và phần còn chờ;
không coi phát event là đã gửi notification hoặc coi test TV3 là coverage toàn dự án.
```
