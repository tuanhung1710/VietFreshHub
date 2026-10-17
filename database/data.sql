
USE [VietFresh_Hub];
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME()<>N'VietFresh_Hub' THROW 58000,N'Sai database demo.',1;
IF (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0)<>60
 OR OBJECT_ID('dbo.tr_order_items_shop_match','TR') IS NULL
 OR COL_LENGTH('dbo.users','username') IS NULL
 OR NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_shop_members_one_active_per_shop')
 THROW 58001,N'Can schema moi day du rang buoc da shop.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock INT;
 EXEC @lock=sys.sp_getapplock @Resource=N'VietFreshHub.Demo.20261017',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock<0 THROW 58002,N'Khong lay duoc khoa nap demo.',1;
 IF EXISTS(SELECT 1 FROM dbo.users) OR EXISTS(SELECT 1 FROM dbo.shops)
  OR EXISTS(SELECT 1 FROM dbo.categories) OR EXISTS(SELECT 1 FROM dbo.roles)
  THROW 58003,N'Demo chi nap vao database rong, khong ghi de du lieu.',1;
 DECLARE @now DATETIME2(0)=SYSUTCDATETIME(),@today DATE=CONVERT(DATE,DATEADD(HOUR,7,SYSUTCDATETIME()));
 DECLARE @created DATETIME2(0)=DATEADD(DAY,-45,@now);
 DECLARE @password NVARCHAR(500)=N'$2a$10$jcSfgNwin1KVJzHUwja3weKxynN6BG0qynEEZjHu2N/vygDkyJtJO';
 DECLARE @userPlan TABLE(n INT PRIMARY KEY,email NVARCHAR(255),username VARCHAR(64),full_name NVARCHAR(150),role_name VARCHAR(50),phone NVARCHAR(30),id BIGINT);
 DECLARE @shopPlan TABLE(n INT PRIMARY KEY,name NVARCHAR(200),owner_n INT,description NVARCHAR(1000),phone NVARCHAR(30),email NVARCHAR(255),address_line NVARCHAR(500),ward NVARCHAR(100),latitude DECIMAL(9,6),longitude DECIMAL(10,6),logo NVARCHAR(1000),banner NVARCHAR(1000),id BIGINT);
 DECLARE @addressPlan TABLE(user_n INT,address_line NVARCHAR(500),ward NVARCHAR(100),latitude DECIMAL(9,6),longitude DECIMAL(10,6));
 DECLARE @productPlan TABLE(n INT PRIMARY KEY,shop_n INT,name NVARCHAR(250),slug VARCHAR(300),image NVARCHAR(1000),description NVARCHAR(MAX),category VARCHAR(20),scenario VARCHAR(20),status VARCHAR(20),approval VARCHAR(20),warning INT,prep BIT,markdown INT,id BIGINT);
 DECLARE @variantPlan TABLE(n INT PRIMARY KEY,product_n INT,pack INT,name NVARCHAR(200),unit NVARCHAR(30),weight DECIMAL(18,3),price DECIMAL(18,2),compare_price DECIMAL(18,2),low_stock INT,status VARCHAR(20),id BIGINT);
 DECLARE @orderPlan TABLE(n INT PRIMARY KEY,shop_n INT,customer_n INT,status VARCHAR(30),minutes_ago INT,method VARCHAR(50),shipping DECIMAL(18,2),id BIGINT,shop_order_id BIGINT,placed DATETIME2(0));
 DECLARE @linePlan TABLE(n INT PRIMARY KEY,order_n INT,variant_n INT,quantity INT,price DECIMAL(18,2),id BIGINT);
 DECLARE @batchPlan TABLE(n INT PRIMARY KEY,variant_n INT,lot INT,expiry_days INT,received_days INT,initial_qty INT,physical_qty INT,reserved_qty INT,dispose_qty INT,status VARCHAR(20),id BIGINT);
 DECLARE @allocationPlan TABLE(line_n INT,batch_n INT,quantity INT,status VARCHAR(20),PRIMARY KEY(line_n,batch_n));
 INSERT @userPlan(n,email,username,full_name,role_name,phone) VALUES
