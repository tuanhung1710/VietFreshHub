# Task 08 — Lịch sử thanh toán và hợp đồng COD

Ngày 20/10/2026. Phạm vi đã được người dùng xác nhận: **nhóm chưa chốt xác nhận thu COD; chỉ làm lịch sử và hợp đồng tích hợp trước**. Đọc lại UC-20 View Payment History và GBR-05: chỉ dữ liệu của customer đã đăng nhập, không sửa dữ liệu khi xem.

## Đã triển khai

- GET `/customer/payments`, link từ trang đơn hàng. Hiển thị payment record, phương thức, trạng thái, số tiền bản ghi, thời gian tạo/thời điểm thanh toán đã ghi nhận và link về đơn hàng.
- Bộ lọc mã đơn, trạng thái, khoảng ngày tạo theo giờ Việt Nam, phân trang 10 dòng, sort createdAt/paymentId giảm dần, đưa trang vượt dữ liệu về trang cuối. Ngày sai/giá trị binding sai không chạy query lịch sử.
- COD PENDING ghi “Chờ thu tiền”. Không tính tổng doanh thu từ các attempt, không gọi Delivered là đã thu tiền, không thêm thao tác tự đánh dấu PAID. Refund hiển thị trạng thái đang lưu, không tự suy ra số tiền còn lại.
- Repository đọc riêng chỉ trả DTO whitelist, luôn ràng buộc customer qua Order. Không select provider payload/secret hoặc thay endpoint của thành viên khác.
- Service mới theo cấu trúc interface trong `service`, implementation trong `service.impl`.
- Mapping `Payment.createdAt/paidAt` dùng LOCAL_DATE_TIME cho DATETIME2 UTC, nhất quán cách xử lý Order ở Task 06. Không migration/đổi dữ liệu cũ; không thêm logic ghi tiền.
- UI tái sử dụng CSS order hiện có, hạn chế bo góc, có empty/error/pagination. Dữ liệu hiển thị qua `th:text`.

## Hợp đồng COD

Xem [TV3_COD_INTEGRATION_CONTRACT.md](TV3_COD_INTEGRATION_CONTRACT.md): hiện trạng Payment toàn đơn/ShopOrder nhiều shop, các điểm cần chốt, actor/evidence, idempotency, transaction/lock, trách nhiệm TV3/TV4/TV2/TV1 và ma trận nghiệm thu phần ghi. Các phương án là đề xuất, chưa phải API đã tồn tại hoặc chính sách được duyệt.

## Kiểm thử

- 11 unit test: customer/status/input, UTC và biên năm SQL Server, paging, nhãn COD.
- 3 MVC + render toàn template: rỗng; dữ liệu escape/tiền thập phân/giờ Việt Nam/link/pagination giữ bộ lọc; binding lỗi không query. Context render bổ sung security expression handler và anonymous context cho navigation chung; không đổi production để đáp ứng test.
- Security matrix thêm `/customer/payments`: anonymous redirect login, sai role 403.
- 1 HTTP + SQL case mới: 12 bản ghi thuộc customer (nhiều record cùng order), dữ liệu customer khác, status/date boundary, paging, year 0001/9999, giờ paidAt, whitelist không chứa payload và POST thay trạng thái không tồn tại (405). So sánh bảng payments/kho trước-sau để kiểm tra chỉ đọc. Các payment PAID/attempt trong case là fixture DB test, không phải luồng thu COD production.

## Chưa triển khai theo phạm vi đã chốt

Kết quả cuối: **195/195 PASS**, 0 failure/error/skipped trong một lượt Maven, gồm 24 SQL integration, 20 security HTTP, 11 payment service unit, 3 payment MVC/render và 137 test hồi quy khác. DB riêng: `VietFreshHub_TV3_Test_655fc14e381e481b9e83e90bb6478868`. Không cộng các lần chạy lại vào số test. Các lượt render ban đầu thiếu handler/context đúng phiên bản Spring Security đã được sửa ở harness test; không sửa security production.

Chưa ghi receipt/thu/đối soát COD; chưa thay payment_transactions hoặc nối action TV4; chưa bật online/coupon. Cần chốt hợp đồng trước phần ghi. Chưa đo coverage, chưa nghiệm thu login/OIDC/provider thật hoặc migration sửa timezone dữ liệu cũ.
