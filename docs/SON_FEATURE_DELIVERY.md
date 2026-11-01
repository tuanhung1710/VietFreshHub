# Bàn giao phần việc Sơn TV2

Phạm vi được đối chiếu từ sheet `CRMS` của ProjectTracking: 41 dòng mang tên Sơn (TV2). Các ví dụ và hướng dẫn trong SRS được xem là tài liệu tham khảo, không phải yêu cầu sửa những module ngoài phân công.

Lần thực hiện này bổ sung phần còn thiếu trên nền product/inventory hiện tại. Các commit có sẵn trước `5064afa` và thay đổi chưa commit của người dùng được giữ nguyên. Chưa push lên remote. Theo yêu cầu bổ sung, storefront Customer đã được xây lại từ template HTML do người dùng cung cấp; chi tiết hiện hành nằm trong [STOREFRONT_TEMPLATE_DELIVERY.md](STOREFRONT_TEMPLATE_DELIVERY.md).

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

## Giao diện Customer theo template được cung cấp

- Thay giao diện storefront Customer cũ bằng `vietfresh_fruit_home_and_category_pagination.html`: Home, catalog, danh sách shop, chi tiết shop, popup sản phẩm, header và footer. Xóa hai template chi tiết sản phẩm/shop cũ. CSS giữ nguyên nội dung `<style>` của bản gốc; không thêm phong cách mới.
- Template gốc được lưu tại `src/test/resources/storefront/reference.html` để đối chiếu. Dữ liệu demo trong script gốc được thay bằng truy vấn database thật, vì vậy số danh mục/shop/sản phẩm và nội dung card phụ thuộc database.
- Home và catalog giữ trang/kích thước trang riêng. Tất cả lựa chọn và mặc định lấy từ `common/PageSizes.java`; quy tắc phân trang và response nằm trong `common/Pagination.java`, `common/PageResponse.java`.
- Trang quản lý và giao diện của thành viên khác không được thiết kế lại. Trang yêu thích/review/ưu đãi liên quan dùng header/footer mới.
- Bỏ lựa chọn “hết hàng/chưa khả dụng” cho người mua. Điều kiện có thể mua được áp dụng bắt buộc ở backend, không chỉ ẩn nhãn ở HTML. Vì vậy dòng Filter by Availability trong sheet được thực hiện theo yêu cầu mới: catalog luôn lọc còn hàng; không cung cấp lựa chọn xem hàng hết tồn.
- Popup chọn SKU đang mua được theo giá chào bán. Link sản phẩm/SKU hết tồn hoặc bị ẩn trả HTTP 404.

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

**TV3 — Checkout/Cart:** `CatalogPricingService.offers(List<Long> variantIds)` trả `price`, `basePrice`, `availableStock` tại giá hiện hành. Cart đã dùng cùng offer để kiểm tra số lượng và tính giá; checkout lấy đơn giá đã tính trong cart để snapshot đơn hàng; reservation chỉ phân bổ lô có giá trùng đơn giá của dòng đơn. Với markdown, lượng khả dụng chỉ gồm các lô ở đúng mức giá chào bán. Các phần nối này được sửa để giá trên storefront không lệch khi thêm giỏ/tạo đơn, có kiểm thử bằng mocks; chưa tạo đơn/thanh toán thử trên SQL Server thật. Giao diện và luồng thanh toán của TV3 được giữ nguyên.

**TV5 — Promotion:** trang shop đọc trực tiếp promotion/coupon và usage đã có. Việc áp dụng ưu đãi/voucher lên đơn thuộc module của TV5/TV3. Không tự cộng dồn promotion và batch markdown trong catalog.

**Admin/System:** gọi `ShopAnnouncementService.receive(shopId, origin, announcementId, title, content)` trong transaction của sự kiện nguồn. `announcementId` phải ổn định và duy nhất trong từng `origin` để retry không tạo thông báo trùng. Đây là điểm tích hợp nội bộ, không có API công khai cho người mua tạo thông báo.

## Kiểm chứng

- Hồi quy sau khi thay template: **269 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** với JDK 21, Maven offline, datasource H2 cô lập và tắt scheduler cảnh báo kho. Test SQL Server được tách khỏi lượt này. Log: `target/template-full-test.log`; kết quả suite: `target/surefire-reports`.
- JavaScript: **3 tests đạt**; kiểm thử contract xác nhận CSS bằng nội dung style của template gốc và đủ các vùng điều khiển.
- Có kiểm thử SQL thực trên H2 cho giá/lượng theo markdown, lọc/sort, review, voucher, yêu thích và trạng thái cảnh báo; kiểm thử Spring MVC/Thymeleaf cho trang Guest/Customer, validation, CSRF và link cũ 404.
- Test SQL Server thật chỉ gửi GET, tắt/mock toàn bộ scheduler có thể ghi dữ liệu: **1 test đạt**, kiểm tra shell, tùy chọn chung, dữ liệu Home và lọc rating/địa điểm. Log: `target/template-sqlserver-test.log`. Chưa kiểm tra cạnh tranh khi đặt hàng trên database thật. Scheduler giao hàng/thanh toán có sẵn vẫn có thể ghi lỗi thiếu bảng trong lượt H2 rỗng; các assertion đều đạt.
- Đã kiểm tra trực quan trên trình duyệt desktop với dữ liệu thật: Home, lọc shop, chọn 5 sản phẩm/trang, trang 2, popup và chi tiết shop; khi quay lại Home, Home giữ kích thước trang riêng. Đã lưu screenshot. Chưa nghiệm thu riêng trên thiết bị mobile.

## Commit để duyệt

Mỗi chức năng/nhóm chức năng được tách thành commit cục bộ trên nhánh `Hoang-Van-Son`. Xem toàn bộ lần bàn giao bằng:

```text
git log --reverse --oneline 5064afa..HEAD
git diff 5064afa..HEAD
```

Commit `cf8f7db` áp dụng quy tắc chỉ hiển thị hàng mua được. Giao diện ở `6436619` đã được thay bởi template người dùng cung cấp trong `213e3db`. Các commit sau `856c715` gồm bộ lọc/API, phân trang common, template mới và nối giá bán sang giỏ/đơn/phân bổ lô. Cần sự đồng ý của người dùng trước khi push.
