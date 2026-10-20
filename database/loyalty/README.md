# Loyalty VND

## Phạm vi đã rà

- `loyalty`: Entity, DTO, repository, service, controller và ba template. Repository không có truy vấn dựa trên tên cột điểm, nên tiếp tục dùng mapping Entity mới.
- Checkout: DTO, controller preview bằng submit Thymeleaf, service xác nhận, hidden input, JavaScript đổi địa chỉ và `event.submitter`.
- Liên kết Loyalty ở trang chủ và header; cấu hình Admin tích/sử dụng thưởng.
- Order/Payment: Payment được tạo sau coupon và tiền thưởng. Hàm hủy trong `CustomerOrderServiceImpl.cancellationHint` đang chặn đơn có giảm giá hoặc cần đối soát/hoàn tiền; giữ nguyên bảo vệ này. Chưa có service hoàn Loyalty tự động. Enum REFUND/ADJUSTMENT và lịch sử được giữ, giá trị chuyển sang tiền.
- Không tìm thấy script SQL, Flyway/Liquibase migration hoặc seed Loyalty có sẵn trong workspace tại thời điểm bắt đầu. Các file ở thư mục này là script thủ công, không tự chạy khi khởi động. Script 01 cũng kiểm tra cột `earning_percent`/`min_redemption_amount` để giữ cấu hình nếu môi trường đã có migration rules riêng.
- Không thấy lời gọi `earnPoints` từ Order/Delivery. API thay bằng `earnReward(orderId)`, chỉ cộng khi đơn tổng COMPLETED và PAID. Chưa gắn thêm sự kiện giao hàng; không đổi trạng thái đơn/thanh toán để tạo thưởng.

## Công thức mới

`thưởng nhận = floor((subtotal - discountTotal) * earning_percent / 100)`.

`discountTotal` gồm coupon và tiền thưởng đã dùng, không gồm ship. `1.00` trong DB nghĩa là `1%`. Dùng `1 ₫` thưởng giảm đúng `1 ₫` tiền hàng; số nhập nguyên, không âm, tối đa 16 chữ số. `0` bỏ sử dụng và không phụ thuộc bật/tắt redemption. Mức dùng khác 0 phải đạt `min_redemption_amount`, đủ số dư và không vượt `max_redemption_percent` tiền hàng sau coupon.

Mặc định trong SQL: 1%, 1.000 ₫, 20%. Java đọc quy tắc từ DB. Giữ khóa User → Order → Account, transaction Checkout, chống xử lý trùng, chủ sở hữu, trạng thái đơn/thanh toán và phân bổ shop đến 0,01 ₫ như luồng cũ; tổng phần phân bổ luôn khớp số tiền giảm nguyên đồng. Preview không ghi dữ liệu.

Ví dụ: 200.000 - coupon 20.000 - thưởng 10.000 + ship 15.000 = **185.000 ₫**, tích **1.700 ₫** khi đủ điều kiện.

## Thứ tự SQL cho DB đang có dữ liệu

**Chưa chạy các script này trên DB dự án.** Dừng ứng dụng, sao lưu, chạy thử trên bản sao SQL Server và đối soát trước khi chuyển môi trường thật. Không để Hibernate tự đổi schema trước migration.

1. `01_prepare_vnd_schema.sql`: giữ nguyên ID và quan hệ Account/Transaction; đổi tên cột điểm thành `legacy_*`, thêm cột tiền DECIMAL(18,2) đang NULL. Giữ toàn bộ bảng cấu hình cũ thành `loyalty_rules_points_legacy`, tạo bảng rules đích. Không chuyển số điểm thành số đồng. Không tự chạy lại nếu đã chuẩn bị.
2. **Xác nhận chính sách chuyển điểm lịch sử sang VND trước khi chạy.** `02_convert_legacy_data.sql` hiện đặt `@ApprovedVndPerLegacyPoint = 100`, nghĩa là **100 VND cho mỗi điểm cũ**. Người chạy phải xác nhận tỷ lệ này đã được duyệt và phù hợp với dữ liệu lịch sử; script không tự dừng để chờ xác nhận khi giá trị đang là 100. Nếu tỷ lệ phụ thuộc thời kỳ/loại giao dịch, cần thay phần chuyển đổi bằng mapping từng dòng đã đối soát; không áp một tỷ lệ chung. Nếu phát sinh đồng lẻ, script dừng, không âm thầm làm tròn phá sổ giao dịch. Tỷ lệ 100 VND/điểm cũ chỉ dùng để chuyển dữ liệu lịch sử; tỷ lệ tích thưởng mới 1% áp dụng cho đơn hàng đủ điều kiện theo công thức mới, không dùng để tính lại lịch sử. Nếu không có Account/Transaction cũ, có thể bỏ bước chuyển dữ liệu này.
3. `03_finalize_vnd_schema.sql`: chỉ tiếp tục khi toàn bộ cột tiền đã chuyển xong; đặt NOT NULL, kiểm tra tiền nguyên/dấu/số dư, thêm unique index chống EARN/REDEEM lặp theo Account/Order. Dữ liệu trùng sẽ khiến bước này thất bại, không bị xóa. Cho phép các cột legacy NULL đối với bản ghi mới; vẫn giữ dữ liệu cũ để kiểm toán.
4. Đối chiếu số lượng/ID, số dư, tổng tích/dùng, tổng giao dịch theo Account, dấu giao dịch, balance-after và sai lệch nếu có từ dữ liệu cũ. Không tự sửa sai lệch lịch sử. Chỉ khởi động code mới khi đã đối soát và bước 03 thành công.

