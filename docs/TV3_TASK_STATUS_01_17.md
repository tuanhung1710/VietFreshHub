# Trạng thái theo bảng task gốc 01–17

Ngày đối chiếu: 20/10/2026. Đây là **bảng số chính thức từ nay**. Các báo cáo Task 06–08 trước đây là tên lượt thực hiện, không tương ứng một-một với bảng gốc; không đổi/xóa lịch sử báo cáo để tránh mất bằng chứng.

| Task gốc | Phạm vi | Trạng thái và bằng chứng |
| --- | --- | --- |
| 01 | Unit test giỏ | Đã làm; CartServiceImplTest, CustomerCartIdentityTest |
| 02 | Draft và checkout COD unit | Đã làm; draft/checkout/shipping/recovery tests |
| 03 | Sửa lỗi Cart/Checkout | Đã nghiệm thu trong phạm vi hiện có; báo cáo Task 03 và các ca SQL rollback/idempotency/FEFO |
| 04 | Hủy theo SRS | Đã làm Pending/Confirmed trước Preparing; rollback/release/cạnh tranh chuẩn bị đã kiểm tra |
| 05 | Tracking | Đã làm lý do thất bại, timeline/UTC/ownership. Chưa coi lịch sử trạng thái là bằng chứng đã gửi notification |
| 06 | History/detail, đang giao, đa shop | Đã làm filter/ownership/paging/UTC và đọc trạng thái đơn con/delivery. **Chưa hoàn tất hợp đồng đồng bộ trạng thái tổng đa shop với TV4**; không đánh dấu done toàn mục |
| 07 | Mua lại | Đã làm, nằm trong báo cáo lượt Task 06; giá/tồn hiện tại, cộng giỏ, báo món bị loại và test SQL đồng thời |
| 08 | Pickup → SOLD/SALE | **Chờ TV2/TV4**. confirmPickup hiện chưa gọi sell. Có StockAllocationService.sell nhưng phải chốt quyền actor và nối transaction/khóa đúng; chưa sửa module khác |
| 09 | Thu/đối soát COD | **Chờ chốt với TV4**, người dùng xác nhận chưa chốt; đã có TV3_COD_INTEGRATION_CONTRACT.md, chưa có nghiệp vụ ghi tiền |
| 10 | Notification đặt/hủy sau commit | Đã phát event phía TV3; **chưa nối consumer dùng notification TV1 sau commit và nghiệm thu** |
| 11 | Payment history Customer | Đã làm; nằm trong báo cáo lượt Task 08, GET /customer/payments, readonly/ownership/filter/paging/render/SQL |
| 12 | Gợi ý dưới product detail | **Đã làm**; 6 unit + 3 controller/render + 1 SQL mới PASS. Xem TV3_TASK_12_RESULTS.md; không sửa catalog/kho TV2 |
| 13 | VNPAY init/callback | Đã triển khai luồng sandbox và test tự động; mặc định tắt. Còn nghiệm thu giao dịch trực tiếp/merchant IPN; xem TV3_VNPAY_SANDBOX.md |
| 14 | Gate/timeout/retry/late callback | Có gate DB sandbox, hạn 15 phút do người dùng chọn, retry/reference và callback muộn REVIEW; còn chốt triển khai DB chung/UX TV4 và đối soát production |
| 15 | Coupon | Chờ TV5 theo yêu cầu người dùng |
| 16 | Admin orders/payments/transactions | Chưa nghiệm thu/hoàn thiện phạm vi TV3; không suy ra done từ các trang admin của module khác |
| 17 | SQL/security/UI/coverage | Đang thực hiện dần: có SQL riêng, security filter HTTP, MVC/template tests. **Chưa đo coverage**, chưa browser/UI toàn luồng hoặc provider thật; chưa done toàn mục |

## Ánh xạ các lượt bị lệch số

- Báo cáo `TV3_TASK_06_RESULTS.md`: task gốc 06 (phần đọc/filter) và 07 (reorder).
- Báo cáo `TV3_TASK_07_RESULTS.md`: một phần task gốc 17 (HTTP/security/integration).
- Báo cáo `TV3_TASK_08_RESULTS.md`: task gốc 11 và chuẩn bị hợp đồng cho 09; **không phải pickup/xuất kho task gốc 08**.
- Lượt hiện tại quay lại **task gốc 12**, vì 08–10 còn phụ thuộc và 11 đã xong.

## Thứ tự tiếp tục

1. Task 12 đã nghiệm thu trong phạm vi gợi ý/read-only; review UI thực tế tiếp tục thuộc 17.
2. Theo yêu cầu mới đã chuyển sang payment sandbox 13–14; cần chạy giao dịch trực tiếp với merchant của người dùng và phối hợp TV4 review gate trước DB chung. 08–10/15 tiếp tục chờ như trước.
3. Khi nhóm chốt: nối 08, 09, 10, sau đó nghiệm thu xuyên module và các trạng thái đa shop còn lại của 06.
