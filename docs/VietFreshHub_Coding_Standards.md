# Coding Standards – VietFresh Hub (SWP391)

> Quy tắc áp dụng khi code hoặc giao việc cho AI agent. Stack đã xác nhận: **Java 21, Spring Boot 4.1.1, Spring MVC, Thymeleaf, Spring Security HTTP Session, SQL Server, Spring Data JPA/Hibernate, Maven, Lombok, Bootstrap/CSS/JavaScript**.
>
> Căn cứ: `pom.xml`, `SecurityConfig.java`, SRS/SDS trên Drive và các quyết định của nhóm trong bản khởi tạo 0.4. Các quy tắc bên dưới là chuẩn triển khai; không có nghĩa source hiện tại đã tuân thủ đầy đủ. Ví dụ code chỉ minh họa, phải đối chiếu entity, DTO, route và schema trước khi dùng.

---

## 1. Thư viện & kiểu dữ liệu

1. Giữ **Java 21 / Spring Boot 4.1.1** theo `pom.xml`. Không tự nâng/hạ phiên bản hoặc chuyển sang React/JSP/JDBC thuần.
2. Dùng namespace **Jakarta** cho Persistence, Validation, Servlet: `jakarta.persistence.*`, `jakarta.validation.*`, `jakarta.servlet.*`. Không đổi máy móc mọi package `javax.*` của Java SE.
3. Quản lý dependency trong **Maven**, không chép JAR vào `WEB-INF/lib`.
4. Ngày không có giờ, ví dụ hạn dùng theo ngày: dùng **`LocalDate`**. Thời điểm sự kiện: chọn `Instant`, `OffsetDateTime` hoặc `LocalDateTime` theo mapping DB và quy ước múi giờ đã thống nhất; không đổi kiểu dữ liệu tùy tiện.
5. Giá, phí, giảm giá và tổng tiền: **`BigDecimal`**, DB dùng `DECIMAL` phù hợp schema. Không dùng `float`/`double` cho tiền.
6. ID phải khớp schema hiện có. Giá trị nullable dùng wrapper; không bắt buộc đổi mọi primitive sang wrapper nếu nghiệp vụ không cần null.
7. Dùng constructor injection, ưu tiên **`@RequiredArgsConstructor` + `private final`**.

### Dependencies đã thấy trong `pom.xml`

| Dependency | Mục đích |
| --- | --- |
| `spring-boot-starter-webmvc` | Controller và Spring MVC |
| `spring-boot-starter-thymeleaf` | Render HTML phía server |
| `spring-boot-starter-data-jpa` | Repository, JPA/Hibernate |
| `spring-boot-starter-security` | Xác thực và phân quyền |
| `spring-boot-starter-validation` | Validation DTO |
| `mssql-jdbc` | Kết nối SQL Server |
| `lombok` | Giảm code lặp; cần annotation processing |
| `spring-boot-devtools` | Hỗ trợ development |
| `thymeleaf-extras-springsecurity6` | Đang khai báo; kiểm tra tương thích thực tế với stack hiện tại trước khi dùng các thuộc tính security |
| Các starter `*-test` cho JPA/Security/Thymeleaf/WebMVC | Hỗ trợ kiểm thử |

VNPAY, Cloudinary và Mail Server đã có trong thiết kế SDS; chưa chứng minh tích hợp đã chạy. Flyway, WebSocket/STOMP là lựa chọn bổ sung chưa được xác nhận từ `pom.xml`; không tự coi chúng là dependency có sẵn.

---

## 2. Entity, DTO & mapping

