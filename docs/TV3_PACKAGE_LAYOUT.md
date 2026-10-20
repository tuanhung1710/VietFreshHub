# Quy ước service của TV3

Cập nhật 20/10/2026 theo mẫu `cart/service/impl` người dùng cung cấp.

```text
checkout/service/
  CheckoutService.java                  (interface)
  CheckoutAddressService.java           (interface)
  impl/
    CheckoutServiceImpl.java
    CheckoutAddressServiceImpl.java

order/service/
  CustomerOrderService.java             (interface)
  CustomerOrderQueryService.java        (interface)
  OrderService.java                     (interface TV4, giữ nguyên)
  impl/
    CustomerOrderServiceImpl.java
    CustomerOrderQueryServiceImpl.java
    OrderServiceImpl.java               (TV4, giữ nguyên)
```

Cây trên chỉ liệt kê các cặp interface/implementation. Các class còn lại trong `service` vẫn giữ vị trí:

- `InventoryReservationService`, `ShippingFeeService`, `CheckoutShippingQuoteService`, `OrderInventoryReleaseService`, `PaymentService`: service cụ thể, hiện không có interface tương ứng. Có thể tiêm qua constructor và dùng transaction mà không cần hậu tố Impl. Chưa tạo thêm interface chỉ để chuyển thư mục.
- `CheckoutDraftStore`: component quản lý draft trong session; không phải implementation của service interface.
- `OrderPlacedEvent`, `OrderCancelledEvent`: dữ liệu sự kiện; không phải service implementation.
- `OrderLabels`: helper định dạng nhãn/thời gian; không phải service implementation.

`impl` là package con độc lập trong Java, không tự kế thừa import hoặc quyền truy cập package của `service`. Vì vậy bốn class được chuyển phải import lại interface và các dependency trước đây cùng package; test khởi tạo implementation phải đổi import/FQCN. Controller vẫn tiêm interface, tên bean mặc định theo tên class không đổi. Không đổi constructor, annotation, transaction, tên class hoặc nghiệp vụ.

Trước đây các class Impl nằm cùng interface là cách tổ chức hợp lệ nhưng chưa đồng nhất với cart; không có yêu cầu kỹ thuật buộc chúng ở đó. Sau thay đổi, TV3 thống nhất: có interface thì implementation nằm trong `service.impl`; standalone service giữ ở `service`.

Không sửa implementation TV4 hoặc module thành viên khác. Không thay nội dung nghiệp vụ đang có trong các file được chuyển.

Kiểm tra: Maven clean thành công, compile lại toàn bộ 326 source files và chạy **157/157 test PASS**, 0 failure/error/skipped, bao gồm 20 SQL integration trên DB riêng `VietFreshHub_TV3_Test_09580a18ceea4451b00c3fff70963716`. Không dùng class cũ còn sót trong target để làm kết quả compile có vẻ thành công.
