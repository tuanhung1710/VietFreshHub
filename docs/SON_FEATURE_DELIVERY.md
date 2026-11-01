# Bàn giao phần việc Sơn TV2

Phạm vi được đối chiếu từ sheet `CRMS` của ProjectTracking: 41 dòng mang tên Sơn (TV2). Các ví dụ và hướng dẫn trong SRS được xem là tài liệu tham khảo, không phải yêu cầu sửa những module ngoài phân công.

Lần thực hiện này bổ sung phần còn thiếu trên nền product/inventory hiện tại. Các commit có sẵn trước `5064afa` và thay đổi chưa commit của người dùng được giữ nguyên. Chưa push lên remote.

## Phần đã xử lý

| Phạm vi | Kết quả lần này |
| --- | --- |
| FE-01.5 View Shop Profile | Trang `/seller/profile` hiển thị hồ sơ của shop đang quản lý. |
| FE-01.5 Edit Shop Profile | Sửa tên, mô tả, liên hệ, đường dẫn logo/ảnh bìa; validation và khóa đúng shop của chủ sở hữu. Địa điểm lấy hàng tiếp tục dùng luồng VietMap sẵn có. |
| FE-01.5 Set Shop Availability | Chủ shop mở/tạm đóng nhận đơn. Không cho dùng thao tác này để mở shop bị đình chỉ. |
| FE-02.4 Promotional Pricing | Catalog đọc giá giảm đang hợp lệ theo lô và chỉ tính lượng có thể bán tại đúng mức giá đó. Dùng quy tắc cận hạn 3 ngày, không cộng trùng khi lô có nhiều markdown. |
| FE-03.1 Product Browsing | Guest/Customer xem danh sách, chi tiết và duyệt cây danh mục thật. Chỉ hiển thị sản phẩm và SKU đang bán, có tồn khả dụng. |
| FE-03.2 Search and Filters | Tìm tên, lọc danh mục gồm danh mục con, khoảng giá và từng shop; phân trang giữ bộ lọc. Tìm tên xử lý `%`, `_`, `[` như ký tự, không để mở rộng truy vấn. |
| FE-03.2 Sort | Giá tăng/giảm, bán chạy dựa trên số lượng của đơn hoàn tất, điểm trung bình từ đánh giá đã duyệt; có thứ tự phụ ổn định. |
| FE-03.3 Shop Directory/Profile | Danh sách shop công khai, hồ sơ/liên hệ/địa chỉ, sản phẩm của shop. Chỉ shop hoạt động và đang nhận đơn xuất hiện trong luồng công khai. |
| FE-03.3 Shop Ratings and Reviews | Xem trung bình và số đánh giá, danh sách đã duyệt, lọc 1–5 sao và phân trang. Chỉ đọc dữ liệu, không tạo luồng viết/kiểm duyệt đánh giá của thành viên khác. |
| FE-03.3 Shop Promotions/Vouchers | Đọc ưu đãi đang hiệu lực của TV5; kiểm tra thời gian, trạng thái promotion/coupon, giới hạn sử dụng; hiển thị điều kiện và hạn dùng. |
| FE-03.3 Favorite Shop | Customer lưu/bỏ lưu shop và xem danh sách riêng. CSRF, kiểm tra tài khoản hiện tại và khóa theo user để tránh tạo trùng. |
| FE-012.2 Low Stock Alerts | Quét định kỳ, tạo thông báo in-app cho chủ shop khi tồn khả dụng xuống ngưỡng, gồm cả 0. Lưu trạng thái đợt cảnh báo để chống lặp qua lần khởi động lại. |
| FE-012.2 Freshness and Expiry Alerts | Cận hạn theo ngưỡng của sản phẩm, hết hạn khi trước ngày hiện tại ở Việt Nam; chỉ cảnh báo lô còn tồn. Liên kết thông báo kiểm tra lại quyền sở hữu kho. |
| FE-012.2 System/Admin Announcements | Điểm nhận thông báo từ module hệ thống/Admin, hộp thông báo `/seller/announcements`, hỗ trợ đọc và đánh dấu đã đọc. Không xây thêm module soạn/phát thông báo Admin. |

18 dòng quản lý product/inventory đã có luồng chính từ trước (CRUD sản phẩm/SKU, danh mục, ảnh, kho/lô, nhập/điều chỉnh, lịch sử, giữ/giải phóng và giá gốc) được giữ lại và chạy kiểm thử hồi quy; không ghi nhận thành tính năng mới của lần này.

## Quy tắc giao diện đã cập nhật theo yêu cầu sau