1. Entity dùng **`@Entity`**, `@Table`, `@Id` và mapping cột đúng schema. Chỉ dùng `IDENTITY` nếu cột DB thực sự sinh ID theo cơ chế đó.
2. Ưu tiên Lombok **`@Getter`, `@Setter`, `@NoArgsConstructor`** trên entity. Không áp dụng `@Data` hàng loạt: quan hệ hai chiều, lazy loading và field nhạy cảm cần kiểm soát `toString`/`equals`/`hashCode`.
3. **Được dùng quan hệ JPA** như `@ManyToOne`, `@OneToMany`, `@ManyToMany` khi phù hợp schema. Không áp dụng quy tắc “entity chỉ chứa FK ID” của mẫu JDBC.
4. Quy định fetch/cascade/orphan removal theo vòng đời dữ liệu. Không dùng `CascadeType.ALL` hoặc xóa dây chuyền một cách mặc định.
5. DTO nhận dữ liệu form; entity biểu diễn dữ liệu lưu trữ. Không bind nguyên entity từ request, đặc biệt với role, shop ID, owner ID, trạng thái, tổng tiền hoặc payment status.
6. Tên lớp theo SDS: entity số ít dạng PascalCase, DTO **`[Name]Request` / `[Name]Response`**.
7. Dùng `@Enumerated(EnumType.STRING)` khi cột và giá trị trong DB phù hợp. Không tự tạo giá trị enum mới khác CHECK constraint hoặc dữ liệu hiện có.

```java
// Mẫu DTO minh họa; bổ sung giới hạn theo SRS thực tế.
@Getter
@Setter
public class ProductRequest {
    @NotBlank
    private String name;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;
}
```

Mức giá tối thiểu/giới hạn độ dài cụ thể phải lấy từ yêu cầu đã chốt. Ví dụ trên không tự tạo business rule mới cho sản phẩm/biến thể của dự án.

---

## 3. Database & cấu hình

1. Dùng **Spring DataSource + JPA**; không tạo `DBContext` mở kết nối trong constructor, không tạo DAO kế thừa DBContext.
2. Cấu hình datasource trong `application.properties`/`application.yml`, lấy secret từ biến môi trường hoặc cấu hình local không commit.
3. **Không hardcode hoặc commit username/password/token/secret** vào Java, HTML, JavaScript hoặc tài liệu.
4. Không đổi schema để làm entity hết lỗi khi chưa kiểm tra thiết kế và dữ liệu hiện có.
5. Không dùng `ddl-auto=create`/`create-drop` trên DB dùng chung. `validate` chỉ kiểm tra, không tự tạo bảng; cần DDL phù hợp trước khi chạy.
6. `pom.xml` hiện chưa có Flyway. Nếu bổ sung migration, phải thêm dependency/cấu hình và thống nhất cách chạy; không giả định đã có.

```properties
# Cấu hình mẫu, đối chiếu profile và schema thực tế trước khi dùng.
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
```

Entity ID, FK, nullability, uniqueness và tiền tệ phải khớp SQL Server. Tài liệu mô tả bảng không thay thế một script DDL đã chạy thành công.

---

## 4. Repository & Service

