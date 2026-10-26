# Cách đọc package cart

Code ưu tiên khai báo kiểu dữ liệu cụ thể, mỗi hàm làm một công việc, dùng vòng lặp và `if/else` để dễ theo dõi.

## Vai trò của các lớp

| Lớp | Công việc |
| --- | --- |
| `CustomerCartViewController` | Nhận yêu cầu từ giao diện, lấy khách đang đăng nhập, hiển thị lỗi và chuyển hướng. Hiện lớp này còn phục vụ trang chủ và trang chi tiết sản phẩm. |
| `CartServiceImpl` | Kiểm tra tài khoản, sản phẩm, tồn kho; thực hiện thêm, sửa, xóa giỏ trong transaction. |
| `CartViewMapper` | Chuyển entity sang DTO, tính tổng tiền và tạo thông báo tình trạng hàng. Không truy cập database. |
| `CartRepository` | Tìm giỏ đang hoạt động của đúng khách hàng. |
| `CartItemRepository` | Tìm dòng sản phẩm theo mã giỏ và mã biến thể. |
| `Cart`, `CartItem` | Ánh xạ các bảng giỏ hàng và chi tiết giỏ hàng. |
| Các lớp trong `dto` | Dữ liệu nhận từ form và dữ liệu trả về để hiển thị. |

## Luồng thêm vào giỏ

1. `handleAddToCart()` kiểm tra form và lấy mã khách từ phiên đăng nhập.
2. `addToCart()` kiểm tra dữ liệu, khóa tài khoản rồi kiểm tra sản phẩm có thể mua hay không.
3. `findOrCreateCart()` tìm giỏ đang hoạt động; chưa có thì tạo giỏ.
4. `findOrCreateItem()` tìm dòng hàng; chưa có thì tạo dòng mới với số lượng ban đầu bằng 0.
5. Cộng số lượng bằng `long`, kiểm tra tồn kho rồi mới đổi về `int` và lưu giá hiện tại.
6. `CartViewMapper.toAddResponse()` tạo dữ liệu kết quả.

## Luồng xem giỏ

`getActiveCart()` lấy danh sách mã biến thể không trùng, đọc tồn kho một lần cho cả danh sách, tạo DTO cho từng dòng rồi sắp xếp theo shop và mã dòng hàng. `CartViewMapper.toCart()` tính tổng số lượng, tổng tiền và đánh dấu giỏ có món không thể mua.

## Các quy tắc cần giữ

- Thao tác giỏ chỉ đọc tồn kho, chưa giữ hoặc trừ hàng. Giữ hàng thuộc bước checkout.
- Các thao tác thay đổi giỏ đều khóa tài khoản trước, kể cả khi tạo giỏ đầu tiên.
- Thêm lại cùng biến thể thì cộng vào dòng cũ. Tổng số lượng không được vượt tồn kho.
- Cập nhật về 0 là xóa; vẫn cho phép xóa món đã ngừng bán hoặc hết hàng.
- Tổng số lượng tính tất cả các dòng. Tổng tiền chỉ tính các dòng hiện có thể mua.
- Hiển thị theo giá hiện tại; `previousPrice` giữ giá đã lưu để thông báo thay đổi giá. Chỉ xem giỏ không cập nhật giá trong database.
- Controller lấy mã khách từ thông tin đăng nhập, không lấy từ form.
