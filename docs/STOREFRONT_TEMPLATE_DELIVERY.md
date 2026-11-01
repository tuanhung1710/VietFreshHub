# Storefront Customer theo template HTML

Nguồn thiết kế: `C:/Users/sonhv/Downloads/vietfresh_fruit_home_and_category_pagination.html`.
Bản đối chiếu được giữ tại `src/test/resources/storefront/reference.html`.
Thay đổi này thay thế thiết kế storefront Customer trước đó; không thiết kế lại giao diện quản lý, tài khoản, checkout hoặc chức năng của thành viên khác.

## Đã xử lý

- Dùng shell và CSS của template cho Home, catalog/danh mục, danh sách shop, shop detail và popup sản phẩm. `customer-shop.css` bằng nội dung `<style>` của bản gốc sau khi chuẩn hóa xuống dòng, có kiểm thử bảo vệ.
- Xóa template product detail/shop detail cũ và các handler trùng. Các URL công khai cũ được chuyển về shell mới hoặc trả 404 khi đối tượng không còn công khai.
- Thay dữ liệu JavaScript demo bằng API truy vấn database: danh mục, shop, sản phẩm, giá chào bán, tồn có thể bán, địa điểm, điểm đánh giá đã duyệt và số lượng bán từ đơn hoàn tất. Giữ biểu tượng trái cây của template.
- Tìm theo tên sản phẩm/shop/danh mục; lọc danh mục, nhiều shop, nhiều địa điểm, khoảng giá và mức sao; sắp xếp phổ biến/mới nhất/bán chạy/giá/rating. Bộ lọc được giữ trên URL và khi phân trang.
- Chỉ trả sản phẩm/SKU đang hoạt động, shop hoạt động và đang nhận đơn, còn lượng khả dụng tại giá chào bán. Không có lựa chọn xem hàng hết tồn hoặc chờ hàng trong catalog Customer.
- Home và catalog giữ trang và kích thước trang độc lập. Chọn kích thước trang, nút trước/sau, tổng trang và xử lý trang vượt phạm vi đều nối dữ liệu thật.
- Popup đọc giá/SKU thật; thêm giỏ có CSRF và yêu cầu Customer đăng nhập. Cart/checkout/reservation dùng cùng giá chào bán để tránh chuyển về giá gốc hoặc lấy nhầm lô có giá khác.

## Điều chỉnh phân trang chung

Sửa tại `src/main/java/VietFreshHub/common/PageSizes.java`:

```java
public static final int DEFAULT = 10;
public static final List<Integer> OPTIONS = List.of(5, 10, 20, 50);
```

Giữ `DEFAULT` thuộc `OPTIONS`. Storefront lấy hai giá trị này từ `/storefront/api/options`, không khai báo lại danh sách kích thước trang trong JavaScript. `Pagination` chuẩn hóa trang bắt đầu từ 1, giới hạn offset an toàn và đưa trang vượt phạm vi về trang cuối; `PageResponse` thống nhất metadata. Các danh sách product/inventory, shop và thông báo dùng mặc định chung trong phạm vi đã sửa.

## URL và dữ liệu

| URL | Vai trò |
| --- | --- |
| `/home`, `/customer/home` | Shell storefront mới |
| `/shops`, `/shops/{id}` | Danh sách/chi tiết shop trong shell mới |
| `/product-detail?productId=...`, `/product-detail/{variantId}` | Mở popup của sản phẩm/SKU công khai trong shell mới |
| `GET /storefront/api/options` | Danh mục, shop và cấu hình page size |
| `GET /storefront/api/home` | Sản phẩm Home và top bán chạy độc lập |
| `GET /storefront/api/products` | Catalog có bộ lọc, sort và phân trang |
| `GET /storefront/api/products/{id}` | Dữ liệu popup sản phẩm; hỗ trợ chọn SKU |
| `GET /storefront/api/cart` | Số lượng trong giỏ của phiên hiện tại |
| `POST /storefront/api/cart` | Thêm SKU đang mua được, kiểm tra CSRF/quyền Customer |

Lần đọc database hiện tại có 20 sản phẩm tổng, 3 shop và 37 đánh giá đã duyệt. Catalog công khai trả 11 sản phẩm mua được. Số lượng/nội dung card khác dữ liệu demo gốc vì lấy database thật; không thêm seed giả để lấp các ô trong template. Chưa chạy migration hoặc ghi dữ liệu database trong lượt kiểm tra này.

## Kiểm chứng và phạm vi còn lại

- Maven JDK 21 offline với H2 cô lập: 269 tests, 0 failures, 0 errors, 0 skipped; loại riêng test SQL Server opt-in. Log: `target/template-full-test.log`.
- JavaScript: `node --test src/test/javascript/storefront.test.mjs`, 3 tests đạt. Contract CSS/HTML nằm trong `StorefrontTemplateContractTest`.
- SQL Server thật: `StorefrontDatabaseReadTest` bật bằng `-Dstorefront.sqlserver=true`, chỉ GET; cả ba scheduler có thể ghi dữ liệu đã tắt/mock. 1 test đạt, log `target/template-sqlserver-test.log`.
- Trình duyệt desktop: xác nhận Home, catalog lọc Vườn Nhà Sơn còn 6 sản phẩm, 5 sản phẩm/trang, trang 2 còn 1 sản phẩm, popup và shop detail; Home vẫn giữ 10 sản phẩm/trang riêng.
- Chưa nghiệm thu riêng trên thiết bị mobile và chưa tạo đơn/thanh toán trên database thật. Logic nối giá/stock có kiểm thử mocks, chưa kiểm tra tranh chấp nhiều giao dịch đồng thời.
- Nội dung trợ giúp/chính sách và hành vi thông báo checkout giữ theo template; chức năng checkout/voucher thuộc phần bàn giao của thành viên khác. Không thêm quy trình mới ngoài phạm vi Sơn.

Ảnh kiểm tra nằm trong thư mục artifact của chat: `storefront-home.png`, `storefront-catalog.png`, `storefront-popup.png`.
Các thay đổi chưa commit đã có trong workspace được giữ lại; chỉ stage đúng file của chức năng này. Test inventory cũ được sửa một kỳ vọng từ page size 5 bị đổi thành 10 sang chấp nhận 5 theo common mới; file vốn chưa được track được giữ nguyên trạng thái untracked.

## Commit để duyệt

```text
git log --reverse --oneline 856c715..HEAD
git diff 856c715..HEAD
```

Các commit tách riêng bộ lọc catalog, phân trang common, API storefront, giao diện theo template và nối giá chào bán. Chỉ commit cục bộ trên nhánh hiện tại. Chưa push; push cần sự đồng ý của người dùng.
