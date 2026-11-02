from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from pathlib import Path
out=Path('artifacts/tv3-overview')
doc=Document()
sec=doc.sections[0]
sec.top_margin=sec.bottom_margin=Inches(.75)
sec.left_margin=sec.right_margin=Inches(.8)
for name in ['Normal','Title','Heading 1','Heading 2']:
 s=doc.styles[name]; s.font.name='Arial'; s.font.color.rgb=RGBColor(0,0,0)
doc.styles['Normal'].font.size=Pt(10.5)
doc.styles['Normal'].paragraph_format.space_after=Pt(6)
doc.styles['Normal'].paragraph_format.line_spacing=1.12
for name,size in [('Title',23),('Heading 1',15),('Heading 2',12)]: doc.styles[name].font.size=Pt(size)
def p(t): doc.add_paragraph(t)
def h(t): doc.add_heading(t,1)
def sub(t): doc.add_heading(t,2)
def flow(t):
 x=doc.add_paragraph(); x.paragraph_format.left_indent=Inches(.15)
 r=x.add_run(t); r.font.name='Consolas'; r.font.size=Pt(9)
def table(headers,rows):
 t=doc.add_table(rows=1,cols=len(headers)); t.style='Light Shading Accent 1'
 for c,v in zip(t.rows[0].cells,headers): c.text=v
 for row in rows:
  cells=t.add_row().cells
  for c,v in zip(cells,row): c.text=v
 for row in t.rows:
  for c in row.cells:
   for para in c.paragraphs:
    for r in para.runs: r.font.size=Pt(9)
  trpr=row._tr.get_or_add_trPr(); trpr.append(OxmlElement('w:cantSplit'))
 t.rows[0]._tr.get_or_add_trPr().append(OxmlElement('w:tblHeader'))
