# Đối chiếu database nhóm và project — 13/10/2026

## Nguồn và kết quả

Đối chiếu hai file người dùng cung cấp:

- `D:/S5/SWP391/Database/VietFreshHub_Schema.sql`: 1.857 dòng, 60 bảng, 9 trigger, các view public catalog và procedure danh mục/duyệt sản phẩm/chuẩn bị giao hàng.
- `D:/S5/SWP391/Database/VietFreshHub_SeedData.sql`: 3.264 dòng, một transaction nạp dữ liệu mẫu có đối soát kho, allocation, tổng tiền và quyền.

SHA-256 của schema: `268150776c87638e2986bc84092231b2b2bbc9be31eab50eaf734f96e83bc938`.

SHA-256 của seed: `a7ad04f727a594490ae630cc1486cad9d5b3f493a403bb08144acd3fd22cf2d2`.

Các hướng dẫn chạy SQL trong comment là tài liệu của nhóm. Lần đối chiếu này không chạy schema/seed hoặc sửa hai file nguồn. SQL Server local đã có `VietFresh_Hub_Final`: 60 bảng, 24 tài khoản, 100 sản phẩm, 25 đơn và 44 allocation. Database `VietFresh_Hub` cũ có 53 bảng.

Project tiếp tục dùng Spring Boot 3.4.1, Java 21, Spring MVC, Thymeleaf, session và CSRF. Không đổi sang REST hoặc đổi schema để ép code cũ chạy.

## Conflict đã xử lý

| Điểm đối chiếu | Trước đây | Sau đồng bộ |
| --- | --- | --- |
| Database đích | Cấu hình trỏ `VietFresh_Hub` | Mặc định `VietFresh_Hub_Final`, có thể cấu hình URL bằng `DB_URL`; vẫn dùng `validate`, không dùng `update/create` |
| InventoryBatch | Mapping cột `batch_code` không còn tồn tại; thiếu status | Bỏ `batch_code`, mapping `status` ACTIVE/BLOCKED |
| Tồn khả dụng | Đếm cả lô BLOCKED, loại mọi lô NULL expiry | Chỉ ACTIVE và `(expiry_date IS NULL OR expiry_date >= ngày Việt Nam)` như view public mới |
| FEFO | Chỉ giữ lô có expiry | Lô có hạn dùng xếp trước theo ngày/batch ID; lô NULL expiry xếp cuối; không giữ lô BLOCKED |
| Allocation | Chỉ ghi RESERVE trong inventory_transactions | Checkout tạo cả `stock_allocations` RESERVED và nhật ký RESERVE trong cùng transaction |
| Hủy đơn | Suy luận phân bổ từ journal, không cập nhật stock_allocations | Lock allocation và batch, đối chiếu journal, kiểm tra đủ lượng theo item, chỉ trả RESERVED của đơn; đổi RELEASED và ghi RELEASE âm như seed |
| SKU bắt buộc | Thiếu thumbnail_url và unit trong entity/Java seed | Mapping đủ trường bắt buộc, compare_at_price và weight; fixture ghi thumbnail/unit rõ ràng |
| Ảnh hiển thị | Chủ yếu suy đoán ảnh local theo tên/slug | Dùng thumbnail SKU và ảnh primary từ product_images; ảnh local chỉ là fallback |
| Publication | Product mặc định ACTIVE/PENDING; public catalog yêu cầu application APPROVED nhưng thiếu kiểm tra danh mục | Product mặc định INACTIVE; public catalog theo ACTIVE/APPROVED, shop ACTIVE, SKU ACTIVE/OUT_OF_STOCK, và danh mục có toàn bộ tổ tiên ACTIVE; không bắt shop phải có application_id |
| Trigger publication | Java demo seed publish product trước khi tạo SKU | Tạo INACTIVE/PENDING, gắn danh mục và SKU rồi publish; không thay trigger của nhóm |
| Địa chỉ | District bắt buộc; text có thể chứa `null`; bỏ metadata khi snapshot | District optional ở entity/form/UI; bỏ phần trống khi hiển thị; snapshot ID hành chính, formatted_address và cặp tọa độ |
| Chuẩn bị hàng | Không mapping snapshot do trigger quản lý | Mapping snapshot chỉ đọc; DB trigger ghi yêu cầu/note, code không ghi đè khi update item |
| Giao lại | Thiếu trường ngày giao lại và nhãn status mới | Mapping redelivery_requested_at/redelivery_scheduled_at và nhãn REDELIVERY_SCHEDULED |
| Thời gian | Hibernate timestamp VM có thể dùng timezone máy | Lifecycle callback lưu UTC chính xác đến giây; ngày tồn kho và hiển thị dùng UTC+7; cấu hình JDBC UTC |
| File hồ sơ seed | `file_url` là URL HTTPS nhưng code xem như Cloudinary public ID | Giữ nguyên URL HTTPS; upload riêng vẫn dùng signed private URL từ public ID |
| Java seed và seed nhóm | Có thể thêm fixture demo vào database nhóm nếu bật dev seed | Nhận diện tài khoản admin seed nhóm và bỏ qua Java demo seed, giữ dữ liệu nhóm |

