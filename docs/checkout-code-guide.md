# Cách đọc package checkout

Package này xử lý đặt hàng COD. Code ưu tiên khai báo kiểu dữ liệu cụ thể, dùng vòng lặp và chia hàm theo từng công việc.

## Vai trò của các lớp

| Lớp | Công việc |
| --- | --- |
| `CheckoutController` | Nhận yêu cầu từ trang web, kiểm tra lỗi form, trả về trang hoặc chuyển hướng. |
| `CheckoutServiceImpl` | Điều phối xem trước và xác nhận đặt hàng; kiểm tra dữ liệu và giữ transaction chung. |
| `CheckoutCartSupport` | Chọn sản phẩm, nhóm theo shop, tính tiền và tạo dấu kiểm tra giỏ hàng. Không truy cập database. |
| `CheckoutOrderService` | Lưu đơn tổng, đơn của từng shop, chi tiết sản phẩm, địa chỉ, lịch sử và payment COD. |
| `InventoryReservationService` | Khóa lô hàng, kiểm tra tồn kho và giữ hàng theo hạn sử dụng. |
| `CheckoutShippingQuoteService` | Lấy địa chỉ nhận/lấy hàng, tính phí qua VietMap và kiểm tra lại báo giá khi xác nhận. |
| `ShippingFeeService` | Tính phí từ khoảng cách giao hàng. |
| `CheckoutDraftStore` | Lưu thông tin đã xem trước trong session, cấp và tìm token đặt hàng. |
| `CheckoutAddressServiceImpl` | Kiểm tra và lưu địa chỉ mới tại trang checkout. |

## Thứ tự đọc luồng đặt hàng

1. `CheckoutController.show()`: lấy sản phẩm đã chọn, địa chỉ và báo giá để hiển thị.
2. `CheckoutDraftStore.issue()`: lưu dữ liệu đã xem trước ở server và trả token cho form.
3. `CheckoutController.confirm()`: kiểm tra form và gọi service xác nhận.
4. `CheckoutServiceImpl.confirm()`: kiểm tra token; nếu token đã tạo đơn thì trả đơn cũ. Sau đó kiểm tra lại tài khoản, địa chỉ, giỏ hàng và báo giá.
5. `CheckoutOrderService`: tạo dữ liệu đơn hàng.
6. `InventoryReservationService.reserve()`: giữ hàng. Nếu thiếu hàng, lỗi được ném ra để rollback transaction.
7. Service ghi payment COD chờ thu, cập nhật giỏ và phát sự kiện đặt hàng.

## Các điểm cần giữ khi sửa tiếp

- `confirm()` là transaction chung: tạo đơn, giữ kho và cập nhật giỏ phải cùng thành công hoặc cùng rollback.
- `Propagation.MANDATORY` nghĩa là hàm phải chạy trong transaction đã có; không tự mở transaction độc lập.
- Giữ thứ tự khóa theo ID để giảm nguy cơ deadlock khi nhiều người đặt hàng.
- `fingerprint` là mã SHA-256 từ nội dung giỏ; dùng để phát hiện giá hoặc số lượng thay đổi sau khi xem trước.
- Khi mất kết nối lúc commit, chưa thể kết luận đơn đã thất bại. Controller giữ token cũ để khách kiểm tra hoặc gửi lại an toàn.
- DTO dạng `record` dùng để mang dữ liệu; các tên như `draft.token()` là cách đọc thuộc tính của record.
