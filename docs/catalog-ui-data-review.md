# Đối chiếu UI và dữ liệu catalog với TV2 — 20/10/2026

## Đã sửa

- `/home` trước đây redirect sang `/customer/home` nhưng bỏ `page`, `keyword`: trang 2 thực tế tải lại trang 1. Đã giữ hai tham số qua redirect.
- Phân trang dùng tổng số **sản phẩm hợp lệ theo bộ lọc**, 12 sản phẩm/trang; 14 sản phẩm tương ứng 12 + 2, không phải 14 phân loại. Không render thanh phân trang khi tổng trang <= 1.
- Khi dữ liệu giảm khiến số trang yêu cầu vượt trang cuối, catalog lấy trang cuối còn tồn tại.
- Bỏ thanh tiện ích trùng menu user. Thu gọn header, ảnh chi tiết, font, khoảng cách và nút trên các trang khách hàng; không dùng browser zoom.
- Nhãn thẻ phân biệt hết hàng với tạm ngừng nhận đơn.

## Phạm vi xác minh

Đã review mã nguồn catalog, quản lý sản phẩm và tồn kho. Không kết nối được SQL Server tại địa chỉ cấu hình trong phiên này (không thấy cổng 1433 lắng nghe), nên **chưa có thống kê DB thực tế**. Con số 14 trong ảnh là tổng mà giao diện từng hiển thị, không phải kết quả kiểm kê DB mới. Không thay đổi trạng thái hay dữ liệu sản phẩm.

Chạy `docs/catalog-data-audit.sql` trong DB đang dùng để lấy từng product_id, các lý do bị ẩn, tồn khả dụng, số sản phẩm hiển thị và số trang. SQL chỉ SELECT.

## Quy tắc hiện tại cần thống nhất

| Vấn đề | Trang khách hàng | Quản lý TV2 / inventory | Đề xuất thống nhất |
|---|---|---|---|
| Nguồn dữ liệu | `CatalogProduct`, `CatalogVariant` | `Product`, `ProductVariant` | Cùng bảng `dbo.products`, `dbo.product_variants`; đối chiếu bằng product_id / variant_id, không so số SKU với số sản phẩm. |
| Duyệt sản phẩm | ACTIVE + APPROVED mới hiển thị | Phân loại quản lý trong `ProductManagementRepositoryImpl.CLASSIFICATION` không xét approval_status | Thống nhất APPROVED là điều kiện công khai; TV2 hiển thị riêng trạng thái duyệt. Không tự bật các bản ghi PENDING/REJECTED. |
| Khóa kiểm duyệt | Catalog chưa kiểm tra trực tiếp moderation_blocked | TV2 ưu tiên violation khi moderation_blocked=1 | Thống nhất cờ khóa là điều kiện bắt buộc ở catalog/checkout; cần kiểm tra các đường cập nhật trạng thái trước khi hợp nhất rule. |
| Shop | Shop ACTIVE mới hiển thị; OPEN mới mua được | TV2 xếp unavailable nếu không ACTIVE/OPEN | Phân biệt rõ “hiển thị nhưng shop ngừng nhận đơn” và “ẩn khỏi sàn”. Không dùng số sản phẩm đang bán của shop làm tổng catalog. |
| Danh mục | Ít nhất một danh mục ACTIVE, toàn bộ tổ tiên ACTIVE | TV2 cũng truyền tập danh mục hợp lệ vào truy vấn quản lý | Giữ quy tắc tổ tiên; báo lý do khi danh mục cha bị tắt. |
| Phân loại | ACTIVE hoặc OUT_OF_STOCK mới hiển thị; ACTIVE + tồn > 0 mới mua | TV2 còn kiểm giá nguyên đồng >= 10.000 | Dùng chung validator giá cho catalog, giỏ hàng và checkout. Không để giá cũ không hợp lệ được gọi là mua được. |
| Tồn khả dụng | Tổng on_hand - reserved ở lô ACTIVE, chưa hết hạn | TV2 cộng max(0, on_hand - reserved) theo từng lô hợp lệ | Dữ liệu chuẩn on_hand >= reserved cho cùng kết quả; dữ liệu lỗi âm sẽ lệch. Cần đối soát các lô âm trước khi sửa công thức dùng chung. |
| Giá giảm theo lô | Catalog dùng giá gốc variant.price | TV2 tính offerPrice / offerQuantity từ batch markdown | Chốt giá hiển thị và giá checkout có áp dụng markdown hay không; chưa tự đưa giá giảm lên catalog vì cần đồng nhất tính tiền. |
| Ảnh | Thumbnail phân loại -> ảnh sản phẩm -> ảnh suy từ tên/slug; cuối cùng mặc định bưởi | TV2 quản lý ảnh SKU / ảnh sản phẩm | Bỏ ảnh bưởi mặc định cho sản phẩm không liên quan trong một thay đổi riêng; dùng placeholder chung để không gây hiểu nhầm. |
| Chip danh mục | Bưởi/Xoài/... hiện tìm theo từ khóa tên | TV2 có category_id | Nếu muốn lọc đúng danh mục, API/UI cần truyền category_id và count cùng điều kiện; hiện chưa phải bộ lọc danh mục thực sự. |

## Nguồn mã để cùng review

- `auth/controller/AuthController.java`: redirect `/home`.
- `catalog/repository/CatalogProductRepository.java`: truy vấn danh sách và countQuery cùng điều kiện; dùng EXISTS nên không nhân số sản phẩm theo số danh mục/SKU.
- `catalog/service/impl/CatalogServiceImpl.java`: isVisible, isPurchasable, chọn SKU có thể mua giá thấp nhất, 12 sản phẩm/trang.
- `catalog/service/CatalogCategoryPolicy.java`: quy tắc danh mục cha.
- `product/repository/ProductManagementRepositoryImpl.java`: CLASSIFICATION, VARIANT_STOCK.
- `inventory/rule/InventoryRules.java`: tồn theo lô, giá tối thiểu, ngày hết hạn.
- `checkout/repository/CheckoutInventoryBatchRepository.java`: tổng tồn khả dụng phía mua hàng.

Các đề xuất hợp nhất nghiệp vụ ở bảng trên là kết quả review, chưa được tự áp dụng vào dữ liệu hoặc checkout trong đợt sửa UI này.