1. Repository dùng **`JpaRepository<Entity, IdType>`**; tên **`[Name]Repository`**.
2. Service chứa nghiệp vụ; theo SDS dùng **`[Name]Service` / `[Name]ServiceImpl`**. Controller không gọi repository để bỏ qua service.
3. Dùng derived query/JPQL khi phù hợp; native SQL chỉ khi có nhu cầu rõ. Không ghép chuỗi từ input thành câu query.
4. Service kiểm tra quyền sở hữu dữ liệu: customer của đơn, shop của sản phẩm, nhân viên được phân công. Có role đúng chưa đủ để truy cập mọi bản ghi.
5. Đặt **`@Transactional`** ở phương thức service bao trọn đơn vị nghiệp vụ, ví dụ tạo đơn và giữ kho. Thiết kế rollback phù hợp, không bắt lỗi rồi trả thành công.
6. Không giữ transaction DB trong lúc chờ cổng thanh toán/email. Lưu trạng thái cần thiết rồi xử lý dịch vụ ngoài có cơ chế retry/đối soát phù hợp.
7. Truy vấn đọc có thể dùng `@Transactional(readOnly = true)` khi phù hợp. Giải quyết N+1 có chủ đích bằng projection/fetch plan; không chuyển toàn bộ quan hệ thành EAGER.
8. Lỗi nghiệp vụ dùng exception rõ nghĩa và xử lý tại controller/advice. Không nuốt lỗi DB bằng cách trả `null`, `false`, `-1` hoặc list rỗng như thể thao tác thành công.
9. Log bằng SLF4J/`@Slf4j`; không dùng `System.out.println` làm cơ chế logging của ứng dụng. Không log mật khẩu, token, giấy tờ hoặc toàn bộ entity nhạy cảm.

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    // Mẫu ranh giới transaction, không phải implementation checkout hoàn chỉnh.
    @Transactional
    public OrderResponse checkout(CheckoutRequest request, Long customerId) {
        // Kiểm tra quyền, giá, coupon và tồn kho ở server.
        // Giữ kho, tạo orders/shop_orders/order_items trong cùng giao dịch.
        // Trả DTO; không gọi mạng thanh toán khi đang giữ khóa DB.
        throw new UnsupportedOperationException("Implement theo SRS và schema thực tế");
    }
}
```

---

## 5. Spring MVC Controller & phân trang

1. Trang HTML dùng **`@Controller`**. Chỉ dùng `@RestController`/`@ResponseBody` cho endpoint JSON thực sự cần.
2. Mapping bằng **`@RequestMapping`, `@GetMapping`, `@PostMapping`**; không dùng `@WebServlet` hoặc `action` switch kiểu Servlet/JSP của mẫu cũ.
3. Controller nhận input, validation, gọi service, chuẩn bị `Model` và trả view/redirect. Không chứa logic dài về kho, thanh toán hoặc phân công.
4. Dùng **`@Valid @ModelAttribute`**, đặt `BindingResult` ngay sau DTO được validate. Khi có lỗi, trả lại form và nạp đủ dữ liệu dropdown.
5. GET dùng đọc; thao tác thay đổi dữ liệu dùng POST/HTTP method phù hợp. Không xóa, khóa tài khoản hoặc đổi trạng thái bằng link GET.
6. Sau POST thành công, ưu tiên **Post/Redirect/Get**, dùng flash message khi cần.
7. Route phải bám cấu hình `/admin/**`, `/manager/**`, `/delivery/**`, `/customer/**`; không tự đổi prefix mà bỏ quên SecurityConfig.

```java
@PostMapping("/manager/products")
public String create(@Valid @ModelAttribute("form") ProductRequest form,
                     BindingResult errors,
                     RedirectAttributes redirectAttributes) {
    if (errors.hasErrors()) {
        return "manager/products/form";
    }
    // Gọi service đã kiểm tra người dùng/shop hiện tại; không tin shopId từ form.
    productService.create(form);
    redirectAttributes.addFlashAttribute("success", "Lưu sản phẩm thành công");
    return "redirect:/manager/products";
}
```

### Phân trang + filter

- UI có thể dùng `page=1` làm trang đầu; **Spring Data dùng chỉ số trang bắt đầu từ 0**. Chuyển đổi ở một nơi rõ ràng.
- Validate `page`, giới hạn `size` và whitelist trường sort; giá trị mặc định phải thống nhất trong module.
- Query trả `Page<DTO>` hoặc dữ liệu phân trang tương đương; không tải toàn bộ DB rồi cắt list trên Java.
- Model gồm dữ liệu, trang hiện tại, tổng trang và giá trị filter. Giữ filter khi chuyển trang; xử lý tập kết quả rỗng.

---

## 6. Thymeleaf & giao diện

1. View trong **`src/main/resources/templates/`**; CSS/JS/ảnh tĩnh trong **`src/main/resources/static/`**.
2. Dùng **Thymeleaf + HTML + Bootstrap/CSS**; layout chung qua fragment `th:replace`.
3. Form dùng `th:object`, `th:field`, `th:errors` và `th:action`; route dùng `@{...}` để xử lý context path.
4. Hiển thị nội dung người dùng bằng `th:text`. Không dùng `th:utext` với nội dung chưa được kiểm soát.
5. Form POST phải gửi CSRF token đúng cấu hình; kiểm tra HTML render và request thực tế. AJAX gửi token trong header phù hợp, không tắt CSRF toàn hệ thống để né lỗi.
6. Không chèn chuỗi người dùng trực tiếp vào JavaScript. Khi cần dữ liệu server trong script, dùng Thymeleaf JavaScript inlining có serialize phù hợp hoặc data attribute.
7. Label gắn đúng input, hiển thị validation tiếng Việt, giữ giá trị hợp lệ khi submit lỗi. Kiểm tra UI từ 360–1920 px theo SRS.

```html
<form th:action="@{/manager/products}" th:object="${form}" method="post">
    <label for="name">Tên sản phẩm</label>
    <input id="name" type="text" th:field="*{name}" class="form-control">
    <small th:errors="*{name}" class="text-danger"></small>
    <button type="submit" class="btn btn-primary">Lưu</button>
</form>
```

### Pagination URL

```html
<!-- i là số trang UI; controller chuyển sang chỉ số 0-based của Spring Data. -->
<a th:href="@{/customer/products(page=${i},keyword=${keyword},categoryId=${categoryId})}"
   th:text="${i}"></a>
```

Các URL trên chỉ minh họa. Phải kiểm tra mapping thực tế và bảo đảm Guest vẫn truy cập được các trang browsing công khai theo SRS.

---

## 7. Security & dữ liệu nhạy cảm

1. Cấu hình đã gửi dùng **HTTP session**, lưu context qua `HttpSessionSecurityContextRepository`; không tự chuyển sang JWT.
2. `formLogin` mặc định đang bị tắt. Khi sửa login, kiểm tra luồng xác thực tùy chỉnh, lưu SecurityContext, bảo vệ session fixation và đăng nhập lại qua request tiếp theo; không chỉ set một attribute tùy ý rồi coi là đã đăng nhập với Spring Security.
3. Role theo `hasRole` hiện tại: **`ADMIN`, `STORE_MANAGER`, `DELIVERY_STAFF`, `CUSTOMER`**. GrantedAuthority phải được chuyển đổi nhất quán với cách kiểm tra role; không tự thêm `SHOP_OWNER` thay `STORE_MANAGER`.
4. Backend kiểm tra cả role và quyền trên bản ghi. Ẩn nút ở giao diện không thay thế phân quyền.
5. Admin duyệt/gỡ sản phẩm theo SRS; không mặc định cấp quyền tạo/sửa sản phẩm như Store Manager.
6. Mật khẩu hash bằng **BCrypt**. Thông báo đăng nhập lỗi không tiết lộ tài khoản có tồn tại hay không.
7. Giấy tờ seller chỉ PDF/JPG/PNG, tối đa **5 MB/tệp** theo SRS; kiểm tra loại, kích thước và quyền truy cập. Không đặt tài liệu xác minh vào static/public URL.
8. VNPAY: chỉ cập nhật payment sau xác minh phía server; kiểm tra reference, số tiền và callback trùng. Return URL trên trình duyệt không đủ để kết luận đã trả tiền.

---

## 8. Quy tắc nghiệp vụ đã chốt

| Hạng mục | Quy tắc |
| --- | --- |
| Phân công thành viên | Giữ TV1–TV5, không tự ánh xạ tên người |
| Shop Profile & Availability | Dùng **FE-03.5**; giữ trách nhiệm TV1 theo phân công hiện hành |
| Subscription | Gói dịch vụ dành cho cửa hàng, không phải gói giao trái cây định kỳ cho khách |
| Delivery | Hệ thống **tự phân công nhân viên đang rảnh**, sau đó ghi nhận trạng thái nhân viên cập nhật |
| Theo dõi giao hàng | Đã lấy hàng, đang giao, thành công/thất bại, lý do và lịch sử; vận chuyển diễn ra ngoài phần mềm |
| Bằng chứng giao hàng | **Không triển khai** upload ảnh/chữ ký/OTP làm bằng chứng giao hàng; không tự DROP bảng cũ chỉ vì bỏ chức năng |
| Tích hợp vận chuyển | Không tự thêm GPS, tối ưu tuyến hoặc đặt dịch vụ hãng vận chuyển |

### Các ràng buộc triển khai

- Phân biệt **rảnh/bận của nhân viên** với **trạng thái giao hàng**. Kiểm soát gán đồng thời để không phân công cùng một nguồn lực vượt quy tắc đã chọn.
- Đề xuất: không có nhân viên phù hợp thì chờ phân công. Tiêu chí chọn giữa nhiều người rảnh, phạm vi shop/khu vực, sức chứa nhiệm vụ và thời điểm chuyển lại rảnh cần được xác nhận trước khi code cố định.
- Bước `Accept Delivery Assignment` trong nguồn cũ chưa được chốt giữ/bỏ; không tự coi là bắt buộc hoặc đã bị xóa.
- Checkout tính lại giá, coupon và tồn kho trên server; tạo đơn/giữ kho phải nhất quán trong transaction.
- Đơn hàng, payment và delivery có vòng đời riêng. COD chưa được coi là đã thu tiền ngay khi đặt đơn.
- Callback/job chạy lại không được ghi nhận tiền, kho, điểm hoặc kích hoạt subscription thêm lần nữa.
- Không suy đoán phí, thời hạn, quy tắc hoàn tiền, quyền bán khi hết gói hoặc điều kiện hủy. Nếu nguồn mâu thuẫn, nêu rõ và hỏi người phụ trách.

---

## 9. Package map

Package đã thấy trong source là **`VietFreshHub.config`**; giữ convention của repository, không đổi hàng loạt sang tên khác. Maven groupId **`com.VietFresh_Hub`** không bắt buộc trùng Java package.

```text
src/main/java/VietFreshHub/
├── config       → SecurityConfig và cấu hình Spring/tích hợp
├── controller   → *Controller, xử lý HTTP và view
├── service      → *Service, *ServiceImpl, nghiệp vụ và transaction
├── repository   → *Repository, truy vấn JPA
├── entity       → JPA entities theo schema
├── dto          → *Request, *Response
├── exception    → Exception nghiệp vụ, handler/advice
└── util         → Helper dùng chung, không chứa secret kết nối

src/main/resources/
├── templates/
│   ├── fragments/
│   ├── auth/
│   ├── customer/
│   ├── manager/
│   ├── delivery/
│   └── admin/
├── static/
│   ├── css/
│   ├── js/
│   └── images/
└── application.properties hoặc application.yml
```

Cây này là quy ước theo SDS và source đã cung cấp, chưa phải kiểm kê toàn bộ repo. Khi code, kiểm tra cấu trúc thực tế trước khi tạo thêm package.

---

## 10. Checklist PR / commit & AI agent

- [ ] Đã đọc source liên quan và đối chiếu SRS/SDS; không chỉ dựa vào tên chức năng.
- [ ] Giữ Boot 4.1.1 / Java 21 / MVC / Thymeleaf / SQL Server / HTTP session.
- [ ] Jakarta đúng phạm vi; dependency qua Maven; constructor injection.
- [ ] Entity đúng schema; DTO có validation; không bind field nhạy cảm từ form.
- [ ] Tiền dùng BigDecimal; kiểu ngày/giờ và ID khớp dữ liệu.
- [ ] Nghiệp vụ ở service; transaction/rollback và truy cập dữ liệu đúng phạm vi.
- [ ] Form có CSRF; thao tác thay đổi không chạy bằng GET.
- [ ] UI dùng fragment, escape nội dung, giữ filter/giá trị hợp lệ khi lỗi.
- [ ] Delivery tự phân công và ghi nhận trạng thái; không thêm bằng chứng giao hàng.
- [ ] Không nuốt exception, không log secret hoặc commit thông tin đăng nhập.
- [ ] Chạy build và test liên quan; ghi đúng những gì đã kiểm tra, không tự báo PASS.
- [ ] Theo SRS, mục tiêu unit test bao phủ tối thiểu 60% business logic tầng service; thống nhất cách đo và lưu report, không tự bịa tỷ lệ.
- [ ] Không nâng dependency, đổi schema hoặc tái cấu trúc ngoài phạm vi task.
- [ ] Ghi FE/UC liên quan, thay đổi dữ liệu và điểm còn thiếu trong PR.

**Hướng dẫn cho AI agent:** Đọc file này cùng `VietFreshHub_Project_Initialization.md` nếu có, sau đó kiểm tra source thực tế. Phân biệt cấu hình đang tồn tại với quy tắc cần triển khai. Link Google Drive không bảo đảm agent đọc được: nếu không truy cập được tài liệu, báo rõ và dùng bản local do người dùng cung cấp. Không coi phần “đề xuất/chưa chốt” là yêu cầu đã duyệt. Không sửa tài liệu hoặc code ngoài phạm vi được giao.

**Tham chiếu kỹ thuật:** [Transactionality — Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html); [CSRF — Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html); [Thymeleaf 3.1](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf).