doc.add_heading('Tổng quan luồng code TV3 sau merge',0)
p('VietFreshHub • Tài liệu học luồng khách mua hàng • 02 tháng 11 năm 2026')
p('Sau merge, TV3 ngoài COD còn có VNPAY. Luồng đơn hàng hiện có hai cách nhìn: đơn tổng của một lượt đặt và đơn riêng của từng shop. Đây là hai điểm cần hiểu trước khi đọc tiếp.')
p('Tài liệu trình bày bản đồ tổng quan dựa trên cấu trúc, ghi chú merge và các điểm điều phối chính trong code hiện tại; chưa phải kết luận kiểm tra đúng sai từng dòng của toàn bộ TV3. Phạm vi bao gồm các thay đổi chưa commit trong workspace tại thời điểm trao đổi.')
h('1 TV3 giải quyết bài toán gì')
p('Theo mạch khách mua hàng đang theo dõi, TV3 giải quyết hành trình sau:')
flow('Tìm sản phẩm có thể mua\n  → Chọn biến thể và số lượng\n  → Đưa vào giỏ\n  → Chọn món cần đặt, địa chỉ và phương thức thanh toán\n  → Tính phí → kiểm tra lại → tạo đơn → giữ kho\n  → COD hoặc thanh toán qua VNPAY\n  → Xem đơn, theo dõi, hủy khi được phép, mua lại\n  → Xem lịch sử thanh toán và nhánh hoàn tiền liên quan')
p('TV3 phải trả lời ba câu hỏi xuyên suốt:')
for t in ['Khách đang muốn mua gì? Sản phẩm và giỏ hàng.','Hệ thống có thể nhận yêu cầu mua này không? Điều kiện bán, tồn kho, địa chỉ, vận chuyển và thanh toán.','Sau khi nhận đơn, khách quản lý giao dịch thế nào? Lịch sử, trạng thái, theo dõi, hủy và mua lại.']: doc.add_paragraph(t,style='List Bullet')
p('Đây là phạm vi kỹ thuật của luồng cần học. Quyền sở hữu chính xác từng class giữa các thành viên cần đối chiếu bảng phân công; cùng một package có thể chứa cả code khách hàng và code người bán.')
h('2 Bản đồ các package theo hành trình')
table(['Chặng','Package chính','Trách nhiệm'],[
('Khám phá sản phẩm','catalog','Danh sách, chi tiết, danh mục, điều kiện hiển thị và khả năng mua.'),
('Gợi ý sản phẩm','recommendation','Bổ sung sản phẩm gợi ý trong trải nghiệm mua sắm.'),
('Chuẩn bị mua','cart','Lưu món, số lượng, cập nhật, xóa và hiển thị tình trạng giỏ.'),
('Xác nhận mua','checkout','Báo phí, lưu bản nháp, kiểm tra lại, tạo đơn và giữ kho.'),
('Thu và ghi nhận tiền','payment; payment.vnpay','COD, khởi tạo VNPAY, nhận kết quả, thử lại và đối soát.'),
('Quản lý sau đặt','Phần khách hàng trong order','Lịch sử, chi tiết, theo dõi, hủy và mua lại.'),
('Nhánh hoàn tiền','refund; phần liên quan trong order','Yêu cầu và xử lý thông tin hoàn tiền.'),
('Dữ liệu hỗ trợ','auth; customer; shop; vietmap','Danh tính khách, địa chỉ nhận, điểm lấy hàng và khoảng cách.'),
('Kết nối vận hành','delivery; notification','Tiến trình giao hàng và thông báo nghiệp vụ.')])
p('Không đọc mỗi package như một hệ thống riêng. Ví dụ, cart đọc tồn kho qua repository nằm trong checkout; checkout tạo entity thuộc order và payment.')
h('3 Một lần đặt có thể sinh nhiều đơn shop')
p('Giả sử khách chọn hai món của shop A và một món của shop B. Khi xác nhận, hệ thống tổ chức dữ liệu như sau:')
flow('Order — lượt đặt tổng của khách\n  ShopOrder A — phần việc của shop A\n    OrderItem 1\n    OrderItem 2\n  ShopOrder B — phần việc của shop B\n    OrderItem 3\n\nOrder còn gắn với địa chỉ được chụp lại lúc đặt và các Payment.')
table(['Đối tượng','Ý nghĩa'],[('Order','Gom toàn bộ một lượt checkout, tổng tiền và các đơn shop.'),('ShopOrder','Phần đơn của một shop, có tiến trình xử lý riêng.'),('OrderItem','Một dòng sản phẩm hoặc biến thể và số lượng trong đơn shop.')])
p('Shop A và B xử lý độc lập. Trạng thái của một đơn shop không đủ để mô tả toàn bộ lượt đặt. Vì vậy code có phần tổng hợp trạng thái và nhiều DTO, query dành cho lịch sử.')
h('4 Hai đường dẫn đơn hàng sau merge')
table(['Đường dẫn','Cách nhìn','Class đầu mối'],[('/customer/orders','Đơn theo từng shop','ShopCustomerOrderController, ShopCustomerOrderServiceImpl và các class query tương ứng.'),('/customer/order-receipts','Toàn bộ một lượt đặt','CustomerOrderController, CustomerOrderServiceImpl và các class query tương ứng.')])
p('Với một lượt checkout gồm shop A và B, order-receipts cho phép nhìn toàn bộ lượt đặt A + B; orders cho phép nhìn và thao tác trên đơn của từng shop.')
p('Không mặc định ID trong hai luồng là cùng loại. Khi đọc một method, cần xác định ngay nó nhận orderId hay shopOrderId.')
p('Luồng hủy hiện đi theo đơn shop, trước khi shop xác nhận, đồng thời còn phải đáp ứng điều kiện thanh toán và đối soát. Không tiếp tục dùng cách hiểu cũ rằng khách luôn hủy toàn bộ đơn tổng.')
h('5 Mạch chạy chính từ đầu đến cuối')
sub('Chặng A Xem sản phẩm')
p('CatalogServiceImpl trả lời hai câu hỏi: sản phẩm có đủ điều kiện xuất hiện trong catalog không, và biến thể có đủ điều kiện để khách mua không. Điều kiện mua liên quan trạng thái sản phẩm, biến thể, danh mục và shop. Kiểm tra số lượng tồn được thực hiện riêng.')
p('Sau merge, sản phẩm đang hoạt động có thể được bán khi chờ hậu kiểm, miễn không bị chặn bởi moderationBlocked và vẫn đáp ứng các điều kiện khác.')
p('Nhìn thấy sản phẩm → được phép mua → đủ số lượng để đặt là ba bước kiểm tra có liên quan nhưng không đồng nghĩa.')
sub('Chặng B Thao tác giỏ')
flow('Request thêm hoặc sửa món\n  → Xác định khách\n  → Tìm biến thể\n  → Kiểm tra điều kiện mua và tồn\n  → Lưu Cart hoặc CartItem\n  → Trả dữ liệu hiển thị')
p('Thêm vào giỏ chưa giữ kho. Người khác vẫn có thể đặt trước khi khách xác nhận, nên checkout bắt buộc kiểm tra lại.')
sub('Chặng C Mở checkout')
flow('CheckoutController.show()\n  → CheckoutServiceImpl.preview()\n  → CheckoutShippingQuoteService.quote()\n  → CheckoutDraftStore.issue()\n  → Hiển thị trang checkout')
table(['Thành phần','Việc thực hiện'],[('Controller','Nhận lựa chọn của khách và chuẩn bị trang.'),('preview()','Lấy các món được chọn, địa chỉ và phương thức thanh toán khả dụng.'),('quote()','Tính khoảng cách, phạm vi giao và phí từng shop.'),('Draft store','Lưu dữ liệu đã báo cho khách trong session.')])
p('Ba loại dữ liệu cần phân biệt: CheckoutPreview dùng để hiển thị; CheckoutDraft là bản nháp server giữ để đối chiếu; CheckoutRequest là form khách gửi khi xác nhận.')
sub('Chặng D Xác nhận checkout')
p('CheckoutServiceImpl.confirm() là điểm nối quan trọng nhất của toàn bộ mạch:')
flow('Kiểm tra token và khách\n  → Tìm đơn đã có để chống tạo trùng\n  → Kiểm tra lại giỏ, sản phẩm, địa chỉ và phương thức thanh toán\n  → Kiểm tra báo phí\n  → Tạo Order / ShopOrder / OrderItem\n  → Giữ hàng theo lô\n  → Tạo Payment\n  → Cập nhật giỏ\n  → Phát sự kiện đặt hàng')
p('CheckoutOrderService ghi cấu trúc đơn và payment. InventoryReservationService giữ hàng từ các lô hợp lệ, ưu tiên lô hết hạn sớm.')
p('Các thao tác ghi phối hợp trong transaction đặt hàng. Khi một bước phát sinh lỗi làm rollback, hệ thống hoàn tác các thay đổi thuộc transaction đó.')
sub('Chặng E Tách nhánh thanh toán')
p('Giải thích cũ rằng checkout chỉ hỗ trợ COD đã không còn đúng.')
flow('Đơn được tạo\n  COD\n    → Ghi nhận khoản cần thu khi nhận hàng\n    → Chuyển đến trang xác nhận\n  BANK_TRANSFER với provider VNPAY\n    → Tạo yêu cầu thanh toán\n    → Chuyển trình duyệt sang VNPAY\n    → Nhận và kiểm tra kết quả trả về\n    → Cập nhật trạng thái thanh toán')
p('Nhóm payment.vnpay còn xử lý thử lại và đối soát yêu cầu quá hạn. Có đơn hàng không có nghĩa là đã thanh toán; chưa nhận được kết quả cũng không đủ để kết luận tiền chưa được thu. Vì vậy luồng online cần nhiều trạng thái và kiểm tra hơn COD.')
sub('Chặng F Sau khi đặt')
table(['Loại tác vụ','Ví dụ'],[('Đọc thông tin','Lịch sử, chi tiết, tổng hợp trạng thái và timeline giao hàng.'),('Thay đổi nghiệp vụ','Hủy đơn shop và mua lại.')])
p('Mua lại đưa những món còn hợp lệ về giỏ theo điều kiện hiện tại. Nó không sao chép đơn cũ thành một đơn mới ngay lập tức.')
p('Hủy đơn phải phối hợp trạng thái shop, giao hàng, thanh toán và giải phóng hàng đã giữ. Không thể hiểu thao tác này chỉ là setStatus("CANCELLED").')
h('6 Cách đọc từng tầng mà không bị lạc')
flow('Template hoặc JavaScript\n  → Controller\n  → Service\n  → Repository hoặc EntityManager\n  → Entity và bảng dữ liệu')
table(['Thành phần','Câu hỏi cần trả lời khi đọc'],[('Template hoặc JS','Khách bấm gì, gửi field nào, đến URL nào?'),('Controller','Nhận request nào, lấy khách ở đâu, gọi service nào, trả trang gì?'),('Service','Quy tắc nghiệp vụ nào được kiểm tra, dữ liệu nào bị thay đổi?'),('Repository hoặc EntityManager','Đọc hoặc ghi gì, có kiểm tra quyền sở hữu hay khóa dữ liệu không?'),('Entity','Bản ghi đại diện cho điều gì, liên kết với ai?'),('DTO','Dữ liệu đến từ khách, dùng hiển thị hay lưu đối chiếu?'),('Test','Code đang cam kết xử lý những tình huống nào?')])
p('Một cập nhật khác sau merge: OrderNotificationService.onOrderPlaced() hiện dùng @EventListener và tham gia transaction đang có. Không suy luận mọi consumer của OrderPlacedEvent đều chạy sau commit từ phần giải thích trước.')
p('Với mạch đã học cart, bước tiếp theo là theo một request mở checkout từ CheckoutController.show(). Đọc từng đoạn bằng bốn câu hỏi: đầu vào là gì, xử lý gì, gọi ai, đầu ra đi đâu; sau đó chuyển sang request xác nhận đặt hàng.')
doc.save(out/'Tong_quan_luong_code_TV3.docx')
print((out/'Tong_quan_luong_code_TV3.docx').resolve())
