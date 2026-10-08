# Dữ liệu demo và kiểm thử storefront

Đã seed vào SQL Server local `localhost:1433`, database `VietFresh_Hub`, ngày 08/10/2026. App kiểm thử chạy tại `http://localhost:8081/home`. App đang chạy sẵn trên 8080 không bị dừng.

## Đăng nhập

Mật khẩu được sinh ngẫu nhiên và chỉ lưu ở `.document-analysis/demo-credentials.json`, thư mục đã gitignore. Các tài khoản dưới đây dùng cùng mật khẩu test trong file đó; DB lưu BCrypt hash. Không commit file thông tin đăng nhập.

| Email | Mục đích |
| --- | --- |
| customer.demo@vietfreshhub.example | Giỏ mẫu 7 dòng: giá đổi, thiếu hàng, hết hàng, ngừng bán, lô hết hạn, shop đóng |
| empty.demo@vietfreshhub.example | Giỏ trống; dùng để thêm/sửa/xóa hàng, kiểm tra hai tài khoản không chung giỏ |
| blocked.demo@vietfreshhub.example | Tài khoản BLOCKED, đăng nhập bị từ chối |
| manager-a.demo@vietfreshhub.example | STORE_MANAGER, không được truy cập giỏ CUSTOMER |
| manager-b.demo@vietfreshhub.example | STORE_MANAGER cho shop demo thứ hai |
| manager-closed.demo@vietfreshhub.example | STORE_MANAGER của shop tạm đóng |
| applicant.demo@vietfreshhub.example | CUSTOMER có hồ sơ seller PENDING |
| admin.demo@vietfreshhub.example | ADMIN; trang đã có nghiệp vụ là `/admin/seller-applications` |
| delivery.demo@vietfreshhub.example | DELIVERY_STAFF, không được truy cập giỏ CUSTOMER |

Manager/delivery chỉ được dùng để test role hiện có. Các đường dashboard `/store_manager/shop`, `/delivery/delivery`, `/admin/admin` chưa có controller trong source hiện tại; không coi tài khoản seed là chứng minh các module đó đã hoàn thành.

## Bộ dữ liệu

- 9 tài khoản mới với email `.example`, 4 hồ sơ seller, 4 shop, 4 category.
- 18 sản phẩm, 34 biến thể có SKU `VF-DEMO-*`, 36 batch, 3 địa chỉ.
- 17 sản phẩm demo công khai; sản phẩm thuộc shop chờ duyệt bị ẩn. Home phân trang 12 sản phẩm/trang.
- Shop `[DEMO] Vườn Nhà Fresh`, `[DEMO] Miệt Vườn Fruit` mở; `[DEMO] Fresh Corner` đóng nhưng vẫn xem được; shop chờ duyệt không xuất hiện.
- Mọi sản phẩm demo có category. Giá, stock và hạn dùng là dữ liệu giả định; ảnh dùng các tài nguyên local đã có của dự án.
- Hạn dùng tính từ ngày seed. Có batch hết hạn và batch NULL hạn dùng để kiểm tra chúng không góp vào hàng có thể bán.

Giỏ `customer.demo` có 7 dòng, tổng quantity 12. Hai dòng đủ điều kiện mua tạo tạm tính **223.000 ₫**; các dòng còn lại không được tính vào tạm tính.

| Trường hợp | Dữ liệu để test |
| --- | --- |
| Giá đổi | Bưởi shop A: snapshot 69.000 ₫, giá hiện tại 79.000 ₫, quantity 2 |
| Hàng có phần reserved | Xoài shop B: on-hand 12, reserved 4, available 8, quantity 1 |
| Thiếu hàng | Sầu riêng shop B: available 2, quantity trong giỏ 5 |
| Hết hàng | Xoài shop A có variant OUT_OF_STOCK |
| Ngừng bán | Vải shop B có variant INACTIVE |
| Lô hết hạn | Bưởi giỏ quà shop B: lô còn quantity nhưng không bán được |
| Shop đóng | Bưởi Fresh Corner có stock nhưng không nhận đơn |

## Chạy lại demo

```powershell
.\scripts\start-demo.ps1
```

Script dùng JDK 21 và Maven wrapper; có thể đổi cổng qua `-Port 8082`. Seed chỉ chạy trong profile `dev` khi `app.seed.enabled=true` và có `APP_SEED_PASSWORD`. Tất cả insert nằm trong một transaction.

Khi đã có tài khoản marker `customer.demo@vietfreshhub.example`, seed bỏ qua lần chạy sau: không đặt lại mật khẩu, giỏ, giá hoặc tồn kho. Chạy lại app không tự khôi phục sản phẩm bạn đã xóa khỏi giỏ. Giữ file thông tin đăng nhập của lần seed đầu. Không có thao tác DROP/TRUNCATE/xóa dữ liệu hoặc sửa bản ghi hiện hữu trong seed.

## Mẫu UI tham khảo

- [Amazon Cart trong bộ mẫu Baymard](https://baymard.com/checkout-usability/benchmark/step-type/cart/28450-amazon): tham khảo cấu trúc danh sách sản phẩm và vùng tổng tiền.
- [Walmart Cart trong bộ mẫu Baymard](https://baymard.com/checkout-usability/benchmark/step-type/cart/28892-walmart): tham khảo phân cấp nội dung và bố cục tóm tắt đơn.
- [Baymard quantity controls](https://baymard.com/research-articles/auto-update-users-quantity-changes): +/- kết hợp input, cập nhật tự động và quantity 0 để xóa.

Storefront dùng branding VietFresh Hub: header search thật, nhóm trái cây, grid card, trang phân loại, nhóm shop trong giỏ, summary bên phải và layout mobile. Không thêm rating/discount/đã bán/phí ship giả hoặc các nút chức năng chưa có nghiệp vụ. Tìm kiếm dùng parameter tại DB; từ khóa được giữ khi phân trang.

## Kiểm chứng đã thực hiện

- 50 test pass: unit service, HTTP security/Thymeleaf, JPA/H2, login/session và seed.
- App thật với SQL Server: customer login, tìm kiếm Xoài, tạm tính/warnings của giỏ, product detail, hai tài khoản tách giỏ, thiếu CSRF bị 403, thêm/sửa/xóa và hai request thêm đồng thời cộng đúng quantity.
- Manager xác thực thành công và bị 403 khi truy cập `/cart`.
- Ảnh UI và kiểm tra không tràn ngang ở 360, 1440, 1920 px với home, cart, product và empty cart.
- Sau kiểm tra live, giỏ mẫu `customer.demo` giữ nguyên; giỏ tài khoản `empty.demo` đã được trả về trống.

Checkout COD đã triển khai sau bước seed; xem TV3_CHECKOUT_COD.md. Có một đơn thật theo luồng phần mềm (order_id=1) của empty.demo để test, payment vẫn chưa thu tiền. Seed dev bổ sung phương thức COD nếu thiếu và không đặt lại giỏ/order. VNPAY và lịch sử/hủy/tracking/reorder là các bước sau.
