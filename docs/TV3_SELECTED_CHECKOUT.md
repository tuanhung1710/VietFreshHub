# Checkout sản phẩm được chọn và địa chỉ nhận hàng

Yêu cầu mới ngày 08/10/2026 thay thế quy tắc bắt buộc mua toàn bộ giỏ ở phiên bản COD trước.

- Giỏ có checkbox từng dòng và Chọn tất cả. Dòng không khả dụng không được tick; không chọn dòng nào thì không được checkout.
- Tiền hàng, phí ship 20.000₫/shop và tổng dự kiến tính theo lựa chọn. Lựa chọn được giữ trong cùng tab khi reload/sửa quantity. Backend luôn kiểm tra lại giá và ownership, không tin tiền do JS tính.
- IDs được chuyển qua GET để review, rồi lưu trong draft session. POST chỉ dùng selection đã lưu; ID của người khác/không còn trong giỏ bị từ chối.
- Mua một phần chỉ tạo order_items và RESERVE cho phần chọn. Các dòng không chọn vẫn ở giỏ ACTIVE, không bị xóa hoặc giữ kho; dòng không chọn bị hết hàng hay đổi giá không chặn lần mua này.
- Mua hết giữ hành vi CHECKED_OUT trước đó. Gửi lại cùng token trả cùng đơn kể cả khi dòng đã mua đã rời giỏ.
- Checkout có form Thêm địa chỉ mới, tự mở khi chưa có địa chỉ. Form đứng riêng để tránh nested form, có CSRF, validation tiếng Việt và giữ input khi lỗi. Owner được lấy từ tài khoản đăng nhập. Lưu xong tự chọn địa chỉ mới và giữ selection.
- Không đổi schema SQL Server. Address service mới nằm trong Checkout, dùng entity Address có sẵn; nhóm có thể thay bằng service TV1 khi ghép.

Kiểm chứng: 84 test hồi quy pass, trong đó có mua một shop, giữ dòng còn lại, bỏ qua trạng thái/giá của dòng không chọn, ID không thuộc giỏ, thêm địa chỉ đúng owner/default và giữ form lỗi. Kiểm tra live trên SQL Server với một tài khoản thử `.example` riêng: thêm địa chỉ đầu tiên, mua một dòng, giữ dòng khác, gửi trùng cùng đơn; UI 360/1440/1920 px không tràn ngang. Không sửa giỏ hay địa chỉ của các tài khoản demo cũ.

Lần test live tạo order_id=2, giá trị 99.000₫ gồm 79.000₫ tiền hàng và 20.000₫ phí một shop; COD vẫn chưa thu tiền. Nhật ký kết quả/tài khoản thử nằm trong .document-analysis/selection-live-result.json (đã gitignore).

Phần này vẫn là checkout COD. Coupon/loyalty, VNPAY và các nghiệp vụ lịch sử/hủy/tracking/reorder là phạm vi tiếp theo.