Với DB mới hoàn toàn: dùng riêng `fresh_install.sql` sau khi có `dbo.users` và `dbo.orders`; **không** chạy kèm 01–03. Script từ chối nếu đã có bất kỳ bảng Loyalty nào.

## Kiểm tra tự động

```powershell
./mvnw.cmd -q -DskipTests compile
./mvnw.cmd -q '-Dtest=LoyaltyAccountViewTest,LoyaltyMoneyTest,LoyaltyRuleFormTest,LoyaltyCheckoutTransactionTest,CheckoutCouponFormTest,AvailableCheckoutCouponsTest' test
node --test src/test/js/checkout.test.cjs
git diff --check
```

Test bao gồm tỷ lệ đọc DB, 1%, không ship, sau coupon/thưởng, làm tròn xuống; số 0/âm/lẻ, dưới tối thiểu, thiếu dư, vượt giới hạn; preview không ghi, quyền sở hữu, trạng thái và chống lặp; form Admin/Thymeleaf/lịch sử VND; xác nhận không chấp nhận hidden input sai; tổng phân bổ shop; Payment cuối cùng 185.000 ₫; rollback số dư, ledger, coupon usage, order discounts, order/shop order, Payment và trạng thái cart khi lỗi sau khi đã flush Payment; preview không vào trạng thái xác nhận đơn.

Kết quả đã chạy: compile thành công; 48 test Java trong sáu lớp trên đều qua (0 lỗi, 0 bỏ qua); 10 test JavaScript đều qua; `git diff --check` sạch. Chưa chạy SQL migration trên SQL Server hoặc kiểm tra giao diện thủ công trên trình duyệt.

Test tích hợp sử dụng DB H2 bộ nhớ riêng, JPA/repository/coupon/Loyalty/Checkout thật. Kho, báo phí ship, catalog availability và publisher được giả lập. Riêng refresh đồ thị catalog có adapter dùng khóa DB thật thay vì refresh để tránh lỗi vòng tham chiếu của Hibernate trong fixture H2; không thay code catalog của ứng dụng. Test này không xác minh SQL Server locking, trigger, migration hay đồng thời nhiều request trên SQL Server. Không chạy test `contextLoads` dùng cấu hình DB của ứng dụng.

## Kiểm tra thủ công sau migration trên môi trường thử

1. Admin: lưu `1.00%`, mức tối thiểu `1000`, giới hạn `20%`; mở lại kiểm tra giá trị. Thử tỷ lệ 2,50%, bật/tắt từng chức năng; từ chối tỷ lệ quá 100%, mức tiền âm/lẻ.
2. Khách hàng: số dư, tổng tích/dùng và lịch sử hiển thị VND như `1.500 ₫`; giao dịch sử dụng âm, ghi chú được escape, phân trang và link đơn đúng chủ tài khoản.
3. Checkout: áp coupon rồi nhập tiền thưởng, bấm Áp dụng. Số dư thực không đổi; tổng thanh toán và số dư dự kiến đổi. Nút xác nhận không chuyển thành “Đang ghi nhận đơn…” khi chỉ preview. Thử 0, âm, lẻ, thiếu dư, dưới tối thiểu và vượt %.
4. Thay coupon hoặc địa chỉ: tiền thưởng đang áp dụng được tính lại, hidden input và URL đổi địa chỉ giữ đúng giá trị đã áp dụng. Nếu vượt giới hạn mới, phải báo lỗi và chặn xác nhận.
5. Với ví dụ 200.000/20.000/10.000/15.000: xác nhận ra Payment 185.000 ₫; kiểm tra tổng giảm và tổng tiền các shop khớp Order. Gửi lại cùng checkout token không trừ thêm.
6. Qua luồng Order/Payment hợp lệ, khi đơn tổng COMPLETED và PAID, tích hợp gọi `earnReward(orderId)` và kiểm tra cộng 1.700 ₫ đúng một lần. Chưa đủ hai trạng thái thì không cộng. Earn chưa được tự nối vào Delivery trong thay đổi này.
7. Giả lập lỗi cuối Checkout và hai yêu cầu cùng dùng số dư trên SQL Server thử: không để lại đơn/coupon usage/thưởng bị trừ một phần, không âm số dư. Kiểm tra đơn có ưu đãi vẫn bị chặn tự hủy để đối soát theo bảo vệ cũ.

Giữ nguyên các thay đổi local không liên quan và `application.properties`; không commit/push.
