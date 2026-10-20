# Task 05 — Tracking customer

Ngày thực hiện: 20/10/2026. Đối chiếu lại tài liệu SRS gốc, phần chi tiết **UC-23 Track Order**, GBR-24 và GBR-31: chỉ chủ đơn được xem; hiển thị trạng thái/timeline và lý do giao thất bại; không sửa dữ liệu khi xem tracking.

## Thay đổi trong TV3

- `OrderTrackingResponse.ShopTracking` thêm `failureReason`, đọc từ mapping delivery hiện có; không sửa schema hoặc entity của TV4.
- `CustomerOrderServiceImpl` đưa lý do hiện tại tới DTO khi delivery FAILED và shop chưa CANCELLED. Chuẩn hóa khoảng trắng, giữ null khi thiếu. Không gắn lý do hiện tại vào các sự kiện FAILED cũ vì DB không lưu snapshot lý do theo từng lần thử.
- Thêm nhãn customer cho REDELIVERY_SCHEDULED và delivery CANCELLED. Đơn shop CANCELLED vẫn được ưu tiên trước thông tin delivery cũ.
- Timeline sắp xếp sau khi bổ sung xác nhận, theo thời gian UTC đã đổi sang giờ Việt Nam. Dữ liệu thiếu thời gian đứng cuối và UI ghi “Chưa ghi nhận thời gian”; không tạo thời gian giả hoặc lỗi comparator với null.
- `customer/order-tracking.html` hiển thị lý do dưới trạng thái thất bại. Thiếu lý do thì hướng dẫn liên hệ cửa hàng. Dùng `th:text` để escape nội dung, không render HTML người dùng nhập.

Các constructor/method của service, ownership query, read-only transaction và truy vấn staff theo đơn giữ nguyên. Không sửa module TV4, notification, checkout, cấu hình ứng dụng hoặc DB nhóm trong task này.

## Kiểm thử

`CustomerOrderTrackingTest`: **18 ca**, gồm lý do thật/null/rỗng, lý do cũ sau đổi trạng thái, hủy shop, hẹn giao lại, timeline thiếu thời gian/đổi UTC/sắp xác nhận, tránh lặp hoàn tất, không lộ tên staff của delivery khác, customer khác bị từ chối trước khi đọc dữ liệu, đơn chưa có delivery. Ba ca render panel từ template thật kiểm tra lý do/escape/fallback/ẩn khi đã giao thành công; không render navigation hay chạy security filter chain.

SQL integration dùng lại harness có marker DB riêng: tạo đơn thật qua checkout, thêm fixture delivery FAILED trực tiếp trong DB test, đọc qua JPA/repository/transaction thật, đối chiếu đúng lý do và kiểm tra customer khác không được xem. So sánh deliveries và kho trước/sau để xác nhận tracking không ghi dữ liệu. Đây là kiểm thử read model của TV3, không phải nghiệm thu thao tác giao thất bại của TV4; staff provider được mock riêng trong ca SQL này và kiểm thử scope bằng unit test.

Script hồi quy `scripts/tv3-sqlserver-tests.ps1` đã thêm tracking test. Lượt đầu của ca SQL không tìm thấy FAILED trong seed; fixture đã được đổi sang tự tạo và không coi lượt thất bại đó là PASS.

Khi thêm đơn tracking, suite cũng phát hiện các test cũ dùng chung SKU seed gần hết tồn, khiến ca hủy đa shop phụ thuộc thứ tự chạy. Harness đã tạo hai SKU fixture riêng ở hai shop, mỗi SKU có batch 100 và ledger RESTOCK tương ứng. Chỉ ghi vào DB test có marker, không sửa SQL nguồn hay tăng tồn production. Các ca tranh đơn vị cuối/FEFO vẫn dùng SKU và số lượng riêng như trước.

## Giới hạn và bước tiếp theo

Nghiệm thu cuối: **127/127 test PASS**, 0 failure/error/skipped trong một lượt Maven: 79 Cart/Checkout, 14 cancellation unit, 18 tracking unit/template và 16 SQL integration. DB riêng: `VietFreshHub_TV3_Test_8e7b8a9186fc4324a29e860907f55c8a`. Không cộng các lượt chạy lại vào số test.

- SRS nói tới thông báo đã gửi. Timeline hiện đọc lịch sử trạng thái; chưa xác nhận notification đã được gửi/đọc và không hiển thị khẳng định đó. Việc tích hợp notification cần hợp đồng với TV1/TV4.
- `failure_reason` hiện là giá trị của delivery, chưa có lịch sử lý do riêng từng lần thử. Không tự sửa mô hình/luồng ghi của TV4 để tạo snapshot. Không hiển thị lý do cũ như một lỗi hiện tại sau khi giao lại/giao thành công.
- Chưa đo coverage hoặc nghiệm thu security chain/CSRF toàn ứng dụng. Ownership đã được kiểm tra tại service và query DB.
- Task tiếp theo: history/reorder customer theo UC-24/GBR-26 — giá và tồn hiện tại, cộng với giỏ đang có, báo món bị loại, phân trang/bộ lọc/ownership; không giữ kho hoặc tạo đơn khi chỉ mua lại.