Release adapter chấp nhận cả RELEASE âm theo schema/seed mới và RELEASE dương của journal cũ khi đối soát. Tuy nhiên, đơn cũ thiếu allocation không được tự suy diễn rồi giải phóng kho. Muốn chuyển dữ liệu từ database cũ phải có migration/đối soát riêng; lần này không chuyển hoặc backfill dữ liệu cũ.

## Phạm vi nghiệp vụ giữ nguyên

Giữ checkout theo sản phẩm đã chọn, phí giao hàng cấu hình theo shop (mặc định 20.000₫), COD PENDING, transaction đa shop, snapshot giá/tên/địa chỉ, token chống tạo đơn trùng, quyền sở hữu giỏ/địa chỉ/đơn, rollback khi thiếu kho, hủy hợp lệ, reorder vào giỏ và tracking chỉ đọc.

Schema mới bổ sung các trường hoặc bảng phục vụ module khác không đồng nghĩa đã triển khai chức năng mới. Không tự bật coupon/loyalty, thanh toán online, payout, subscription, review/chat, batch markdown hoặc mutation giao hàng. Các đơn seed có ưu đãi/đã trả tiền vẫn áp dụng điều kiện hủy an toàn hiện có. Giá/ship seed lịch sử giữ nguyên; không sửa chúng về cấu hình checkout hiện tại.

28 bảng có entity trong project; `product_categories` được xử lý qua join table. Các bảng còn lại của schema được giữ nguyên và chưa cần entity nếu project chưa dùng. Những nhóm bảng đã đọc gồm auth/hồ sơ, shop/địa điểm/giờ mở cửa, catalog/ảnh/giá/duyệt sản phẩm, inventory/allocation, cart/order/preparation, promotion/coupon, payment/payout, delivery, loyalty/subscription, review, notification, chat và stored_files/audit. Không có entity bắt buộc phải ghi vào bảng chưa triển khai.

## Kiểm chứng

Kết quả cuối: **BUILD SUCCESS, 108 tests, 0 failures, 0 errors, 0 skipped**.

- 104 test hồi quy/unit/H2: auth, quyền/CSRF, giỏ, checkout, race/duplicate submit, rollback, release đúng allocation, lô BLOCKED/NULL expiry, địa chỉ optional và metadata, ảnh DB, tổ tiên danh mục, UTC độc lập timezone máy, URL hồ sơ seed.
- 4 test SQL Server opt-in: khởi động toàn bộ app và Hibernate schema validate; đăng nhập các role seed; đối chiếu số sản phẩm public với view; đọc chi tiết/tracking/lịch sử 25 đơn; render home/cart/checkout/orders/detail/tracking/admin bằng Thymeleaf; checkout một phần, duplicate token, snapshot địa chỉ và trigger preparation; hủy đơn mới và đơn COD seed qua allocation.
- Test SQL Server dùng transaction rollback, không chạy DDL, không gọi cổng thanh toán hoặc upload file. SQL Server có thể để lại khoảng trống IDENTITY khi insert bị rollback; bản ghi và lượng kho của nhóm được giữ lại.
- Kiểm tra tĩnh mapping: 60 bảng schema, 28 bảng entity, không thấy cột entity bị thiếu trong schema hoặc trường NOT NULL không có default bị bỏ sót trong các entity được dùng. Kiểm tra `git diff --check` không có lỗi whitespace.

Test SQL Server nằm ở `src/test/java/VietFreshHub/Database/GroupSchemaSqlServerTest.java`, chỉ bật khi `VF_SCHEMA_SQLSERVER_TESTS=true`, yêu cầu đúng database `VietFresh_Hub_Final`. Các test H2 tạo schema riêng trong memory. `VietFreshHubApplicationTests` cũ không nằm trong lần chạy này; app context thực tế đã được bao phủ bởi nhóm SQL Server test.

Chạy lại trong PowerShell với JDK 21 và Maven wrapper, dùng `-Dtest` chọn các lớp trên. Mockito trên máy này cần `-DargLine=-javaagent:C:/Users/Admin/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar` để tránh lỗi tự attach JVM của Windows.

Đã kiểm tra HTML qua MockMvc với dữ liệu SQL Server thật; chưa đánh giá lại layout trong trình duyệt hoặc đo tải/coverage. Các nhận xét về schema/seed không được suy ra thành đã hoàn thiện mọi module trong database.