(1,N'admin@vietfreshhub.vn',N'admin',N'Nguyễn Hoàng Minh',N'ROLE_ADMIN',N'0906784312'),
(2,N'hoangvanson@gmail.com',N'vuonnhason',N'Hoàng Văn Sơn',N'ROLE_STORE_MANAGER',N'0918372645'),
(3,N'khonghung@gmail.com',N'traicaykhonghung',N'Khổng Hưng',N'ROLE_STORE_MANAGER',N'0935481762'),
(4,N'phamphuoc@gmail.com',N'nongsanphamphuoc',N'Phạm Phước',N'ROLE_STORE_MANAGER',N'0986714352'),
(5,N'quochuy.pham.ship@gmail.com',N'delivery.000001',N'Phạm Quốc Huy',N'ROLE_DELIVERY_STAFF',N'0932851467'),
(6,N'ngochavo.ship@gmail.com',N'delivery.000002',N'Võ Ngọc Hà',N'ROLE_DELIVERY_STAFF',N'0905361874'),
(7,N'thanhdat.dang.ship@gmail.com',N'delivery.000003',N'Đặng Thành Đạt',N'ROLE_DELIVERY_STAFF',N'0984672351'),
(8,N'thumai.nguyen91@gmail.com',N'customer.000001',N'Nguyễn Thu Mai',N'ROLE_CUSTOMER',N'0903247816'),
(9,N'quocbao.tran88@gmail.com',N'customer.000002',N'Trần Quốc Bảo',N'ROLE_CUSTOMER',N'0987253641'),
(10,N'minhtuan.le93@gmail.com',N'customer.000003',N'Lê Minh Tuấn',N'ROLE_CUSTOMER',N'0936185724'),
(11,N'ngocanh.pham95@gmail.com',N'customer.000004',N'Phạm Ngọc Anh',N'ROLE_CUSTOMER',N'0917534862'),
(12,N'thuyduong.vo90@gmail.com',N'customer.000005',N'Võ Thùy Dương',N'ROLE_CUSTOMER',N'0974826135'),
(13,N'giahan.dang97@gmail.com',N'customer.000006',N'Đặng Gia Hân',N'ROLE_CUSTOMER',N'0869351742'),
(14,N'nhatminh.hoang89@gmail.com',N'customer.000007',N'Hoàng Nhật Minh',N'ROLE_CUSTOMER',N'0908126437'),
(15,N'thanhtruc.bui92@gmail.com',N'customer.000008',N'Bùi Thanh Trúc',N'ROLE_CUSTOMER',N'0937642815'),
(16,N'quanghuy.do94@gmail.com',N'customer.000009',N'Đỗ Quang Huy',N'ROLE_CUSTOMER',N'0914268357'),
(17,N'phuongthao.ngo96@gmail.com',N'customer.000010',N'Ngô Phương Thảo',N'ROLE_CUSTOMER',N'0981635724'),
(18,N'duclong.phan87@gmail.com',N'customer.000011',N'Phan Đức Long',N'ROLE_CUSTOMER',N'0876452139'),
(19,N'khanhvy.huynh98@gmail.com',N'customer.000012',N'Huỳnh Khánh Vy',N'ROLE_CUSTOMER',N'0963527481');
INSERT @shopPlan(n,name,owner_n,description,phone,email,address_line,ward,latitude,longitude,logo,banner) VALUES
(1,N'Vườn Nhà Sơn',2,N'Trái cây miền Tây theo mùa, nhận hàng mỗi sáng tại kho Tân Phú. Chọn từng đợt chín, đóng túi thoáng và giao theo lịch khách đặt.',N'0918372645',N'hoangvanson@gmail.com',N'126 Lũy Bán Bích',N'Phường Tân Phú',10.7852,106.6397,N'/images/shops/son-logo.svg',N'/images/shops/son-banner.svg'),
(2,N'Trái Cây Khổng Hưng',3,N'Trái cây tuyển chọn, quy cách hộp nhỏ và thùng quà. Giữ lạnh theo từng nhóm hàng, hỗ trợ chọn độ chín và gói quà khi đặt trước.',N'0935481762',N'khonghung@gmail.com',N'38 Xuân Thủy',N'Phường An Khánh',10.8031,106.7326,N'/images/shops/hung-logo.svg',N'/images/shops/hung-banner.svg'),
(3,N'Nông Sản Phạm Phước',4,N'Cửa hàng nông sản gia đình đang chuẩn bị danh mục hàng đầu tiên. Hồ sơ người bán đã được duyệt.',N'0986714352',N'phamphuoc@gmail.com',N'74 Phạm Văn Bạch',N'Phường Tân Sơn',10.8193,106.6372,N'/images/shops/phuoc-logo.svg',N'/images/shops/phuoc-banner.svg');
INSERT @addressPlan(user_n,address_line,ward,latitude,longitude) VALUES
(8,N'41 Nguyễn Trãi',N'Phường Bến Thành',10.7645,106.6643),
(9,N'208 Võ Văn Tần',N'Phường Bàn Cờ',10.7727,106.676),
(10,N'96 Điện Biên Phủ',N'Phường Gia Định',10.7809,106.68769999999999),
(11,N'17 Nguyễn Đình Chiểu',N'Phường Xuân Hòa',10.7891,106.6994),
(12,N'62 Hoàng Hoa Thám',N'Phường Bình Thạnh',10.7973,106.6643),
(13,N'135 Lê Văn Sỹ',N'Phường Nhiêu Lộc',10.7645,106.676),
(14,N'29 Trần Hưng Đạo',N'Phường Cầu Ông Lãnh',10.7727,106.68769999999999),
(15,N'84 Cao Thắng',N'Phường Bàn Cờ',10.7809,106.6994),
(16,N'55 Nguyễn Văn Trỗi',N'Phường Phú Nhuận',10.7891,106.6643),
(17,N'103 Phan Xích Long',N'Phường Cầu Kiệu',10.7973,106.676),
(18,N'37 Nguyễn Thị Minh Khai',N'Phường Sài Gòn',10.7645,106.68769999999999),
(19,N'146 Lê Quang Định',N'Phường Bình Lợi Trung',10.7727,106.6994);
INSERT @productPlan(n,shop_n,name,slug,image,description,category,scenario,status,approval,warning,prep,markdown) VALUES
(1,1,N'Xoài cát Hòa Lộc',N'xoai-cat-hoa-loc',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/Mangos_-_single_and_halved.jpg/960px-Mangos_-_single_and_halved.jpg',N'Xoài chín vừa, vỏ vàng nhạt và thịt ít xơ. Chọn quả còn cuống, đóng túi lưới; để nơi thoáng, khi chín chuyển ngăn mát.',N'LOCAL',N'healthy',N'ACTIVE',N'APPROVED',2,0,0),
(2,1,N'Chôm chôm nhãn',N'chom-chom-nhan',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bb/Rambutans_with_seed.jpg/960px-Rambutans_with_seed.jpg',N'Chôm chôm nguyên chùm, cùi giòn và vị ngọt nhẹ. Đóng túi có lỗ thoáng, bảo quản ngăn mát và rửa trước khi ăn.',N'LOCAL',N'near',N'ACTIVE',N'APPROVED',2,0,5000),
(3,1,N'Bưởi Năm Roi Vĩnh Long',N'buoi-nam-roi-vinh-long',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f3/Pomelo_fruit.jpg/960px-Pomelo_fruit.jpg',N'Bưởi nguyên quả, chọn cỡ khoảng 1,2–1,5 kg. Giao quả chưa bóc để giữ tép mọng; giá theo quy cách đã chọn.',N'LOCAL',N'healthy',N'ACTIVE',N'APPROVED',2,0,0),
(4,1,N'Cam sành Vĩnh Long',N'cam-sanh-vinh-long',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e3/Oranges_-_whole-halved-segment.jpg/960px-Oranges_-_whole-halved-segment.jpg',N'Cam vỏ xanh, nhiều nước, phù hợp ép hoặc ăn múi. Phân cỡ trước khi đóng túi; giữ ngăn mát, không ngâm nước trước khi cất.',N'LOCAL',N'healthy',N'ACTIVE',N'APPROVED',2,0,0),
(5,1,N'Ổi nữ hoàng',N'oi-nu-hoang',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/84/Goiaba_vermelha.jpg/960px-Goiaba_vermelha.jpg',N'Ổi nguyên quả, ruột chắc, độ chín vừa để ăn giòn. Đóng túi giấy có lót, rửa sạch và cắt bỏ cuống trước khi dùng.',N'LOCAL',N'low',N'ACTIVE',N'APPROVED',2,0,0),
(6,1,N'Dừa xiêm Bến Tre',N'dua-xiem-ben-tre',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f1/Coconuts_-_single_and_cracked_open.jpg/960px-Coconuts_-_single_and_cracked_open.jpg',N'Dừa xiêm nguyên quả, nước ngọt thanh. Đơn đặt gọt vỏ được xử lý khi chuẩn bị giao; phần đã mở nên dùng trong ngày.',N'LOCAL',N'healthy',N'ACTIVE',N'APPROVED',2,1,0),
(7,1,N'Nhãn xuồng cơm vàng',N'nhan-xuong-com-vang',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2d/Dimocarpus_longan_fruits.jpg/960px-Dimocarpus_longan_fruits.jpg',N'Nhãn nguyên chùm được lựa bỏ quả dập trước khi đóng túi. Cùi dày, thích hợp ăn tươi; bảo quản mát và dùng sớm.',N'LOCAL',N'hidden',N'INACTIVE',N'APPROVED',2,0,0),
(8,1,N'Dưa hấu Long An',N'dua-hau-long-an',N'https://upload.wikimedia.org/wikipedia/commons/4/4c/Vampire_watermelon.jpg',N'Dưa hấu nguyên trái, chọn vỏ lành và cuống khô. Quy cách theo khoảng trọng lượng, phần đã cắt cần giữ lạnh và dùng sớm.',N'LOCAL',N'soldout',N'ACTIVE',N'APPROVED',2,0,0),
(9,1,N'Sầu riêng Ri6 tách múi',N'sau-rieng-ri6-tach-mui',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/02/Durian.jpg/960px-Durian.jpg',N'Múi sầu riêng được tách và đóng hộp khi chuẩn bị đơn. Không thêm đường; giữ lạnh 2–5°C và dùng trong ngày sau khi nhận.',N'CUT',N'violation',N'ACTIVE',N'REJECTED',2,1,0),
(10,1,N'Chuối Laba Đà Lạt',N'chuoi-laba-da-lat',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/40/Bananas_on_countertop.JPG/960px-Bananas_on_countertop.JPG',N'Chuối bán theo nải, chọn độ chín theo nhu cầu ăn ngay hoặc để chín tiếp. Để nơi khô thoáng, tránh đè lên quả.',N'LOCAL',N'pending',N'ACTIVE',N'PENDING',2,0,0),
(11,1,N'Mít Thái tách múi',N'mit-thai-tach-mui',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/54/Jackfruit_NS-1_01_Asit.jpg/960px-Jackfruit_NS-1_01_Asit.jpg',N'Mít tách múi, bỏ xơ và đóng hộp kín. Múi có độ chín vừa, giữ ngăn mát; mở hộp nên dùng ngay trong ngày.',N'CUT',N'archived',N'DELETED',N'APPROVED',2,1,0),
(12,1,N'Thanh long ruột trắng Bình Thuận',N'thanh-long-ruot-trang-binh-thuan',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/35/Dragon_fruit_.jpg/960px-Dragon_fruit_.jpg',N'Thanh long nguyên quả, tai xanh và vỏ không dập. Phân loại quả trước khi đóng túi, phù hợp ăn tươi hoặc làm nước ép.',N'LOCAL',N'held',N'ACTIVE',N'APPROVED',2,0,0),
(13,2,N'Táo Gala New Zealand',N'tao-gala-new-zealand',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/15/Red_Apple.jpg/960px-Red_Apple.jpg',N'Táo Gala cỡ vừa, thịt giòn, vị ngọt dịu. Đóng khay có đệm từng quả, bảo quản 2–5°C; rửa dưới vòi nước trước khi ăn.',N'IMPORTED',N'healthy',N'ACTIVE',N'APPROVED',4,0,0),
(14,2,N'Kiwi xanh New Zealand',N'kiwi-xanh-new-zealand',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b8/Kiwi_%28Actinidia_chinensis%29_1_Luc_Viatour.jpg/960px-Kiwi_%28Actinidia_chinensis%29_1_Luc_Viatour.jpg',N'Kiwi xanh vỏ nâu, vị chua ngọt. Quả còn chắc có thể để chín tự nhiên; khi mềm chuyển ngăn mát và dùng sớm.',N'IMPORTED',N'near',N'ACTIVE',N'APPROVED',4,0,12000),
(15,2,N'Việt quất Peru',N'viet-quat-peru',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7d/Dish_of_blueberries.jpg/960px-Dish_of_blueberries.jpg',N'Việt quất nguyên hộp 125 g, giữ lớp phấn tự nhiên trên vỏ. Bảo quản lạnh, chỉ rửa lượng dùng ngay và không ngâm lâu.',N'IMPORTED',N'expired',N'ACTIVE',N'APPROVED',4,0,0),
(16,2,N'Cherry đỏ Úc',N'cherry-do-uc',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/40/Bing_Cherries_%28USDA_ARS%29.jpg/960px-Bing_Cherries_%28USDA_ARS%29.jpg',N'Cherry đỏ nguyên cuống, thịt chắc, đóng hộp có lót giấy. Bảo quản 2–5°C, rửa trước khi dùng và bỏ hạt khi cho trẻ nhỏ.',N'IMPORTED',N'healthy',N'ACTIVE',N'APPROVED',4,0,0),
(17,2,N'Nho xanh Ninh Thuận',N'nho-xanh-ninh-thuan',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d9/Grapes%2C_Dry_Creek_Valley-7705.jpg/960px-Grapes%2C_Dry_Creek_Valley-7705.jpg',N'Nho nguyên chùm được chọn quả lành, đóng khay hạn chế xô lệch. Giữ lạnh và chỉ tách, rửa trước khi ăn.',N'LOCAL',N'healthy',N'ACTIVE',N'APPROVED',4,0,0),
(18,2,N'Lê Hàn Quốc',N'le-han-quoc',N'https://upload.wikimedia.org/wikipedia/commons/9/92/Blake%27s_Pride_pears.jpg',N'Lê vỏ vàng, nhiều nước, đóng lưới xốp từng quả. Cỡ quả khoảng 400–500 g; giữ ngăn mát và tránh chèn vật nặng lên hộp.',N'IMPORTED',N'pending',N'ACTIVE',N'PENDING',4,0,0),
(19,2,N'Lựu đỏ Peru',N'luu-do-peru',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fa/Pomegranate_fruit_-_whole_and_piece_with_arils.jpg/960px-Pomegranate_fruit_-_whole_and_piece_with_arils.jpg',N'Lựu nguyên quả, vỏ đỏ lành, phù hợp tách hạt ăn tươi hoặc ép nước. Để mát, phần đã tách giữ trong hộp kín và dùng sớm.',N'IMPORTED',N'healthy',N'ACTIVE',N'APPROVED',4,0,0),
(20,2,N'Đào tiên',N'dao-tien',N'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9e/Autumn_Red_peaches.jpg/960px-Autumn_Red_peaches.jpg',N'Đào nguyên quả, hương thơm nhẹ, đóng khay có đệm. Chọn quả mềm vừa để dùng; phần còn chắc có thể để chín tiếp ở nơi thoáng.',N'IMPORTED',N'hidden',N'INACTIVE',N'APPROVED',4,0,0);
INSERT @variantPlan(n,product_n,pack,name,unit,weight,price,compare_price,low_stock,status) VALUES
(1,1,1,N'Túi 1 kg',N'kg',1,59000,67000,3,N'ACTIVE'),
(2,1,2,N'Thùng gia đình 3 kg',N'thùng',3,168000,189000,3,N'ACTIVE'),
(3,2,1,N'Túi 500 g',N'túi',0.5,29000,33000,4,N'ACTIVE'),
(4,3,1,N'Quả cỡ 1,2 kg',N'quả',1.2,52000,NULL,5,N'ACTIVE'),
(5,3,2,N'Cặp hai quả',N'cặp',2.4,99000,NULL,5,N'ACTIVE'),
(6,4,1,N'Túi 1 kg',N'kg',1,32000,36000,6,N'ACTIVE'),
(7,4,2,N'Túi ép nước 2 kg',N'túi',2,58000,65000,6,N'ACTIVE'),
(8,5,1,N'Túi 1 kg',N'kg',1,35000,40000,2,N'ACTIVE'),
(9,6,1,N'Một quả gọt vỏ',N'quả',0.9,24000,NULL,3,N'ACTIVE'),
(10,7,1,N'Túi 500 g',N'túi',0.5,46000,52000,4,N'ACTIVE'),
(11,8,1,N'Trái cỡ 2 kg',N'trái',2,48000,54000,5,N'OUT_OF_STOCK'),
(12,8,2,N'Trái cỡ 3 kg',N'trái',3,69000,78000,5,N'OUT_OF_STOCK'),
(13,9,1,N'Hộp múi 400 g',N'hộp',0.4,149000,NULL,6,N'ACTIVE'),
(14,10,1,N'Nải cỡ 700 g',N'nải',0.7,23000,26000,2,N'ACTIVE'),
(15,10,2,N'Nải cỡ 1 kg',N'nải',1,31000,35000,2,N'ACTIVE'),
(16,10,3,N'Túi chuối chín 1,5 kg',N'túi',1.5,42000,48000,2,N'ACTIVE'),
(17,11,1,N'Hộp múi 500 g',N'hộp',0.5,49000,55000,3,N'DELETED'),
(18,12,1,N'Túi 1 kg',N'kg',1,28000,NULL,4,N'ACTIVE'),
(19,12,2,N'Thùng 5 kg',N'thùng',5,125000,NULL,4,N'ACTIVE'),
(20,13,1,N'Khay 4 quả',N'khay',0.7,89000,100000,5,N'ACTIVE'),
(21,13,2,N'Hộp 1 kg',N'hộp',1,119000,134000,5,N'ACTIVE'),
(22,13,3,N'Thùng 3 kg',N'thùng',3,339000,380000,5,N'ACTIVE'),
(23,14,1,N'Hộp 500 g',N'hộp',0.5,79000,89000,6,N'ACTIVE'),
(24,14,2,N'Hộp tuyển chọn 1 kg',N'hộp',1,149000,167000,6,N'ACTIVE'),
(25,15,1,N'Hộp 125 g',N'hộp',0.125,85000,NULL,2,N'ACTIVE'),
(26,16,1,N'Hộp 500 g',N'hộp',0.5,219000,246000,3,N'ACTIVE'),
(27,16,2,N'Hộp quà 1 kg',N'hộp',1,429000,481000,3,N'INACTIVE'),
(28,17,1,N'Khay 500 g',N'khay',0.5,59000,67000,4,N'ACTIVE'),
(29,17,2,N'Hộp 1 kg',N'hộp',1,109000,123000,4,N'ACTIVE'),
(30,18,1,N'Hộp hai quả',N'hộp',0.9,129000,NULL,5,N'ACTIVE'),
(31,19,1,N'Túi hai quả',N'túi',0.8,139000,156000,6,N'ACTIVE'),
(32,19,2,N'Hộp quà bốn quả',N'hộp',1.6,269000,302000,6,N'ACTIVE'),
(33,20,1,N'Khay 500 g',N'khay',0.5,89000,100000,2,N'ACTIVE');
INSERT @orderPlan(n,shop_n,customer_n,status,minutes_ago,method,shipping) VALUES
(1,1,16,N'COMPLETED',-5760,N'BANK_TRANSFER',18000),
(2,1,18,N'COMPLETED',-6350,N'COD',12000),
(3,1,12,N'COMPLETED',-6940,N'BANK_TRANSFER',18000),
(4,1,14,N'COMPLETED',-7530,N'COD',12000),
(5,1,18,N'COMPLETED',-8120,N'BANK_TRANSFER',12000),
(6,1,14,N'COMPLETED',-8710,N'COD',12000),
(7,1,8,N'COMPLETED',-9300,N'COD',18000),
(8,1,13,N'COMPLETED',-9890,N'COD',21000),
(9,1,10,N'COMPLETED',-10480,N'COD',12000),
(10,1,8,N'COMPLETED',-11070,N'COD',18000),
(11,1,13,N'COMPLETED',-11660,N'COD',21000),
(12,1,14,N'COMPLETED',-12250,N'COD',12000),
(13,1,14,N'COMPLETED',-12840,N'BANK_TRANSFER',12000),
(14,1,12,N'COMPLETED',-13430,N'BANK_TRANSFER',18000),
(15,1,18,N'COMPLETED',-14020,N'COD',12000),
(16,1,8,N'COMPLETED',-14610,N'BANK_TRANSFER',18000),
(17,1,10,N'COMPLETED',-3600,N'BANK_TRANSFER',12000),
(18,1,14,N'COMPLETED',-2520,N'BANK_TRANSFER',12000),
(19,1,10,N'COMPLETED',-1680,N'COD',12000),
(20,1,14,N'CANCELLED',-18720,N'BANK_TRANSFER',12000),
(21,1,18,N'CANCELLED',-18900,N'COD',12000),
(22,1,14,N'CANCELLED',-19080,N'COD',12000),
(23,1,10,N'CANCELLED',-19260,N'COD',12000),
(24,1,14,N'CANCELLED',-19440,N'COD',12000),
(25,1,14,N'PENDING',-35,N'BANK_TRANSFER',12000),
(26,1,12,N'PENDING',-78,N'COD',18000),
(27,1,13,N'PENDING',-121,N'COD',21000),
(28,1,15,N'PENDING',-164,N'COD',15000),
(29,1,19,N'PENDING',-207,N'COD',15000),
(30,1,10,N'PENDING',-250,N'COD',12000),
(31,1,15,N'PENDING',-293,N'COD',15000),
(32,1,18,N'PENDING',-336,N'COD',12000),
(33,1,8,N'PENDING',-379,N'COD',18000),
(34,1,9,N'PENDING',-422,N'COD',21000),
(35,1,15,N'PENDING',-465,N'COD',15000),
(36,1,13,N'PENDING',-508,N'COD',21000),
(37,1,9,N'PENDING',-551,N'COD',21000),
(38,1,19,N'CONFIRMED',-180,N'COD',15000),
(39,1,13,N'CONFIRMED',-219,N'COD',21000),
(40,1,9,N'CONFIRMED',-258,N'COD',21000),
(41,1,9,N'CONFIRMED',-297,N'COD',21000),
(42,1,11,N'CONFIRMED',-336,N'BANK_TRANSFER',15000),
(43,1,14,N'CONFIRMED',-375,N'COD',12000),
(44,1,8,N'CONFIRMED',-414,N'COD',18000),
(45,1,16,N'CONFIRMED',-453,N'BANK_TRANSFER',18000),
(46,1,10,N'CONFIRMED',-492,N'COD',12000),
(47,1,11,N'CONFIRMED',-531,N'COD',15000),
(48,1,12,N'CONFIRMED',-570,N'COD',18000),
(49,1,8,N'CONFIRMED',-609,N'BANK_TRANSFER',18000),
(50,1,19,N'PREPARING',-280,N'COD',15000),
(51,1,8,N'PREPARING',-319,N'COD',18000),
(52,1,14,N'PREPARING',-358,N'BANK_TRANSFER',12000),
(53,1,15,N'PREPARING',-397,N'COD',15000),
(54,1,12,N'PREPARING',-436,N'BANK_TRANSFER',18000),
(55,1,19,N'PREPARING',-475,N'COD',15000),
(56,1,12,N'PREPARING',-514,N'COD',18000),
(57,1,18,N'PREPARING',-553,N'COD',12000),
(58,1,11,N'READY_FOR_DELIVERY',-180,N'COD',15000),
(59,1,19,N'READY_FOR_DELIVERY',-219,N'COD',15000),
(60,1,9,N'READY_FOR_DELIVERY',-258,N'COD',21000),
(61,1,17,N'READY_FOR_DELIVERY',-297,N'COD',21000),
(62,1,11,N'READY_FOR_DELIVERY',-336,N'COD',15000),
(63,1,13,N'READY_FOR_DELIVERY',-375,N'COD',21000),
(64,1,14,N'OUT_FOR_DELIVERY',-280,N'COD',12000),
(65,1,8,N'OUT_FOR_DELIVERY',-319,N'COD',18000),
(66,1,15,N'OUT_FOR_DELIVERY',-358,N'COD',15000),
(67,1,19,N'OUT_FOR_DELIVERY',-397,N'COD',15000),
(68,2,9,N'COMPLETED',-5760,N'BANK_TRANSFER',30000),
(69,2,19,N'COMPLETED',-6870,N'BANK_TRANSFER',22000),
(70,2,17,N'COMPLETED',-7980,N'COD',30000),
(71,2,13,N'COMPLETED',-9090,N'BANK_TRANSFER',30000),
(72,2,19,N'COMPLETED',-10200,N'COD',22000),
(73,2,9,N'COMPLETED',-11310,N'BANK_TRANSFER',30000),
(74,2,14,N'COMPLETED',-12420,N'BANK_TRANSFER',18000),
(75,2,16,N'COMPLETED',-13530,N'COD',26000),
(76,2,18,N'COMPLETED',-3600,N'BANK_TRANSFER',18000),
(77,2,18,N'COMPLETED',-2520,N'BANK_TRANSFER',18000),
(78,2,19,N'COMPLETED',-1680,N'COD',22000),
(79,2,13,N'CANCELLED',-18720,N'COD',30000),
(80,2,13,N'CANCELLED',-18900,N'BANK_TRANSFER',30000),
(81,2,17,N'PENDING',-35,N'BANK_TRANSFER',30000),
(82,2,11,N'PENDING',-78,N'BANK_TRANSFER',22000),
(83,2,11,N'PENDING',-121,N'BANK_TRANSFER',22000),
(84,2,19,N'PENDING',-164,N'COD',22000),
(85,2,13,N'PENDING',-207,N'COD',30000),
(86,2,15,N'CONFIRMED',-180,N'COD',22000),
(87,2,17,N'CONFIRMED',-219,N'BANK_TRANSFER',30000),
(88,2,13,N'CONFIRMED',-258,N'COD',30000),
(89,2,17,N'CONFIRMED',-297,N'BANK_TRANSFER',30000),
(90,2,19,N'PREPARING',-280,N'BANK_TRANSFER',22000),
(91,2,13,N'PREPARING',-319,N'COD',30000),
(92,2,18,N'PREPARING',-358,N'BANK_TRANSFER',18000),
(93,2,8,N'READY_FOR_DELIVERY',-180,N'BANK_TRANSFER',26000),
(94,2,14,N'READY_FOR_DELIVERY',-219,N'BANK_TRANSFER',18000),
(95,2,10,N'READY_FOR_DELIVERY',-258,N'BANK_TRANSFER',18000),
(96,2,16,N'READY_FOR_DELIVERY',-297,N'BANK_TRANSFER',26000),
(97,2,14,N'READY_FOR_DELIVERY',-336,N'COD',18000),
(98,2,8,N'OUT_FOR_DELIVERY',-280,N'BANK_TRANSFER',26000),
(99,2,12,N'OUT_FOR_DELIVERY',-319,N'BANK_TRANSFER',26000),
(100,2,8,N'OUT_FOR_DELIVERY',-358,N'COD',26000);
INSERT @linePlan(n,order_n,variant_n,quantity,price) VALUES
(1,1,1,1,59000),
(2,1,2,2,168000),
(3,2,3,2,29000),
(4,2,4,5,52000),
(5,3,5,1,99000),
(6,3,6,3,32000),
(7,4,7,1,58000),
(8,4,8,1,35000),
(9,4,9,5,24000),
(10,4,10,1,46000),
(11,5,11,1,48000),
(12,5,12,2,69000),
(13,5,13,5,149000),
(14,5,14,2,23000),
(15,6,15,1,31000),
(16,6,16,5,42000),
(17,7,17,4,49000),
(18,7,18,5,28000),
(19,7,19,1,125000),
(20,8,1,1,59000),
(21,8,2,1,168000),
(22,8,3,4,29000),
(23,9,4,3,52000),
(24,9,5,2,99000),
(25,10,6,5,32000),
(26,10,7,2,58000),
(27,10,8,3,35000),
(28,11,9,1,24000),
(29,11,10,5,46000),
(30,11,11,2,48000),
(31,12,12,1,69000),
(32,12,13,5,149000),
(33,12,14,2,23000),
(34,12,15,4,31000),
(35,13,16,5,42000),
(36,13,17,1,49000),
(37,14,18,2,28000),
(38,14,19,2,125000),
(39,15,1,5,59000),
(40,15,2,2,168000),
(41,16,3,2,29000),
(42,16,4,3,52000),
(43,17,7,2,58000),
(44,17,9,2,24000),
(45,18,1,2,59000),
(46,18,18,1,28000),
(47,19,15,1,31000),
(48,19,3,5,24000),
(49,20,5,1,99000),
(50,20,6,1,32000),
(51,20,7,1,58000),
(52,21,8,1,35000),
(53,21,9,1,24000),
(54,21,10,1,46000),
(55,22,11,1,48000),
(56,22,12,1,69000),
(57,22,13,1,149000),
(58,23,14,1,23000),
(59,23,15,1,31000),
(60,23,16,1,42000),
(61,23,17,1,49000),
(62,24,18,1,28000),
(63,24,19,1,125000),
(64,24,1,1,59000),
(65,24,2,1,168000),
(66,25,18,5,28000),
(67,26,15,4,31000),
(68,26,5,2,99000),
(69,26,3,3,24000),
(70,26,8,2,35000),
(71,26,6,3,32000),
(72,27,2,1,168000),
(73,27,7,2,58000),
(74,27,15,2,31000),
(75,27,4,4,52000),
(76,28,16,4,42000),
(77,28,14,2,23000),
(78,28,2,1,168000),
(79,28,4,4,52000),
(80,29,8,3,35000),
(81,29,19,1,125000),
(82,29,15,1,31000),
(83,29,9,2,24000),
(84,30,15,4,31000),
(85,30,5,2,99000),
(86,30,19,1,125000),
(87,31,2,1,168000),
(88,31,6,3,32000),
(89,31,5,2,99000),
(90,31,8,4,35000),
(91,31,1,2,59000),
(92,32,1,5,59000),
(93,33,5,2,99000),
(94,33,7,2,58000),
(95,33,6,2,32000),
(96,34,14,1,23000),
(97,35,14,1,23000),
(98,35,4,4,52000),
(99,35,8,3,35000),
(100,36,19,1,125000),
(101,36,6,3,32000),
(102,36,4,2,52000),
(103,36,9,5,24000),
(104,36,14,3,23000),
(105,37,16,3,42000),
(106,38,8,4,35000),
(107,38,19,1,125000),
(108,38,4,3,52000),
(109,38,6,2,32000),
(110,38,16,4,42000),
(111,39,19,1,125000),
(112,39,2,1,168000),
(113,40,14,5,23000),
(114,40,4,5,52000),
(115,41,16,2,42000),
(116,42,8,2,35000),
(117,42,2,1,168000),
(118,42,19,1,125000),
(119,42,18,2,28000),
(120,43,18,3,28000),
(121,44,15,2,31000),
(122,44,18,3,28000),
(123,44,2,1,168000),
(124,44,1,5,59000),
(125,44,3,1,24000),
(126,45,9,4,24000),
(127,45,15,3,31000),
(128,45,5,2,99000),
(129,46,7,2,58000),
(130,46,1,4,59000),
(131,46,19,1,125000),
(132,46,6,2,32000),
(133,46,16,4,42000),
(134,47,4,5,52000),
(135,47,8,2,35000),
(136,47,16,5,42000),
(137,47,14,2,23000),
(138,47,15,1,31000),
(139,48,1,4,59000),
(140,48,15,3,31000),
(141,49,1,1,59000),
(142,49,9,5,24000),
(143,49,19,1,125000),
(144,49,14,4,23000),
(145,50,2,1,168000),
(146,50,16,2,42000),
(147,50,7,2,58000),
(148,51,15,3,31000),
(149,51,3,1,24000),
(150,51,18,5,28000),
(151,52,3,1,24000),
(152,52,7,2,58000),
(153,52,18,3,28000),
(154,52,1,1,59000),
(155,52,8,1,35000),
(156,53,4,5,52000),
(157,53,3,1,24000),
(158,53,15,3,31000),
(159,54,15,3,31000),
(160,54,19,1,125000),
(161,55,6,1,32000),
(162,55,4,1,52000),
(163,55,2,1,168000),
(164,55,1,2,59000),
(165,55,7,2,58000),
(166,56,15,1,31000),
(167,57,15,4,31000),
(168,57,5,2,99000),
(169,57,9,5,24000),
(170,57,3,1,24000),
(171,57,4,1,52000),
(172,58,8,5,35000),
(173,58,16,3,42000),
(174,59,2,1,168000),
(175,60,2,1,168000),
(176,60,19,1,125000),
(177,61,6,4,32000),
(178,62,2,1,168000),
(179,62,16,5,42000),
(180,62,14,5,23000),
(181,63,16,3,42000),
(182,63,14,4,23000),
(183,63,8,1,35000),
(184,63,9,5,24000),
(185,63,7,2,58000),
(186,64,3,2,24000),
(187,64,1,2,59000),
(188,64,18,1,28000),
(189,65,5,2,99000),
(190,65,15,3,31000),
(191,65,14,5,23000),
(192,65,8,1,35000),
(193,66,4,5,52000),
(194,66,2,1,168000),
(195,67,8,3,35000),
(196,67,14,4,23000),
(197,67,16,2,42000),
(198,68,20,1,89000),
(199,68,21,1,119000),
(200,69,22,2,339000),
(201,69,23,3,79000),
(202,70,24,3,149000),
(203,70,25,2,85000),
(204,70,26,3,219000),
(205,70,27,2,429000),
(206,71,28,2,59000),
(207,71,29,2,109000),
(208,72,30,2,129000),
(209,72,31,3,139000),
(210,73,32,1,269000),
(211,73,33,2,89000),
(212,73,20,2,89000),
(213,74,21,3,119000),
(214,74,22,2,339000),
(215,75,23,3,79000),
(216,75,24,2,149000),
(217,76,31,1,139000),
(218,76,24,2,137000),
(219,77,20,2,89000),
(220,77,21,1,119000),
(221,77,32,1,269000),
(222,78,20,1,89000),
(223,79,25,1,85000),
(224,79,26,1,219000),
(225,79,27,1,429000),
(226,79,28,1,59000),
(227,80,29,1,109000),
(228,80,30,1,129000),
(229,81,30,2,129000),
(230,81,23,1,67000),
(231,81,31,1,139000),
(232,82,20,3,89000),
(233,82,23,2,67000),
(234,83,30,3,129000),
(235,83,32,3,269000),
(236,83,28,3,59000),
(237,84,20,2,89000),
(238,85,29,1,109000),
(239,86,24,2,137000),
(240,87,32,2,269000),
(241,87,22,1,339000),
(242,88,23,1,67000),
(243,88,32,2,269000),
(244,89,21,3,119000),
(245,89,28,1,59000),
(246,89,32,1,269000),
(247,90,21,1,119000),
(248,91,29,2,109000),
(249,91,30,2,129000),
(250,91,20,2,89000),
(251,92,24,3,137000),
(252,93,20,1,89000),
(253,94,22,2,339000),
(254,94,30,3,129000),
(255,94,32,2,269000),
(256,95,26,2,219000),
(257,95,28,2,59000),
(258,95,23,1,67000),
(259,96,23,2,67000),
(260,96,21,2,119000),
(261,96,31,2,139000),
(262,97,24,2,137000),
(263,98,20,3,89000),
(264,98,23,2,67000),
(265,99,23,1,67000),
(266,99,26,3,219000),
(267,100,30,1,129000),
(268,100,20,2,89000),
(269,100,29,1,109000);
INSERT @batchPlan(n,variant_n,lot,expiry_days,received_days,initial_qty,physical_qty,reserved_qty,dispose_qty,status) VALUES
(11,1,1,-2,-14,10,2,0,1,N'ACTIVE'),
(12,1,2,1,-3,5,3,3,0,N'ACTIVE'),
(13,1,3,6,-3,33,31,21,0,N'ACTIVE'),
(21,2,1,-2,-14,9,3,0,1,N'ACTIVE'),
(22,2,2,1,-3,6,5,5,0,N'ACTIVE'),
(23,2,3,6,-3,16,16,5,0,N'ACTIVE'),
(31,3,1,-2,-14,10,1,0,1,N'ACTIVE'),
(32,3,2,1,-3,16,9,8,0,N'ACTIVE'),
(41,4,1,-2,-14,14,2,0,1,N'ACTIVE'),
(42,4,2,1,-3,8,8,8,0,N'ACTIVE'),
(43,4,3,8,-3,38,33,20,0,N'ACTIVE'),
(51,5,1,-2,-14,7,3,0,1,N'ACTIVE'),
(52,5,2,1,-3,4,4,4,0,N'ACTIVE'),
(53,5,3,8,-3,20,18,4,0,N'ACTIVE'),
(61,6,1,-2,-14,11,1,0,2,N'ACTIVE'),
(62,6,2,1,-3,5,5,5,0,N'ACTIVE'),
(63,6,3,5,-3,25,25,10,0,N'ACTIVE'),
(71,7,1,-2,-14,7,2,0,2,N'ACTIVE'),
(72,7,2,1,-3,6,4,4,0,N'ACTIVE'),
(73,7,3,5,-3,22,22,6,0,N'ACTIVE'),
(81,8,1,-2,-14,8,3,0,1,N'ACTIVE'),
(82,8,2,1,-3,7,5,5,0,N'ACTIVE'),
(83,8,3,6,-3,26,24,22,0,N'ACTIVE'),
(91,9,1,-2,-14,8,1,0,1,N'ACTIVE'),
(92,9,2,1,-3,8,6,6,0,N'ACTIVE'),
(93,9,3,7,-3,33,33,15,0,N'ACTIVE'),
(101,10,1,-2,-14,9,2,0,1,N'ACTIVE'),
(103,10,3,8,-3,3,3,0,0,N'ACTIVE'),
(111,11,1,-2,-14,3,0,0,0,N'ACTIVE'),
(121,12,1,-2,-14,3,0,0,0,N'ACTIVE'),
(131,13,1,-2,-14,13,2,0,1,N'ACTIVE'),
(133,13,3,6,-3,6,6,0,0,N'BLOCKED'),
(141,14,1,-2,-14,8,3,0,1,N'ACTIVE'),
(142,14,2,1,-3,8,8,8,0,N'ACTIVE'),
(143,14,3,7,-3,48,39,16,0,N'ACTIVE'),
(151,15,1,-2,-14,7,1,0,1,N'ACTIVE'),
(152,15,2,1,-3,4,3,3,0,N'ACTIVE'),
(153,15,3,7,-3,52,49,25,0,N'ACTIVE'),
(161,16,1,-2,-14,13,2,0,1,N'ACTIVE'),
(162,16,2,1,-3,5,5,5,0,N'ACTIVE'),
(163,16,3,7,-3,57,55,30,0,N'ACTIVE'),
(171,17,1,-2,-14,9,3,0,1,N'ACTIVE'),
(173,17,3,8,-3,5,5,0,0,N'ACTIVE'),
(181,18,1,-2,-14,10,1,0,2,N'ACTIVE'),
(182,18,2,1,-3,7,6,6,0,N'ACTIVE'),
(183,18,3,5,-3,16,15,15,0,N'ACTIVE'),
(191,19,1,-2,-14,7,2,0,2,N'ACTIVE'),
(192,19,2,1,-3,8,8,8,0,N'ACTIVE'),
(201,20,1,-3,-14,7,3,0,1,N'ACTIVE'),
(202,20,2,3,-3,2,0,0,0,N'ACTIVE'),
(203,20,3,18,-3,18,12,6,0,N'ACTIVE'),
(211,21,1,-3,-14,6,1,0,1,N'ACTIVE'),
(212,21,2,3,-3,3,2,2,0,N'ACTIVE'),
(213,21,3,18,-3,11,11,4,0,N'ACTIVE'),
(221,22,1,-3,-14,7,2,0,1,N'ACTIVE'),
(222,22,2,3,-3,3,3,3,0,N'ACTIVE'),
(223,22,3,18,-3,8,8,0,0,N'ACTIVE'),
(231,23,1,-3,-14,10,3,0,1,N'ACTIVE'),
(232,23,2,3,-3,12,9,6,0,N'ACTIVE'),
(241,24,1,-3,-14,7,1,0,1,N'ACTIVE'),
(242,24,2,3,-3,10,8,7,0,N'ACTIVE'),
(251,25,1,-3,-14,10,7,0,1,N'ACTIVE'),
(261,26,1,-3,-14,8,3,0,2,N'ACTIVE'),
(262,26,2,3,-3,4,1,1,0,N'ACTIVE'),
(263,26,3,14,-3,13,13,1,0,N'ACTIVE'),
(271,27,1,-3,-14,5,1,0,2,N'ACTIVE'),
(273,27,3,14,-3,4,4,0,0,N'ACTIVE'),
(281,28,1,-3,-14,5,2,0,1,N'ACTIVE'),
(282,28,2,3,-3,2,2,2,0,N'ACTIVE'),
(283,28,3,15,-3,9,9,4,0,N'ACTIVE'),
(291,29,1,-3,-14,6,3,0,1,N'ACTIVE'),
(292,29,2,3,-3,3,2,2,0,N'ACTIVE'),
(293,29,3,15,-3,7,7,1,0,N'ACTIVE'),
(301,30,1,-3,-14,4,1,0,1,N'ACTIVE'),
(302,30,2,3,-3,4,3,3,0,N'ACTIVE'),
(303,30,3,16,-3,12,12,5,0,N'ACTIVE'),
(311,31,1,-3,-14,6,2,0,1,N'ACTIVE'),
(312,31,2,3,-3,3,2,2,0,N'ACTIVE'),
(313,31,3,17,-3,8,8,0,0,N'ACTIVE'),
(321,32,1,-3,-14,5,3,0,1,N'ACTIVE'),
(322,32,2,3,-3,2,1,1,0,N'ACTIVE'),
(323,32,3,17,-3,18,18,9,0,N'ACTIVE'),
(331,33,1,-3,-14,5,1,0,2,N'ACTIVE'),
(333,33,3,18,-3,6,6,0,0,N'ACTIVE');
INSERT @allocationPlan(line_n,batch_n,quantity,status) VALUES
(62,181,1,N'RELEASED'),
(63,191,1,N'RELEASED'),
(64,11,1,N'RELEASED'),
(65,21,1,N'RELEASED'),
(58,141,1,N'RELEASED'),
(59,151,1,N'RELEASED'),
(60,161,1,N'RELEASED'),
(61,171,1,N'RELEASED'),
(55,111,1,N'RELEASED'),
(56,121,1,N'RELEASED'),
(57,131,1,N'RELEASED'),
(52,81,1,N'RELEASED'),
(53,91,1,N'RELEASED'),
(54,101,1,N'RELEASED'),
(227,291,1,N'RELEASED'),
(228,301,1,N'RELEASED'),
(49,51,1,N'RELEASED'),
(50,61,1,N'RELEASED'),
(51,71,1,N'RELEASED'),
(223,251,1,N'RELEASED'),
(224,261,1,N'RELEASED'),
(225,271,1,N'RELEASED'),
(226,281,1,N'RELEASED'),
(41,31,2,N'SOLD'),
(42,41,3,N'SOLD'),
(39,11,5,N'SOLD'),
(40,21,2,N'SOLD'),
(215,231,3,N'SOLD'),
(216,241,2,N'SOLD'),
(37,181,2,N'SOLD'),
(38,191,2,N'SOLD'),
(35,161,5,N'SOLD'),
(36,171,1,N'SOLD'),
(213,211,3,N'SOLD'),
(214,221,2,N'SOLD'),
(31,121,1,N'SOLD'),
(32,131,5,N'SOLD'),
(33,141,2,N'SOLD'),
(34,151,4,N'SOLD'),
(28,91,1,N'SOLD'),
(29,101,5,N'SOLD'),
(30,111,2,N'SOLD'),
(210,321,1,N'SOLD'),
(211,331,2,N'SOLD'),
(212,201,2,N'SOLD'),
(25,61,5,N'SOLD'),
(26,71,2,N'SOLD'),
(27,81,3,N'SOLD'),
(23,41,3,N'SOLD'),
(24,51,2,N'SOLD'),
(208,301,2,N'SOLD'),
(209,311,3,N'SOLD'),
(20,11,1,N'SOLD'),
(21,21,1,N'SOLD'),
(22,31,4,N'SOLD'),
(17,171,4,N'SOLD'),
(18,181,5,N'SOLD'),
(19,191,1,N'SOLD'),
(206,281,2,N'SOLD'),
(207,291,2,N'SOLD'),
(15,151,1,N'SOLD'),
(16,161,5,N'SOLD'),
(11,111,1,N'SOLD'),
(12,121,2,N'SOLD'),
(13,131,5,N'SOLD'),
(14,141,2,N'SOLD'),
(202,241,3,N'SOLD'),
(203,251,2,N'SOLD'),
(204,261,3,N'SOLD'),
(205,271,2,N'SOLD'),
(7,71,1,N'SOLD'),
(8,81,1,N'SOLD'),
(9,91,5,N'SOLD'),
(10,101,1,N'SOLD'),
(5,51,1,N'SOLD'),
(6,61,3,N'SOLD'),
(200,221,2,N'SOLD'),
(201,231,3,N'SOLD'),
(3,31,2,N'SOLD'),
(4,41,5,N'SOLD'),
(1,11,1,N'SOLD'),
(2,21,2,N'SOLD'),
(198,201,1,N'SOLD'),
(199,211,1,N'SOLD'),
(43,72,2,N'SOLD'),
(44,92,2,N'SOLD'),
(217,312,1,N'SOLD'),
(218,242,2,N'SOLD'),
(45,12,2,N'SOLD'),
(46,182,1,N'SOLD'),
(219,202,2,N'SOLD'),
(220,212,1,N'SOLD'),
(221,322,1,N'SOLD'),
(47,152,1,N'SOLD'),
(48,32,5,N'SOLD'),
(222,203,1,N'SOLD'),
(141,12,1,N'RESERVED'),
(142,92,5,N'RESERVED'),
(143,192,1,N'RESERVED'),
(144,142,4,N'RESERVED'),
(139,12,2,N'RESERVED'),
(139,13,2,N'RESERVED'),
(140,152,3,N'RESERVED'),
(167,153,4,N'RESERVED'),
(168,52,2,N'RESERVED'),
(169,92,1,N'RESERVED'),
(169,93,4,N'RESERVED'),
(170,32,1,N'RESERVED'),
(171,42,1,N'RESERVED'),
(105,162,3,N'RESERVED'),
(134,42,5,N'RESERVED'),
(135,82,2,N'RESERVED'),
(136,162,2,N'RESERVED'),
(136,163,3,N'RESERVED'),
(137,142,2,N'RESERVED'),
(138,153,1,N'RESERVED'),
(166,153,1,N'RESERVED'),
(129,72,2,N'RESERVED'),
(130,13,4,N'RESERVED'),
(131,192,1,N'RESERVED'),
(132,62,2,N'RESERVED'),
(133,163,4,N'RESERVED'),
(161,62,1,N'RESERVED'),
(162,42,1,N'RESERVED'),
(163,22,1,N'RESERVED'),
(164,13,2,N'RESERVED'),
(165,72,2,N'RESERVED'),
(97,142,1,N'RESERVED'),
(98,42,1,N'RESERVED'),
(98,43,3,N'RESERVED'),
(99,82,3,N'RESERVED'),
(126,93,4,N'RESERVED'),
(127,153,3,N'RESERVED'),
(128,52,2,N'RESERVED'),
(159,153,3,N'RESERVED'),
(160,192,1,N'RESERVED'),
(96,142,1,N'RESERVED'),
(121,153,2,N'RESERVED'),
(122,182,3,N'RESERVED'),
(123,22,1,N'RESERVED'),
(124,13,5,N'RESERVED'),
(125,32,1,N'RESERVED'),
(156,43,5,N'RESERVED'),
(157,32,1,N'RESERVED'),
(158,153,3,N'RESERVED'),
(195,82,2,N'SOLD'),
(195,83,1,N'SOLD'),
(196,143,4,N'SOLD'),
(197,163,2,N'SOLD'),
(120,182,3,N'RESERVED'),
(181,163,3,N'RESERVED'),
(182,143,4,N'RESERVED'),
(183,83,1,N'RESERVED'),
(184,93,5,N'RESERVED'),
(185,73,2,N'RESERVED'),
(151,32,1,N'RESERVED'),
(152,73,2,N'RESERVED'),
(153,183,3,N'RESERVED'),
(154,13,1,N'RESERVED'),
(155,83,1,N'RESERVED'),
(193,43,5,N'SOLD'),
(194,22,1,N'SOLD'),
(251,242,3,N'RESERVED'),
(267,302,1,N'SOLD'),
(268,203,2,N'SOLD'),
(269,292,1,N'SOLD'),
(92,13,5,N'RESERVED'),
(116,83,2,N'RESERVED'),
(117,22,1,N'RESERVED'),
(118,192,1,N'RESERVED'),
(119,183,2,N'RESERVED'),
(178,22,1,N'RESERVED'),
(179,163,5,N'RESERVED'),
(180,143,5,N'RESERVED'),
(262,242,2,N'RESERVED'),
(148,153,3,N'RESERVED'),
(149,32,1,N'RESERVED'),
(150,183,5,N'RESERVED'),
(189,53,2,N'SOLD'),
(190,153,3,N'SOLD'),
(191,143,5,N'SOLD'),
(192,83,1,N'SOLD'),
(248,292,2,N'RESERVED'),
(249,302,2,N'RESERVED'),
(250,203,2,N'RESERVED'),
(265,232,1,N'SOLD'),
(266,262,3,N'SOLD'),
(115,163,2,N'RESERVED'),
(177,62,2,N'RESERVED'),
(177,63,2,N'RESERVED'),
(244,212,2,N'RESERVED'),
(244,213,1,N'RESERVED'),
(245,282,1,N'RESERVED'),
(246,322,1,N'RESERVED'),
(259,232,2,N'RESERVED'),
(260,213,2,N'RESERVED'),
(261,312,2,N'RESERVED'),
(87,22,1,N'RESERVED'),
(88,63,3,N'RESERVED'),
(89,53,2,N'RESERVED'),
(90,83,4,N'RESERVED'),
(91,13,2,N'RESERVED'),
(145,23,1,N'RESERVED'),
(146,163,2,N'RESERVED'),
(147,73,2,N'RESERVED'),
(186,32,2,N'SOLD'),
(187,13,2,N'SOLD'),
(188,183,1,N'SOLD'),
(247,213,1,N'RESERVED'),
(263,203,3,N'SOLD'),
(264,232,2,N'SOLD'),
(113,143,5,N'RESERVED'),
(114,43,5,N'RESERVED'),
(175,23,1,N'RESERVED'),
(176,192,1,N'RESERVED'),
(242,232,1,N'RESERVED'),
(243,323,2,N'RESERVED'),
(256,262,1,N'RESERVED'),
(256,263,1,N'RESERVED'),
(257,282,1,N'RESERVED'),
(257,283,1,N'RESERVED'),
(258,232,1,N'RESERVED'),
(111,192,1,N'RESERVED'),
(112,23,1,N'RESERVED'),
(174,23,1,N'RESERVED'),
(240,323,2,N'RESERVED'),
(241,222,1,N'RESERVED'),
(253,222,2,N'RESERVED'),
(254,302,1,N'RESERVED'),
(254,303,2,N'RESERVED'),
(255,323,2,N'RESERVED'),
(80,83,3,N'RESERVED'),
(81,192,1,N'RESERVED'),
(82,153,1,N'RESERVED'),
(83,93,2,N'RESERVED'),
(238,293,1,N'RESERVED'),
(106,83,4,N'RESERVED'),
(107,192,1,N'RESERVED'),
(108,43,3,N'RESERVED'),
(109,63,2,N'RESERVED'),
(110,163,4,N'RESERVED'),
(172,83,5,N'RESERVED'),
(173,163,3,N'RESERVED'),
(239,242,2,N'RESERVED'),
(252,203,1,N'RESERVED'),
(76,163,4,N'RESERVED'),
(77,143,2,N'RESERVED'),
(78,23,1,N'RESERVED'),
(79,43,4,N'RESERVED'),
(234,303,3,N'RESERVED'),
(235,323,3,N'RESERVED'),
(236,283,3,N'RESERVED'),
(67,153,4,N'RESERVED'),
(68,53,2,N'RESERVED'),
(69,32,3,N'RESERVED'),
(70,83,2,N'RESERVED'),
(71,63,3,N'RESERVED'),
(232,203,3,N'RESERVED'),
(233,232,2,N'RESERVED'),
(66,183,5,N'RESERVED');


 INSERT dbo.roles(role_name,description) VALUES
 ('ROLE_ADMIN',N'Superadmin quản trị toàn sàn'),('ROLE_STORE_MANAGER',N'Chủ shop, một người vận hành'),
 ('ROLE_CUSTOMER',N'Khách mua hàng'),('ROLE_DELIVERY_STAFF',N'Nhân viên giao hàng');
 DECLARE @n INT=1,@id BIGINT,@admin BIGINT,@owner BIGINT,@accountCreated DATETIME2(0);
 WHILE @n<=19
 BEGIN
  SET @accountCreated=DATEADD(HOUR,@n*3,DATEADD(DAY,-8,@created));
  INSERT dbo.users(email,username,password_hash,full_name,phone,status,email_verified_at,created_at,updated_at)
   SELECT email,username,@password,full_name,phone,'ACTIVE',@accountCreated,@accountCreated,@accountCreated FROM @userPlan WHERE n=@n;
  SET @id=SCOPE_IDENTITY(); UPDATE @userPlan SET id=@id WHERE n=@n;
  INSERT dbo.user_roles(user_id,role_id,assigned_at)
   SELECT @id,r.role_id,@accountCreated FROM @userPlan u JOIN dbo.roles r ON r.role_name=u.role_name WHERE u.n=@n;
  IF @n>=8
   INSERT dbo.addresses(user_id,recipient_name,phone,province,ward,address_line,formatted_address,latitude,longitude,is_default,created_at,updated_at)
    SELECT @id,u.full_name,u.phone,N'TP Hồ Chí Minh',a.ward,a.address_line,
      a.address_line+N', '+a.ward+N', TP Hồ Chí Minh',a.latitude,a.longitude,1,@created,@created FROM @userPlan u JOIN @addressPlan a ON a.user_n=u.n WHERE u.n=@n;
  SET @n+=1;
 END;
 SELECT @admin=id FROM @userPlan WHERE n=1;
 DECLARE @application BIGINT,@shop BIGINT,@shopName NVARCHAR(200),@shopCreated DATETIME2(0);
 SET @n=1;
 WHILE @n<=3
 BEGIN
  SELECT @owner=u.id,@shopName=s.name FROM @shopPlan s JOIN @userPlan u ON u.n=s.owner_n WHERE s.n=@n;
  SET @shopCreated=CASE WHEN @n=3 THEN DATEADD(HOUR,-2,@now) ELSE DATEADD(DAY,(@n-1)*3,@created) END;
  INSERT dbo.seller_applications(user_id,business_name,status,reviewed_by,submitted_at,reviewed_at,updated_at)
   VALUES(@owner,@shopName,'APPROVED',@admin,DATEADD(DAY,-1,@shopCreated),@shopCreated,@shopCreated);
  SET @application=SCOPE_IDENTITY();
  INSERT dbo.seller_application_status_history(application_id,old_status,new_status,changed_by,note,created_at)
   VALUES(@application,NULL,'PENDING',@owner,N'Nộp hồ sơ đăng ký người bán',DATEADD(DAY,-1,@shopCreated)),
    (@application,'PENDING','UNDER_REVIEW',@admin,N'Đối chiếu thông tin đăng ký kinh doanh',DATEADD(HOUR,-1,@shopCreated)),
    (@application,'UNDER_REVIEW','APPROVED',@admin,N'Đã duyệt mở shop',@shopCreated);
  INSERT dbo.shops(application_id,shop_name,description,phone,email,logo_url,banner_url,status,availability_status,created_at,updated_at)
   SELECT @application,name,description,phone,email,logo,banner,'ACTIVE','OPEN',@shopCreated,@shopCreated FROM @shopPlan WHERE n=@n;
  SET @shop=SCOPE_IDENTITY(); UPDATE @shopPlan SET id=@shop WHERE n=@n;
  INSERT dbo.shop_members(shop_id,user_id,member_role,status,joined_at) VALUES(@shop,@owner,'OWNER','ACTIVE',@shopCreated);
  UPDATE ur SET assigned_at=@shopCreated FROM dbo.user_roles ur JOIN dbo.roles r ON r.role_id=ur.role_id WHERE ur.user_id=@owner AND r.role_name='ROLE_STORE_MANAGER';
  INSERT dbo.shop_locations(shop_id,province,ward,address_line,latitude,longitude)
   SELECT @shop,N'TP Hồ Chí Minh',ward,address_line,latitude,longitude FROM @shopPlan WHERE n=@n;
  INSERT dbo.shop_business_hours(shop_id,day_of_week,open_time,close_time,is_closed)
   SELECT @shop,v.d,CASE WHEN @n=1 THEN '06:30' WHEN @n=2 THEN '08:00' ELSE '07:00' END,CASE WHEN @n=1 THEN '19:30' WHEN @n=2 THEN '21:00' ELSE '18:00' END,0 FROM (VALUES(1),(2),(3),(4),(5),(6),(7)) v(d);
  SET @n+=1;
 END;
 DECLARE @rootCategory BIGINT,@local BIGINT,@imported BIGINT,@cut BIGINT,@gift BIGINT;
 INSERT dbo.categories(name,slug,created_at,updated_at) VALUES(N'Trái cây','trai-cay',@created,@created); SET @rootCategory=SCOPE_IDENTITY();
 INSERT dbo.categories(parent_category_id,name,slug,created_at,updated_at) VALUES(@rootCategory,N'Trái cây Việt Nam','trai-cay-viet-nam',@created,@created); SET @local=SCOPE_IDENTITY();
 INSERT dbo.categories(parent_category_id,name,slug,created_at,updated_at) VALUES(@rootCategory,N'Trái cây nhập khẩu','trai-cay-nhap-khau',@created,@created); SET @imported=SCOPE_IDENTITY();
 INSERT dbo.categories(parent_category_id,name,slug,created_at,updated_at) VALUES(@rootCategory,N'Trái cây sơ chế','trai-cay-so-che',@created,@created); SET @cut=SCOPE_IDENTITY();
 INSERT dbo.categories(parent_category_id,name,slug,created_at,updated_at) VALUES(@rootCategory,N'Quà tặng trái cây','qua-tang-trai-cay',@created,@created); SET @gift=SCOPE_IDENTITY();
 INSERT dbo.categories(name,slug,status,created_at,updated_at) VALUES(N'Danh mục mùa vụ tạm ngừng','mua-vu-tam-ngung','INACTIVE',@created,@created);
 DECLARE @pid BIGINT,@vid BIGINT,@pack INT,@price DECIMAL(18,2),@bid BIGINT,@category VARCHAR(20),@scenario VARCHAR(20),@productCreated DATETIME2(0);
 SET @n=1;
 WHILE @n<=20
 BEGIN
  SET @productCreated=DATEADD(HOUR,@n*11,DATEADD(DAY,5,@created));
  SELECT @shop=s.id,@owner=u.id,@category=p.category,@scenario=p.scenario FROM @productPlan p JOIN @shopPlan s ON s.n=p.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE p.n=@n;
  INSERT dbo.products(shop_id,name,slug,description,requires_preparation_check,preparation_note,expiry_warning_days,created_at,updated_at)
   SELECT @shop,name,slug,description,
    prep,CASE WHEN prep=1 THEN N'Kiểm tra độ chín, sơ chế theo quy cách và đóng gói trước khi bàn giao.' END,warning,
    @productCreated,@productCreated FROM @productPlan WHERE n=@n;
  SET @pid=SCOPE_IDENTITY(); UPDATE @productPlan SET id=@pid WHERE n=@n;
  INSERT dbo.product_categories(product_id,category_id) VALUES(@pid,CASE @category WHEN 'IMPORTED' THEN @imported WHEN 'CUT' THEN @cut WHEN 'GIFT' THEN @gift ELSE @local END);
  INSERT dbo.product_images(product_id,image_url,is_primary,sort_order,created_at) SELECT @pid,image,1,0,@productCreated FROM @productPlan WHERE n=@n;
  SET @pack=1;
  WHILE EXISTS(SELECT 1 FROM @variantPlan WHERE product_n=@n AND pack=@pack)
  BEGIN
   INSERT dbo.product_variants(product_id,sku,variant_name,thumbnail_url,price,compare_at_price,weight,unit,low_stock_threshold,created_at,updated_at)
    SELECT @pid,'TMP-'+CONVERT(VARCHAR(36),NEWID()),v.name,p.image,v.price,v.compare_price,
     v.weight,v.unit,v.low_stock,@productCreated,@productCreated
    FROM @variantPlan v JOIN @productPlan p ON p.n=v.product_n WHERE v.product_n=@n AND v.pack=@pack;
   SET @vid=SCOPE_IDENTITY(); UPDATE @variantPlan SET id=@vid WHERE product_n=@n AND pack=@pack;
   UPDATE dbo.product_variants SET sku='SKU-'+RIGHT('000000'+CONVERT(VARCHAR(20),@vid),CASE WHEN LEN(CONVERT(VARCHAR(20),@vid))>6 THEN LEN(CONVERT(VARCHAR(20),@vid)) ELSE 6 END) WHERE variant_id=@vid;
   INSERT dbo.product_images(product_id,variant_id,image_url,is_primary,sort_order,created_at) SELECT @pid,@vid,image,1,0,@productCreated FROM @productPlan WHERE n=@n;
   SELECT @price=price FROM dbo.product_variants WHERE variant_id=@vid;
   INSERT dbo.price_history(variant_id,price,effective_from,effective_to,changed_by) VALUES
    (@vid,@price-5000,@productCreated,DATEADD(DAY,-25,@now),@owner),(@vid,@price,DATEADD(DAY,-25,@now),NULL,@owner);
   INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at) VALUES(@owner,'UPDATE','VARIANT',@vid,N'Cập nhật giá bán sau đợt nhập hàng mới',DATEADD(DAY,-25,@now));
   SET @pack+=1;
  END;
  UPDATE dbo.products SET status='ACTIVE',seller_hidden=0 WHERE product_id=@pid;
  IF @scenario<>'pending'
  BEGIN
   EXEC dbo.usp_review_product @product_id=@pid,@admin_user_id=@admin,@decision='APPROVED',@note=N'Đối chiếu thông tin nguồn gốc và quy cách sản phẩm';
   UPDATE dbo.products SET approved_at=DATEADD(DAY,1,@productCreated),updated_at=DATEADD(DAY,1,@productCreated) WHERE product_id=@pid;
   UPDATE dbo.product_review_history SET created_at=DATEADD(DAY,1,@productCreated) WHERE product_id=@pid;
  END;
  INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at) VALUES(@owner,'CREATE','PRODUCT',@pid,N'Thêm sản phẩm, quy cách bán và thông tin bảo quản',@productCreated);
  SET @n+=1;
 END;
 -- Initial stock precedes reservations; physical changes are written from the same allocation plan.
 DECLARE @bn INT,@vn INT,@initial INT,@received DATETIME2(0),@expiry DATE;
 DECLARE batches CURSOR LOCAL FAST_FORWARD FOR SELECT n,variant_n,initial_qty,DATEADD(DAY,received_days,@now),DATEADD(DAY,expiry_days,@today) FROM @batchPlan ORDER BY n;
 OPEN batches; FETCH NEXT FROM batches INTO @bn,@vn,@initial,@received,@expiry;
 WHILE @@FETCH_STATUS=0
 BEGIN
  SELECT @vid=v.id,@price=v.price,@owner=u.id FROM @variantPlan v JOIN @productPlan p ON p.n=v.product_n JOIN @shopPlan s ON s.n=p.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE v.n=@vn;
  INSERT dbo.inventory_batches(variant_id,quantity_on_hand,reserved_quantity,cost_price,received_at,expiry_date,created_at,updated_at) VALUES(@vid,@initial,0,FLOOR(@price*0.6),@received,@expiry,@received,@received);
  SET @bid=SCOPE_IDENTITY(); UPDATE @batchPlan SET id=@bid WHERE n=@bn;
  IF @initial>0
   INSERT dbo.inventory_transactions(variant_id,batch_id,transaction_type,quantity,reference_type,reference_id,note,created_by,created_at)
    VALUES(@vid,@bid,'RESTOCK',@initial,'BATCH',@bid,N'Nhập lô trái cây phục vụ đơn hàng',@owner,@received);
  INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at) VALUES(@owner,'RESTOCK','BATCH',@bid,N'Nhập hàng theo lô và hạn sử dụng',@received);
  FETCH NEXT FROM batches INTO @bn,@vn,@initial,@received,@expiry;
 END; CLOSE batches; DEALLOCATE batches;
 INSERT dbo.payment_methods(name,method_code) VALUES(N'Thanh toán khi nhận hàng','COD'),(N'Chuyển khoản ngân hàng','BANK_TRANSFER');
 DECLARE @promotionPlan TABLE(shop_n INT,promotion_id BIGINT,coupon_id BIGINT,discount DECIMAL(18,2),minimum_value DECIMAL(18,2));
 DECLARE @promo BIGINT,@coupon BIGINT;
 SET @n=1;
 WHILE @n<=2
 BEGIN
  SELECT @shop=id FROM @shopPlan WHERE n=@n;
  INSERT dbo.promotions(shop_id,name,description,discount_type,discount_value,minimum_order_value,start_at,end_at,status,created_at,updated_at)
   VALUES(@shop,CASE @n WHEN 1 THEN N'Giỏ trái cây gia đình' ELSE N'Trái cây tuyển chọn cuối tuần' END,
    N'Ưu đãi theo giá trị đơn hàng, áp dụng khi nhập mã của cửa hàng.','FIXED',CASE @n WHEN 1 THEN 15000 ELSE 25000 END,CASE @n WHEN 1 THEN 150000 ELSE 300000 END,DATEADD(DAY,-14,@now),DATEADD(DAY,7,@now),'ACTIVE',DATEADD(DAY,-14,@now),DATEADD(DAY,-14,@now));
  SET @promo=SCOPE_IDENTITY();
  INSERT dbo.coupons(promotion_id,code,usage_limit,per_user_limit,minimum_order_value,start_at,end_at,status)
   VALUES(@promo,CASE @n WHEN 1 THEN 'SONGIADINH' ELSE 'HUNGCHONLOC' END,500,5,CASE @n WHEN 1 THEN 150000 ELSE 300000 END,DATEADD(DAY,-14,@now),DATEADD(DAY,7,@now),'ACTIVE');
  SET @coupon=SCOPE_IDENTITY();
  INSERT @promotionPlan VALUES(@n,@promo,@coupon,CASE @n WHEN 1 THEN 15000 ELSE 25000 END,CASE @n WHEN 1 THEN 150000 ELSE 300000 END);
  INSERT dbo.promotion_products(promotion_id,product_id) SELECT @promo,id FROM @productPlan WHERE shop_n=@n AND status='ACTIVE' AND approval<>'REJECTED';
  SET @n+=1;
 END;
 DECLARE @customer BIGINT,@order BIGINT,@so BIGINT,@placed DATETIME2(0),@status VARCHAR(30),@method VARCHAR(50),@subtotal DECIMAL(18,2),@payment BIGINT,@paid BIT,@refunded BIT,@delivery BIGINT,@staff BIGINT,@shipping DECIMAL(18,2),@discount DECIMAL(18,2),@total DECIMAL(18,2),@shopN INT;
 SET @n=1;
 WHILE @n<=100
 BEGIN
  SELECT @customer=u.id,@shop=s.id,@owner=ow.id,@status=o.status,@method=o.method,@placed=DATEADD(MINUTE,o.minutes_ago,@now),@shipping=o.shipping,@shopN=o.shop_n
   FROM @orderPlan o JOIN @userPlan u ON u.n=o.customer_n JOIN @shopPlan s ON s.n=o.shop_n JOIN @userPlan ow ON ow.n=s.owner_n WHERE o.n=@n;
  SELECT @subtotal=SUM(quantity*price) FROM @linePlan WHERE order_n=@n;
  SET @discount=0;
  IF @n%5=0 AND @status<>'CANCELLED'
   SELECT @discount=discount FROM @promotionPlan WHERE shop_n=@shopN AND @subtotal>=minimum_value;
  SET @total=@subtotal-@discount+@shipping;
  SET @paid=CASE WHEN @status='COMPLETED' OR (@method='BANK_TRANSFER' AND @status NOT IN('PENDING','CANCELLED')) THEN 1 ELSE 0 END;
  SET @refunded=CASE WHEN @method='BANK_TRANSFER' AND @status='CANCELLED' THEN 1 ELSE 0 END;
  INSERT dbo.orders(customer_id,order_code,status,payment_status,subtotal,discount_total,shipping_fee,grand_total,placed_at,completed_at,cancelled_at)
   VALUES(@customer,'TMP-'+CONVERT(VARCHAR(36),NEWID()),CASE WHEN @status='COMPLETED' THEN 'COMPLETED' WHEN @status='CANCELLED' THEN 'CANCELLED' WHEN @status='PENDING' THEN 'PENDING' WHEN @status='CONFIRMED' THEN 'CONFIRMED' ELSE 'PROCESSING' END,
    CASE WHEN @refunded=1 THEN 'REFUNDED' WHEN @paid=1 THEN 'PAID' ELSE 'UNPAID' END,@subtotal,@discount,@shipping,@total,@placed,CASE WHEN @status='COMPLETED' THEN DATEADD(HOUR,3,@placed) END,CASE WHEN @status='CANCELLED' THEN DATEADD(HOUR,2,@placed) END);
  SET @order=SCOPE_IDENTITY();
  UPDATE dbo.orders SET order_code='ORD-'+CASE WHEN LEN(CONVERT(VARCHAR(20),@order))<6 THEN REPLICATE('0',6-LEN(CONVERT(VARCHAR(20),@order))) ELSE '' END+CONVERT(VARCHAR(20),@order) WHERE order_id=@order;
  INSERT dbo.order_addresses(order_id,recipient_name,phone,province,ward,address_line,formatted_address,latitude,longitude)
   SELECT @order,recipient_name,phone,province,ward,address_line,formatted_address,latitude,longitude FROM dbo.addresses WHERE user_id=@customer AND is_default=1;
  -- Insert lines while PENDING so preparation snapshot triggers run normally.
  INSERT dbo.shop_orders(order_id,shop_id,status,subtotal,discount_total,shipping_fee,total_amount,shipping_distance_meters) VALUES(@order,@shop,'PENDING',@subtotal,@discount,@shipping,@total,1800+(@n*397)%12600);
  SET @so=SCOPE_IDENTITY(); UPDATE @orderPlan SET id=@order,shop_order_id=@so,placed=@placed WHERE n=@n;
  IF @discount>0
  BEGIN
   INSERT dbo.coupon_usages(coupon_id,user_id,order_id,discount_amount,used_at) SELECT coupon_id,@customer,@order,@discount,@placed FROM @promotionPlan WHERE shop_n=@shopN;
   INSERT dbo.order_discounts(shop_order_id,promotion_id,coupon_id,discount_amount,created_at) SELECT @so,promotion_id,coupon_id,@discount,@placed FROM @promotionPlan WHERE shop_n=@shopN;
  END;
  DECLARE @ln INT,@quantity INT,@line BIGINT;
  DECLARE order_lines CURSOR LOCAL FAST_FORWARD FOR SELECT n,variant_n,quantity,price FROM @linePlan WHERE order_n=@n ORDER BY n;
  OPEN order_lines; FETCH NEXT FROM order_lines INTO @ln,@vn,@quantity,@price;
  WHILE @@FETCH_STATUS=0
  BEGIN
   INSERT dbo.order_items(shop_order_id,variant_id,product_name_snapshot,variant_name_snapshot,sku_snapshot,unit_price,quantity,total_amount)
    SELECT @so,v.variant_id,p.name,v.variant_name,v.sku,@price,@quantity,@price*@quantity FROM @variantPlan m JOIN dbo.product_variants v ON v.variant_id=m.id JOIN dbo.products p ON p.product_id=v.product_id WHERE m.n=@vn;
   SET @line=SCOPE_IDENTITY(); UPDATE @linePlan SET id=@line WHERE n=@ln;
   IF @status IN('READY_FOR_DELIVERY','OUT_FOR_DELIVERY','COMPLETED')
    INSERT dbo.order_item_preparation_checks(order_item_id,checked_by,checked_at,note)
     SELECT @line,@owner,DATEADD(MINUTE,45,@placed),N'Đã kiểm tra và đóng gói theo yêu cầu' FROM dbo.order_items WHERE order_item_id=@line AND requires_preparation_check_snapshot=1;
   FETCH NEXT FROM order_lines INTO @ln,@vn,@quantity,@price;
  END; CLOSE order_lines; DEALLOCATE order_lines;
  INSERT dbo.order_status_history(shop_order_id,status,changed_by,note,created_at) VALUES(@so,'PENDING',@customer,N'Khách đặt hàng',@placed);
  IF @status<>'PENDING'
   INSERT dbo.order_status_history(shop_order_id,status,changed_by,note,created_at) VALUES(@so,'CONFIRMED',@owner,N'Shop xác nhận và giữ hàng',DATEADD(MINUTE,15,@placed));
  IF @status IN('PREPARING','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','COMPLETED')
   INSERT dbo.order_status_history(shop_order_id,status,changed_by,note,created_at) VALUES(@so,'PREPARING',@owner,N'Đang kiểm tra và đóng gói',DATEADD(MINUTE,30,@placed));
  IF @status IN('READY_FOR_DELIVERY','OUT_FOR_DELIVERY','COMPLETED')
   INSERT dbo.order_status_history(shop_order_id,status,changed_by,note,created_at) VALUES(@so,'READY_FOR_DELIVERY',@owner,N'Đã sẵn sàng bàn giao',DATEADD(MINUTE,60,@placed));
  IF @status IN('OUT_FOR_DELIVERY','COMPLETED')
   INSERT dbo.order_status_history(shop_order_id,status,changed_by,note,created_at) VALUES(@so,'OUT_FOR_DELIVERY',@owner,N'Đã xuất kho, bàn giao người giao hàng',DATEADD(MINUTE,90,@placed));
  IF @status IN('COMPLETED','CANCELLED')
   INSERT dbo.order_status_history(shop_order_id,status,changed_by,note,created_at) VALUES(@so,@status,CASE WHEN @status='CANCELLED' THEN @customer ELSE @owner END,CASE WHEN @status='CANCELLED' THEN N'Khách hủy, trả toàn bộ hàng đã giữ' ELSE N'Giao hàng thành công' END,DATEADD(HOUR,CASE WHEN @status='CANCELLED' THEN 2 ELSE 3 END,@placed));
  UPDATE dbo.shop_orders SET status=@status,confirmed_at=CASE WHEN @status<>'PENDING' THEN DATEADD(MINUTE,15,@placed) END,
   ready_at=CASE WHEN @status IN('READY_FOR_DELIVERY','OUT_FOR_DELIVERY','COMPLETED') THEN DATEADD(MINUTE,60,@placed) END,
   completed_at=CASE WHEN @status='COMPLETED' THEN DATEADD(HOUR,3,@placed) END,cancelled_at=CASE WHEN @status='CANCELLED' THEN DATEADD(HOUR,2,@placed) END WHERE shop_order_id=@so;
  INSERT dbo.payments(order_id,payment_method_id,amount,status,provider,transaction_reference,created_at,paid_at)
   SELECT @order,payment_method_id,@total,CASE WHEN @refunded=1 THEN 'REFUNDED' WHEN @paid=1 THEN 'PAID' WHEN @status='CANCELLED' THEN 'CANCELLED' ELSE 'PENDING' END,CASE WHEN @method='COD' THEN N'COD' ELSE N'BANK_TRANSFER' END,N'PAY-'+CONVERT(NVARCHAR(20),@order),@placed,
    CASE WHEN @paid=1 OR @refunded=1 THEN DATEADD(MINUTE,CASE WHEN @method='COD' THEN 180 ELSE 5 END,@placed) END FROM dbo.payment_methods WHERE method_code=@method;
  SET @payment=SCOPE_IDENTITY();
  INSERT dbo.payment_transactions(payment_id,event_type,amount,status,created_at) VALUES(@payment,'CREATED',@total,'PENDING',@placed);
  IF @paid=1 OR @refunded=1
   INSERT dbo.payment_transactions(payment_id,event_type,amount,status,provider_transaction_id,created_at) VALUES(@payment,'PAYMENT',@total,'PAID',N'TXN-'+CONVERT(NVARCHAR(20),@payment),DATEADD(MINUTE,CASE WHEN @method='COD' THEN 180 ELSE 5 END,@placed));
  IF @refunded=1
   INSERT dbo.payment_transactions(payment_id,event_type,amount,status,provider_transaction_id,created_at) VALUES(@payment,'REFUND',@total,'REFUNDED',N'REF-'+CONVERT(NVARCHAR(20),@payment),DATEADD(HOUR,2,@placed));
  IF @status IN('READY_FOR_DELIVERY','OUT_FOR_DELIVERY','COMPLETED')
  BEGIN
   SELECT @staff=id FROM @userPlan WHERE n=5+(@n%3);
   INSERT dbo.deliveries(shop_order_id,status,assigned_at,picked_up_at,delivered_at,created_at,updated_at)
    VALUES(@so,CASE WHEN @status='COMPLETED' THEN 'DELIVERED' WHEN @status='OUT_FOR_DELIVERY' THEN 'OUT_FOR_DELIVERY' WHEN @n%3=0 THEN 'UNASSIGNED' ELSE 'ASSIGNED' END,
     CASE WHEN @status<>'READY_FOR_DELIVERY' OR @n%3<>0 THEN DATEADD(MINUTE,65,@placed) END,
     CASE WHEN @status IN('OUT_FOR_DELIVERY','COMPLETED') THEN DATEADD(MINUTE,90,@placed) END,
     CASE WHEN @status='COMPLETED' THEN DATEADD(HOUR,3,@placed) END,DATEADD(MINUTE,60,@placed),CASE WHEN @status='COMPLETED' THEN DATEADD(HOUR,3,@placed) ELSE DATEADD(MINUTE,90,@placed) END);
   SET @delivery=SCOPE_IDENTITY();
   INSERT dbo.delivery_status_history(delivery_id,status,changed_by,note,created_at) VALUES(@delivery,'UNASSIGNED',@owner,N'Chờ phân công giao hàng',DATEADD(MINUTE,60,@placed));
   IF @status<>'READY_FOR_DELIVERY' OR @n%3<>0
   BEGIN
    INSERT dbo.delivery_assignments(delivery_id,delivery_staff_id,assigned_by,status,assigned_at,accepted_at) VALUES(@delivery,@staff,@admin,CASE WHEN @status='COMPLETED' THEN 'COMPLETED' WHEN @status='OUT_FOR_DELIVERY' THEN 'ACCEPTED' ELSE 'ASSIGNED' END,DATEADD(MINUTE,65,@placed),CASE WHEN @status IN('COMPLETED','OUT_FOR_DELIVERY') THEN DATEADD(MINUTE,70,@placed) END);
    INSERT dbo.delivery_status_history(delivery_id,status,changed_by,note,created_at) VALUES(@delivery,'ASSIGNED',@admin,N'Phân công người giao hàng',DATEADD(MINUTE,65,@placed));
   END;
   IF @status IN('OUT_FOR_DELIVERY','COMPLETED')
   BEGIN
    INSERT dbo.delivery_status_history(delivery_id,status,changed_by,note,created_at) VALUES(@delivery,'ACCEPTED',@staff,N'Đã nhận chuyến giao',DATEADD(MINUTE,70,@placed)),(@delivery,'PICKED_UP',@staff,N'Đã nhận hàng tại shop',DATEADD(MINUTE,90,@placed)),(@delivery,'OUT_FOR_DELIVERY',@staff,N'Đang giao đến khách',DATEADD(MINUTE,95,@placed));
   END;
   IF @status='COMPLETED'
    INSERT dbo.delivery_status_history(delivery_id,status,changed_by,note,created_at) VALUES(@delivery,'DELIVERED',@staff,N'Khách đã nhận hàng',DATEADD(HOUR,3,@placed));
  END;
  IF @status='COMPLETED'
   INSERT dbo.seller_payouts(shop_id,shop_order_id,gross_amount,commission_amount,net_amount,status,processed_at,transaction_reference,created_at)
    VALUES(@shop,@so,@subtotal-@discount,FLOOR((@subtotal-@discount)*0.05),(@subtotal-@discount)-FLOOR((@subtotal-@discount)*0.05),CASE WHEN @n%3=0 THEN 'PENDING' ELSE 'PAID' END,
     CASE WHEN @n%3<>0 THEN DATEADD(DAY,1,@placed) END,N'PAYOUT-'+CONVERT(NVARCHAR(20),@so),DATEADD(HOUR,3,@placed));
  SET @n+=1;
 END;
 -- One RESERVE entry for every allocation; SALE/RELEASE consume the hold once.
 INSERT dbo.stock_allocations(order_item_id,batch_id,quantity,status)
  SELECT l.id,b.id,a.quantity,a.status FROM @allocationPlan a JOIN @linePlan l ON l.n=a.line_n JOIN @batchPlan b ON b.n=a.batch_n;
 INSERT dbo.inventory_transactions(variant_id,batch_id,transaction_type,quantity,reference_type,reference_id,note,created_by,created_at)
  SELECT v.id,b.id,'RESERVE',a.quantity,'ORDER_ITEM',l.id,N'Giữ hàng cho đơn '+o.order_code,u.id,DATEADD(MINUTE,10,op.placed)
  FROM @allocationPlan a JOIN @linePlan l ON l.n=a.line_n JOIN @orderPlan op ON op.n=l.order_n JOIN dbo.orders o ON o.order_id=op.id JOIN @batchPlan b ON b.n=a.batch_n JOIN @variantPlan v ON v.n=l.variant_n JOIN @shopPlan s ON s.n=op.shop_n JOIN @userPlan u ON u.n=s.owner_n;
 INSERT dbo.inventory_transactions(variant_id,batch_id,transaction_type,quantity,reference_type,reference_id,note,created_by,created_at)
  SELECT v.id,b.id,CASE a.status WHEN 'SOLD' THEN 'SALE' ELSE 'RELEASE' END,-a.quantity,'ORDER_ITEM',l.id,
   CASE a.status WHEN 'SOLD' THEN N'Xuất bán cho đơn ' ELSE N'Hủy đơn, trả giữ cho ' END+o.order_code,u.id,DATEADD(MINUTE,CASE WHEN a.status='SOLD' THEN 90 ELSE 120 END,op.placed)
  FROM @allocationPlan a JOIN @linePlan l ON l.n=a.line_n JOIN @orderPlan op ON op.n=l.order_n JOIN dbo.orders o ON o.order_id=op.id JOIN @batchPlan b ON b.n=a.batch_n JOIN @variantPlan v ON v.n=l.variant_n JOIN @shopPlan s ON s.n=op.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE a.status IN('SOLD','RELEASED');
 INSERT dbo.inventory_transactions(variant_id,batch_id,transaction_type,quantity,reference_type,reference_id,note,created_by,created_at)
  SELECT v.id,b.id,'EXPIRED',-b.dispose_qty,'BATCH',b.id,N'Hủy một phần lô hết hạn, còn phần đang chờ xử lý',u.id,DATEADD(HOUR,-1,@now)
  FROM @batchPlan b JOIN @variantPlan v ON v.n=b.variant_n JOIN @productPlan p ON p.n=v.product_n JOIN @shopPlan s ON s.n=p.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE b.dispose_qty>0;
 UPDATE b SET quantity_on_hand=b.quantity_on_hand-COALESCE(a.sold,0)-p.dispose_qty,reserved_quantity=COALESCE(a.held,0),status=p.status,updated_at=@now
  FROM dbo.inventory_batches b JOIN @batchPlan p ON p.id=b.batch_id OUTER APPLY(SELECT SUM(CASE WHEN status='SOLD' THEN quantity ELSE 0 END) sold,SUM(CASE WHEN status='RESERVED' THEN quantity ELSE 0 END) held FROM dbo.stock_allocations WHERE batch_id=b.batch_id) a;
 INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at)
  SELECT created_by,CASE transaction_type WHEN 'EXPIRED' THEN 'DISPOSE' ELSE transaction_type END,'BATCH',batch_id,note,created_at FROM dbo.inventory_transactions WHERE transaction_type<>'RESTOCK';
 -- Final business states occur after historical orders, without discarding history.
 UPDATE p SET status=m.status,
  seller_hidden=CASE WHEN m.scenario='hidden' THEN 1 ELSE 0 END,moderation_blocked=CASE WHEN m.scenario='violation' THEN 1 ELSE 0 END,
  moderation_reason=CASE WHEN m.scenario='violation' THEN N'Chưa bổ sung chứng từ nguồn gốc sau hậu kiểm, ngừng nhận đơn mới.' END,
  approval_status=m.approval,updated_at=@now
  FROM dbo.products p JOIN @productPlan m ON m.id=p.product_id;
 UPDATE v SET status=m.status,updated_at=@now
  FROM dbo.product_variants v JOIN @variantPlan m ON m.id=v.variant_id JOIN @productPlan p ON p.n=m.product_n;
 INSERT dbo.product_review_history(product_id,decision,reviewed_by,note,created_at) SELECT id,'REJECTED',@admin,N'Thiếu chứng từ nguồn gốc sau hậu kiểm',@now FROM @productPlan WHERE scenario='violation';
 INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at)
  SELECT CASE WHEN p.scenario='violation' THEN @admin ELSE u.id END,CASE WHEN p.scenario='violation' THEN 'MODERATION_BLOCK' ELSE 'STATUS' END,'PRODUCT',p.id,
   CASE p.scenario WHEN 'hidden' THEN N'Tạm ẩn để cập nhật đợt hàng tiếp theo' WHEN 'violation' THEN N'Chờ bổ sung chứng từ nguồn hàng' ELSE N'Ngừng kinh doanh quy cách cũ, giữ lịch sử giao dịch' END,@now
  FROM @productPlan p JOIN @shopPlan s ON s.n=p.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE p.scenario IN('hidden','violation','archived');
 INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at)
  SELECT u.id,'BLOCK','BATCH',b.id,N'Khóa lô để chờ kiểm tra chất lượng',@now FROM @batchPlan b JOIN @variantPlan v ON v.n=b.variant_n JOIN @productPlan p ON p.n=v.product_n JOIN @shopPlan s ON s.n=p.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE b.status='BLOCKED';
 INSERT dbo.batch_markdowns(batch_id,price,start_date,end_date,status,note,created_by,created_at)
  SELECT b.id,v.price-p.markdown,DATEADD(DAY,-3,@today),DATEADD(DAY,b.expiry_days,@today),'ACTIVE',N'Ưu đãi đợt hàng dùng sớm, áp dụng riêng lô này',u.id,DATEADD(DAY,-3,@now)
  FROM @batchPlan b JOIN @variantPlan v ON v.n=b.variant_n JOIN @productPlan p ON p.n=v.product_n JOIN @shopPlan s ON s.n=p.shop_n JOIN @userPlan u ON u.n=s.owner_n WHERE p.markdown>0 AND b.lot=2;
 INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at) SELECT created_by,'MARKDOWN','BATCH',batch_id,note,created_at FROM dbo.batch_markdowns;
 -- Supporting customer data is intentionally small. Newly approved shop has no business activity.
 INSERT dbo.favorite_shops(user_id,shop_id,created_at) SELECT u.id,s.id,DATEADD(DAY,-5,@now) FROM @userPlan u JOIN @shopPlan s ON s.n=1+(u.n%2) WHERE u.role_name='ROLE_CUSTOMER';
 INSERT dbo.carts(user_id,created_at,updated_at) SELECT id,@now,@now FROM @userPlan WHERE role_name='ROLE_CUSTOMER';
 INSERT dbo.cart_items(cart_id,variant_id,quantity,unit_price_snapshot,added_at,updated_at)
  SELECT c.cart_id,v.id,1+u.n%2,v.price,@now,@now FROM @userPlan u JOIN dbo.carts c ON c.user_id=u.id JOIN @productPlan p ON p.n=CASE WHEN u.n%3=0 THEN 13 ELSE 1 END JOIN @variantPlan v ON v.product_n=p.n AND v.pack=1;
 DECLARE @plan BIGINT;
 INSERT dbo.subscription_plans(name,description,price,billing_period,benefits,created_at,updated_at) VALUES(N'Người bán tiêu chuẩn',N'Gói vận hành cửa hàng theo tháng',99000,'MONTHLY',N'Quản lý sản phẩm, kho và đơn hàng',@created,@created); SET @plan=SCOPE_IDENTITY();
 INSERT dbo.shop_subscriptions(shop_id,plan_id,start_at,end_at,status,auto_renew,created_at) SELECT id,@plan,DATEADD(DAY,-20,@now),DATEADD(DAY,10,@now),'ACTIVE',0,DATEADD(DAY,-20,@now) FROM @shopPlan WHERE n<=2;
 INSERT dbo.subscription_payments(shop_id,plan_id,amount,action,auto_renew,status,transaction_reference,reviewed_by,subscription_id,created_at,reviewed_at)
  SELECT s.shop_id,@plan,99000,'SUBSCRIBE',0,'PAID','SUB-'+CONVERT(VARCHAR(20),s.subscription_id),@admin,s.subscription_id,DATEADD(DAY,-20,@now),DATEADD(DAY,-20,@now) FROM dbo.shop_subscriptions s;
 INSERT dbo.loyalty_accounts(user_id,updated_at) SELECT id,@now FROM @userPlan WHERE role_name='ROLE_CUSTOMER';
 DECLARE @la BIGINT,@points INT,@balance INT;
 DECLARE earned CURSOR LOCAL FAST_FORWARD FOR SELECT la.loyalty_account_id,o.order_id,CONVERT(INT,FLOOR(o.subtotal/10000)),o.completed_at FROM dbo.orders o JOIN dbo.loyalty_accounts la ON la.user_id=o.customer_id WHERE o.status='COMPLETED' ORDER BY o.completed_at,o.order_id;
 OPEN earned; FETCH NEXT FROM earned INTO @la,@order,@points,@placed;
 WHILE @@FETCH_STATUS=0
 BEGIN
  UPDATE dbo.loyalty_accounts SET points_balance=points_balance+@points,total_earned=total_earned+@points WHERE loyalty_account_id=@la;
  SELECT @balance=points_balance FROM dbo.loyalty_accounts WHERE loyalty_account_id=@la;
  INSERT dbo.loyalty_transactions(loyalty_account_id,order_id,transaction_type,points,balance_after,note,created_at) VALUES(@la,@order,'EARN',@points,@balance,N'Tích điểm từ đơn hoàn tất',@placed);
  FETCH NEXT FROM earned INTO @la,@order,@points,@placed;
 END; CLOSE earned; DEALLOCATE earned;

 -- Reviews reference delivered purchases, never unrelated products or another merchant.
 INSERT dbo.reviews(user_id,product_id,order_item_id,rating,content,status,moderated_by,moderated_at,created_at,updated_at)
  SELECT o.customer_id,v.product_id,oi.order_item_id,CASE WHEN o.order_id%7=0 THEN 3 WHEN o.order_id%3=0 THEN 4 ELSE 5 END,
   CASE WHEN o.order_id%7=0 THEN N'Có vài quả mềm hơn dự kiến, cửa hàng đã hướng dẫn bảo quản.' WHEN o.order_id%3=0 THEN N'Đóng gói cẩn thận, đúng quy cách đã chọn.' ELSE N'Hàng tươi, giao đúng hẹn. Sẽ đặt lại cho gia đình.' END,
   CASE WHEN o.order_id%8=0 THEN 'PENDING' ELSE 'APPROVED' END,CASE WHEN o.order_id%8<>0 THEN @admin END,CASE WHEN o.order_id%8<>0 THEN DATEADD(HOUR,3,o.completed_at) END,
   DATEADD(HOUR,1,o.completed_at),DATEADD(HOUR,3,o.completed_at)
  FROM dbo.orders o JOIN dbo.shop_orders so ON so.order_id=o.order_id CROSS APPLY(SELECT TOP(1) * FROM dbo.order_items WHERE shop_order_id=so.shop_order_id ORDER BY order_item_id) oi JOIN dbo.product_variants v ON v.variant_id=oi.variant_id WHERE o.status='COMPLETED';
 INSERT dbo.reviews(user_id,shop_id,shop_order_id,rating,content,status,moderated_by,moderated_at,created_at,updated_at)
  SELECT o.customer_id,so.shop_id,so.shop_order_id,CASE WHEN o.order_id%4=0 THEN 4 ELSE 5 END,N'Cửa hàng phản hồi nhanh, giao đủ số lượng đã đặt.','APPROVED',@admin,DATEADD(HOUR,4,o.completed_at),DATEADD(HOUR,2,o.completed_at),DATEADD(HOUR,4,o.completed_at)
  FROM dbo.orders o JOIN dbo.shop_orders so ON so.order_id=o.order_id WHERE o.status='COMPLETED' AND o.order_id%3=0;
 INSERT dbo.notifications(user_id,type,title,content,entity_type,entity_id,created_at)
  SELECT m.user_id,'NEW_ORDER',N'Có đơn hàng mới',N'Đơn '+o.order_code+N' đang chờ cửa hàng xác nhận.','SHOP_ORDER',so.shop_order_id,o.placed_at
  FROM dbo.shop_orders so JOIN dbo.orders o ON o.order_id=so.order_id JOIN dbo.shop_members m ON m.shop_id=so.shop_id AND m.status='ACTIVE' WHERE so.status='PENDING';
 INSERT dbo.notifications(user_id,type,title,content,entity_type,entity_id,created_at,read_at)
  SELECT o.customer_id,CASE WHEN o.status='COMPLETED' THEN 'ORDER_COMPLETED' ELSE 'ORDER_CANCELLED' END,
   CASE WHEN o.status='COMPLETED' THEN N'Đơn hàng đã giao thành công' ELSE N'Đơn hàng đã hủy' END,
   N'Cập nhật trạng thái đơn '+o.order_code+N'. Bạn có thể xem chi tiết tại lịch sử đơn hàng.','ORDER',o.order_id,COALESCE(o.completed_at,o.cancelled_at),CASE WHEN o.order_id%4<>0 THEN DATEADD(MINUTE,15,COALESCE(o.completed_at,o.cancelled_at)) END
  FROM dbo.orders o WHERE o.status IN('COMPLETED','CANCELLED');
 INSERT dbo.notifications(user_id,type,title,content,entity_type,entity_id,created_at)
  SELECT a.delivery_staff_id,'DELIVERY_ASSIGNED',N'Chuyến giao hàng được phân công',N'Vui lòng kiểm tra địa chỉ nhận hàng và thời gian giao.','DELIVERY',a.delivery_id,a.assigned_at FROM dbo.delivery_assignments a WHERE a.status IN('ASSIGNED','ACCEPTED');
 INSERT dbo.notification_deliveries(notification_id,channel,status,sent_at) SELECT notification_id,'IN_APP','SENT',created_at FROM dbo.notifications;
 INSERT dbo.conversations(customer_id,shop_id,order_id,created_at,closed_at)
  SELECT o.customer_id,so.shop_id,o.order_id,DATEADD(MINUTE,2,o.placed_at),CASE WHEN o.status='COMPLETED' THEN DATEADD(HOUR,1,o.completed_at) END
  FROM dbo.orders o JOIN dbo.shop_orders so ON so.order_id=o.order_id WHERE o.order_id%11=0 AND o.status<>'CANCELLED';
 INSERT dbo.messages(conversation_id,sender_user_id,content,message_type,created_at,read_at)
  SELECT c.conversation_id,c.customer_id,N'Nhờ cửa hàng chọn quả chín vừa, gọi giúp mình trước khi giao.','TEXT',c.created_at,DATEADD(MINUTE,6,c.created_at) FROM dbo.conversations c;
 INSERT dbo.messages(conversation_id,sender_user_id,content,message_type,created_at,read_at)
  SELECT c.conversation_id,m.user_id,N'Cửa hàng đã nhận yêu cầu, sẽ kiểm tra độ chín khi đóng gói và liên hệ trước khi giao.','TEXT',DATEADD(MINUTE,8,c.created_at),CASE WHEN c.conversation_id%3<>0 THEN DATEADD(MINUTE,12,c.created_at) END FROM dbo.conversations c JOIN dbo.shop_members m ON m.shop_id=c.shop_id AND m.status='ACTIVE';

 IF (SELECT COUNT(*) FROM dbo.products)<>20 OR (SELECT COUNT(*) FROM dbo.orders)<>100 THROW 58010,N'Sai so luong demo.',1;
 IF EXISTS(SELECT 1 FROM @batchPlan p JOIN dbo.inventory_batches b ON b.batch_id=p.id WHERE b.quantity_on_hand<>p.physical_qty OR b.reserved_quantity<>p.reserved_qty) THROW 58011,N'Ton kho khong khop ke hoach.',1;
 IF EXISTS(SELECT 1 FROM dbo.inventory_batches b OUTER APPLY(SELECT SUM(quantity) qty FROM dbo.stock_allocations WHERE batch_id=b.batch_id AND status='RESERVED') a WHERE b.reserved_quantity<>COALESCE(a.qty,0)) THROW 58012,N'So giu khong khop don.',1;
 IF EXISTS(SELECT 1 FROM dbo.inventory_batches b OUTER APPLY(SELECT SUM(quantity) qty FROM dbo.inventory_transactions WHERE batch_id=b.batch_id AND transaction_type IN('RESTOCK','SALE','RETURN','ADJUSTMENT','EXPIRED','DAMAGED')) t WHERE b.quantity_on_hand<>COALESCE(t.qty,0)) THROW 58013,N'Ton thuc khong khop so kho.',1;
 IF EXISTS(SELECT 1 FROM dbo.order_items oi JOIN dbo.shop_orders so ON so.shop_order_id=oi.shop_order_id JOIN dbo.product_variants v ON v.variant_id=oi.variant_id JOIN dbo.products p ON p.product_id=v.product_id WHERE p.shop_id<>so.shop_id) THROW 58014,N'Dong don sai shop.',1;
 IF EXISTS(SELECT 1 FROM dbo.products p JOIN @shopPlan s ON s.id=p.shop_id WHERE s.n=3) OR EXISTS(SELECT 1 FROM dbo.shop_orders so JOIN @shopPlan s ON s.id=so.shop_id WHERE s.n=3) THROW 58015,N'Shop moi phai rong.',1;
 IF EXISTS(SELECT 1 FROM (
  SELECT SUM(CASE WHEN transaction_type IN('RESTOCK','SALE','RETURN','ADJUSTMENT','EXPIRED','DAMAGED') THEN quantity ELSE 0 END) OVER(PARTITION BY batch_id ORDER BY created_at,transaction_id ROWS UNBOUNDED PRECEDING) physical,
   SUM(CASE WHEN transaction_type IN('RESERVE','RELEASE','SALE') THEN quantity ELSE 0 END) OVER(PARTITION BY batch_id ORDER BY created_at,transaction_id ROWS UNBOUNDED PRECEDING) held
  FROM dbo.inventory_transactions) balance WHERE physical<0 OR held<0 OR held>physical) THROW 58016,N'Lich su kho khong hop le.',1;
 COMMIT TRANSACTION;
 SELECT N'DEMO_CREATED' result,@today business_date,(SELECT COUNT(*) FROM dbo.users) users,(SELECT COUNT(*) FROM dbo.products) products,(SELECT COUNT(*) FROM dbo.product_variants) skus,(SELECT COUNT(*) FROM dbo.inventory_batches) batches,(SELECT COUNT(*) FROM dbo.orders) orders,(SELECT COUNT(*) FROM dbo.order_items) lines,(SELECT COUNT(*) FROM dbo.stock_allocations) allocations;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