- Thay toàn bộ template mua sắm thuộc phạm vi product/shop Customer bằng fragment `customer-shop` và stylesheet `customer-shop.css` riêng. Không dùng lại header, hero hoặc CSS storefront cũ cho các trang này.
- Bố cục tập trung vào danh sách, bộ lọc, giá và chọn mua; dùng chung card, header và tab shop. Trang quản lý và giao diện của thành viên khác không được thiết kế lại.
- Bỏ lựa chọn “hết hàng/chưa khả dụng” cho người mua. Điều kiện có thể mua được áp dụng bắt buộc ở backend, không chỉ ẩn nhãn ở HTML. Vì vậy dòng Filter by Availability trong sheet được thực hiện theo yêu cầu mới: catalog luôn lọc còn hàng; không cung cấp lựa chọn xem hàng hết tồn.
- SKU hết tồn/ẩn không xuất hiện trong bộ chọn. Link chi tiết cũ trả HTTP 404 với đường dẫn khám phá sản phẩm khác.

## Chuẩn bị database

Chạy migration bổ sung `database/migrations/20261101_inventory_alert_states.sql` trên SQL Server trước khi bật thông báo kho. Migration tạo bảng trạng thái chống lặp; không sửa/xóa dữ liệu kho hay đơn hàng hiện có. Lần làm này chưa thực thi migration lên database thật.

Quét mặc định bắt đầu sau 60 giây, lặp mỗi 5 phút. Có thể cấu hình:

```properties
inventory.alerts.enabled=true
inventory.alerts.initial-delay-ms=60000
inventory.alerts.scan-delay-ms=300000
```

Cảnh báo chỉ phát khi điều kiện được phát hiện mới. Sau khi một lần quét xác nhận đã phục hồi, cảnh báo được kích hoạt lại cho đợt tiếp theo. Không gửi email/SMS.

## Điểm bàn giao cho thành viên khác

**TV3 — Checkout/Cart:** `CatalogPricingService.offers(List<Long> variantIds)` trả `price`, `basePrice`, `availableStock` tại giá hiện hành. Với markdown, `availableStock` chỉ gồm lô ở đúng mức giá thấp nhất. Cart/checkout hiện vẫn có đoạn dùng giá gốc `variant.getPrice()`; TV3 cần dùng cùng offer để snapshot/tính tiền và phân bổ đúng lô dưới khóa. Lần này chưa sửa nghiệp vụ cart, thanh toán hoặc reservation checkout; chưa thể coi giảm giá là hoàn tất end-to-end trong đơn hàng.

**TV5 — Promotion:** trang shop đọc trực tiếp promotion/coupon và usage đã có. Việc áp dụng ưu đãi/voucher lên đơn thuộc module của TV5/TV3. Không tự cộng dồn promotion và batch markdown trong catalog.

**Admin/System:** gọi `ShopAnnouncementService.receive(shopId, origin, announcementId, title, content)` trong transaction của sự kiện nguồn. `announcementId` phải ổn định và duy nhất trong từng `origin` để retry không tạo thông báo trùng. Đây là điểm tích hợp nội bộ, không có API công khai cho người mua tạo thông báo.

## Kiểm chứng

- Toàn bộ bộ kiểm thử: **256 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** với JDK 21, Maven offline, datasource H2 cô lập và tắt scheduler cảnh báo kho. Log: `target/son-full-test.log`; kết quả suite: `target/surefire-reports`.
- Có kiểm thử SQL thực trên H2 cho giá/lượng theo markdown, lọc/sort, review, voucher, yêu thích và trạng thái cảnh báo; kiểm thử Spring MVC/Thymeleaf cho trang Guest/Customer, validation, CSRF và link cũ 404.
- Bộ test dùng H2/mocks, không xác nhận đầy đủ dialect, trigger, khóa cạnh tranh hoặc dữ liệu SQL Server thật. Scheduler của module giao hàng/thanh toán hiện có có thể ghi lỗi thiếu bảng khi context mặc định của test khởi động trên H2 rỗng; đây không phải assertion thất bại.
- Đã render HTML sáu trang chính. Phiên Computer Use xem trình duyệt hết thời gian chờ cấp quyền, nên chưa xác nhận trực quan bằng screenshot hoặc nghiệm thu mobile trên trình duyệt thật.

## Commit để duyệt

Mỗi chức năng/nhóm chức năng được tách thành commit cục bộ trên nhánh `Hoang-Van-Son`. Xem toàn bộ lần bàn giao bằng:

```text
git log --reverse --oneline 5064afa..HEAD
git diff 5064afa..HEAD
```

Commit `cf8f7db` áp dụng quy tắc chỉ hiển thị hàng mua được; `6436619` thay giao diện Customer theo yêu cầu bổ sung. Các commit tiếp theo chỉ làm gọn code, kiểm thử và ghi tài liệu bàn giao. Cần sự đồng ý của người dùng trước khi push.
