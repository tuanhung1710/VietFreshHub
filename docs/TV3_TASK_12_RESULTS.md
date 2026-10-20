# Task gốc 12 — Gợi ý sản phẩm dưới product detail

Ngày 20/10/2026. Thực hiện theo bảng gốc 01–17 người dùng gửi lại; không tiếp tục đánh số lượt cũ thành task nghiệp vụ. Task 08–10 vẫn chờ phối hợp, không bị bỏ qua hoặc coi là đã làm.

## Thay đổi

- Module đọc riêng `VietFreshHub.recommendation`: DTO, repository chỉ đọc, service interface và `service.impl`.
- Controller product detail thuộc TV3 nhận productId đã được catalog resolve, rồi tải tối đa 6 gợi ý. Không nhầm variantId với productId; source thiếu/không visible trả danh sách rỗng.
- Ưu tiên cùng danh mục active (kể cả kiểm tra ancestor policy hiện có), rồi cùng shop, rồi productId mới hơn. Fallback là sản phẩm khác còn mua được, không giả số lượt bán hoặc gọi “phổ biến”.
- Chỉ sản phẩm ACTIVE/APPROVED, shop ACTIVE/OPEN, có category hợp lệ và variant ACTIVE với batch ACTIVE còn hạn/còn số lượng khả dụng. Loại sản phẩm đang xem.
- Query chọn tối đa 6 product trước, sau đó đọc variant còn mua được theo batch để chọn variant giá thấp nhất (tie theo variantId), trả một card mỗi product. Không N+1 theo từng card, không load toàn catalog vào Java.
- Fragment gợi ý đặt ngay dưới nội dung product detail. Card theo style storefront hiện có, có tên/shop/variant/ảnh lazy load/giá hỗ trợ thập phân, link trực tiếp variant. Không ảnh thì hiển thị thiếu ảnh, không gắn ảnh trái cây giả.
- Không reserve/tạo giỏ/đơn, không thay giá/tồn hay service/repository catalog/inventory của TV2. Giá và tồn tiếp tục được kiểm tra ở cart/checkout khi người dùng mua.

## Kiểm thử

- 6 unit test service: input/source không hợp lệ/hidden, category tree không active, giữ thứ tự ranking/dedupe variant và variant không còn khả dụng.
- 3 controller/fragment tests: productId resolve đúng, ẩn section rỗng, escape tên/giá thập phân/link variant/ảnh thiếu/không claim popularity.
- 1 SQL/JPA thật: source cùng category, candidate khác shop nhưng liên quan, hai variant giá khác nhau, loại source/hết hàng/batch hết hạn/chưa duyệt, không trùng card và không đổi stock/cart/order/ledger.
- Cập nhật các test khởi tạo controller sau khi thêm dependency; không xóa kiểm tra security/cart cũ.

## Giới hạn

Nghiệm thu: **10/10 test gợi ý PASS** (6 service unit, 3 controller/render, 1 SQL). Lượt tổng hợp trước đó chạy 205 test: 204 PASS, ca SQL gợi ý mới lỗi fixture. Fixture đã được sửa theo trigger công bố (category/SKU trước ACTIVE) và giá bán tối thiểu 10.000 VND; sau đó chạy lại riêng 10 test gợi ý thành công. Không gọi kết quả này là một lượt 205/205 PASS và không cộng lặp số test. Bộ 195 test trước đó không thất bại ở lượt tổng hợp này.

DB test: `VietFreshHub_TV3_Test_36cfed1852624fb790a2a128ad9fcbac`. Lượt tổng hợp chạy chậm và ghi nhận RESOURCE_SEMAPHORE ở query đọc đơn cũ; không thay cấu hình SQL Server. Không tắt trigger hoặc constraint để chạy fixture.

Đây là gợi ý liên quan/khả dụng, chưa có ranking bán chạy. Không cache nên card phản ánh thời điểm query, không bảo đảm giữ tồn cho người xem. Browser QA/coverage toàn TV3 tiếp tục ở task gốc 17. Pickup/xuất kho/thu COD/notification không được sửa trong lượt này.
