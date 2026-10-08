package VietFreshHub.config;

import VietFreshHub.Auth.entity.*;
import VietFreshHub.Auth.repository.*;
import VietFreshHub.Cart.entity.*;
import VietFreshHub.Cart.repository.*;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.repository.*;
import VietFreshHub.Shop.entity.Shop;
import VietFreshHub.Shop.repository.ShopRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    public static final String CUSTOMER_EMAIL = "customer.demo@vietfreshhub.example";
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final SellerApplicationRepository applications;
    private final ShopRepository shops;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final InventoryBatchRepository batches;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final VietFreshHub.Payment.repository.PaymentMethodRepository paymentMethods;
    private final Map<String, Category> demoCategories = new LinkedHashMap<>();
    @Value("${app.seed.password:}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if(paymentMethods.findByMethodCode("COD").isEmpty()) {
            var cod=new VietFreshHub.Payment.entity.PaymentMethod(); cod.setMethodCode("COD");
            cod.setName("Thanh toán khi nhận hàng (COD)"); paymentMethods.save(cod);
        }
        if (users.existsByEmailIgnoreCase(CUSTOMER_EMAIL)) {
            log.info("Demo seed already exists; preserving accounts, prices, stock and carts.");
            return;
        }
        if (seedPassword == null || seedPassword.length() < 12) {
            throw new IllegalStateException("Set APP_SEED_PASSWORD to a test password of at least 12 characters before seeding.");
        }
        // One transaction: partial fixtures are rolled back. Never merge or overwrite unrelated records.
        Map<String, Role> roleMap = new HashMap<>();
        for (String name : List.of("CUSTOMER", "STORE_MANAGER", "ADMIN", "DELIVERY_STAFF")) {
            roleMap.put(name, roles.findByRoleName("ROLE_" + name).orElseGet(() -> {
                Role role = new Role(); role.setRoleName("ROLE_" + name);
                role.setDescription("Quyền " + name); return roles.save(role);
            }));
        }
        String hash = passwordEncoder.encode(seedPassword);
        User buyer = createUser(CUSTOMER_EMAIL, "Khách hàng giỏ mẫu", "ACTIVE", roleMap.get("CUSTOMER"), hash);
        User emptyBuyer = createUser("empty.demo@vietfreshhub.example", "Khách hàng giỏ trống", "ACTIVE", roleMap.get("CUSTOMER"), hash);
        createUser("blocked.demo@vietfreshhub.example", "Khách hàng bị khóa", "BLOCKED", roleMap.get("CUSTOMER"), hash);
        User admin = createUser("admin.demo@vietfreshhub.example", "Quản trị kiểm thử", "ACTIVE", roleMap.get("ADMIN"), hash);
        createUser("delivery.demo@vietfreshhub.example", "Nhân viên giao hàng kiểm thử", "ACTIVE", roleMap.get("DELIVERY_STAFF"), hash);
        User managerA = createUser("manager-a.demo@vietfreshhub.example", "Quản lý Vườn Nhà", "ACTIVE", roleMap.get("STORE_MANAGER"), hash);
        User managerB = createUser("manager-b.demo@vietfreshhub.example", "Quản lý Miệt Vườn", "ACTIVE", roleMap.get("STORE_MANAGER"), hash);
        User managerClosed = createUser("manager-closed.demo@vietfreshhub.example", "Quản lý cửa hàng tạm đóng", "ACTIVE", roleMap.get("STORE_MANAGER"), hash);
        User applicant = createUser("applicant.demo@vietfreshhub.example", "Người bán chờ duyệt", "ACTIVE", roleMap.get("CUSTOMER"), hash);

        Shop shopA = createShop("[DEMO] Vườn Nhà Fresh", managerA, admin, true, "OPEN");
        Shop shopB = createShop("[DEMO] Miệt Vườn Fruit", managerB, admin, true, "OPEN");
        Shop closedShop = createShop("[DEMO] Fresh Corner", managerClosed, admin, true, "CLOSED");
        Shop hiddenShop = createShop("[DEMO] Cửa hàng chờ duyệt", applicant, admin, false, "OPEN");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Map<String, ProductVariant> sample = new HashMap<>();
        String[] names = {"Bưởi da xanh", "Sầu riêng", "Xoài cát", "Vải thiều"};
        String[] slugs = {"buoi", "sau-rieng", "xoai", "vai"};
        int[] prices = {79000, 229000, 65000, 89000};
        for (int fruit = 0; fruit < names.length; fruit++) {
            Category category = new Category(); category.setName("[DEMO] " + names[fruit]);
            category.setSlug("vf-demo-category-" + slugs[fruit]); entityManager.persist(category);
            demoCategories.put(slugs[fruit], category);
        }
        int index = 0;
        for (Shop shop : List.of(shopA, shopB)) {
            String key = shop == shopA ? "A" : "B";
            for (int fruit = 0; fruit < names.length; fruit++) {
                for (int pack = 0; pack < 2; pack++) {
                    Product product = createProduct(shop, names[fruit] + (pack == 0 ? " tuyển chọn" : " giỏ quà"),
                            "vf-demo-" + key.toLowerCase() + "-" + slugs[fruit] + "-" + pack,
                            "Trái cây đóng gói theo phân loại. Cửa hàng: " + shop.getShopName() + ".", admin);
                    for (int weight : List.of(1, 3)) {
                        String sku = "VF-DEMO-" + key + "-" + fruit + "-" + pack + "-" + weight;
                        int stock = 18 + (index++ % 12);
                        int reserved = 0;
                        String status = "ACTIVE";
                        LocalDate expiry = today.plusDays(10 + index % 10);
                        if (key.equals("B") && fruit == 1 && pack == 0 && weight == 1) stock = 2;
                        if (key.equals("A") && fruit == 2 && pack == 0 && weight == 1) { stock = 0; status = "OUT_OF_STOCK"; }
                        if (key.equals("B") && fruit == 3 && pack == 0 && weight == 1) status = "INACTIVE";
                        if (key.equals("B") && fruit == 0 && pack == 1 && weight == 1) expiry = today.minusDays(1);
                        if (key.equals("B") && fruit == 2 && pack == 0 && weight == 1) { stock = 12; reserved = 4; }
                        ProductVariant variant = createVariant(product, sku, (pack == 0 ? "Hộp " : "Giỏ ") + weight + " kg",
                                BigDecimal.valueOf((long) prices[fruit] * weight + (pack == 1 ? 20000 : 0)), status);
                        createBatch(variant, "VF-DEMO-BATCH-" + sku, stock, reserved, expiry);
                        sample.put(key + "-" + fruit + "-" + pack + "-" + weight, variant);
                    }
                }
            }
        }
        Product closed = createProduct(closedShop, "Bưởi da xanh hộp gia đình", "vf-demo-closed-buoi",
                "Cửa hàng tạm đóng; sản phẩm vẫn xem được, hiện chưa nhận đơn mới.", admin);
        ProductVariant closedVariant = createVariant(closed, "VF-DEMO-CLOSED", "Hộp 2 kg", new BigDecimal("159000"), "ACTIVE");
        createBatch(closedVariant, "VF-DEMO-CLOSED-BATCH", 20, 0, today.plusDays(12));
        Product hidden = createProduct(hiddenShop, "Xoài cát cửa hàng chờ duyệt", "vf-demo-hidden-xoai",
                "Dữ liệu kiểm thử quyền hiển thị cửa hàng.", admin);
        ProductVariant hiddenVariant = createVariant(hidden, "VF-DEMO-HIDDEN", "Hộp 1 kg", new BigDecimal("65000"), "ACTIVE");
        createBatch(hiddenVariant, "VF-DEMO-HIDDEN-BATCH", 20, 0, today.plusDays(12));

        ProductVariant changedPrice = sample.get("A-0-0-1");
        createBatch(changedPrice, "VF-DEMO-EXPIRED-EXCLUDED", 100, 0, today.minusDays(3));
        createBatch(changedPrice, "VF-DEMO-UNDATED-EXCLUDED", 50, 0, null);
        Cart cart = new Cart(); cart.setUser(buyer); carts.save(cart);
        createCartItem(cart, changedPrice, 2, new BigDecimal("69000"));
        createCartItem(cart, sample.get("B-2-0-1"), 1, null);
        createCartItem(cart, sample.get("B-1-0-1"), 5, null);
        createCartItem(cart, sample.get("A-2-0-1"), 1, null);
        createCartItem(cart, sample.get("B-3-0-1"), 1, null);
        createCartItem(cart, sample.get("B-0-1-1"), 1, null);
        createCartItem(cart, closedVariant, 1, null);
        createAddress(buyer, "Nhà riêng", "Đường kiểm thử 01", true);
        createAddress(buyer, "Văn phòng", "Đường kiểm thử 02", false);
        createAddress(emptyBuyer, "Nhà riêng", "Đường kiểm thử 03", true);
        entityManager.flush();
        log.info("Created demo dataset: 9 accounts, 4 shops, 4 categories, 18 products, 34 variants, 36 batches, 7 cart items, 3 addresses. Passwords are not logged.");
    }

    private User createUser(String email, String fullName, String status, Role role, String hash) {
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalStateException("Demo account already exists: " + email + ". No account has been overwritten.");
        User user = new User(); user.setEmail(email); user.setFullName(fullName); user.setStatus(status);
        user.setPasswordHash(hash); user.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        users.save(user);
        UserRoleId id = new UserRoleId(); id.setUserId(user.getUserId()); id.setRoleId(role.getRoleId());
        UserRole grant = new UserRole(); grant.setId(id); grant.setUser(user); grant.setRole(role);
        userRoles.save(grant); user.getUserRoles().add(grant);
        return user;
    }
    private Shop createShop(String name, User owner, User admin, boolean approved, String availability) {
        SellerApplication application = new SellerApplication(); application.setUser(owner); application.setBusinessName(name);
        application.setStatus(approved ? SellerApplicationStatus.APPROVED : SellerApplicationStatus.PENDING);
        if (approved) { application.setReviewedBy(admin); application.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0)); }
        applications.save(application);
        Shop shop = new Shop(); shop.setShopName(name); shop.setApplicationId(application.getApplicationId());
        shop.setDescription("Cửa hàng giả định dùng để kiểm thử VietFresh Hub."); shop.setStatus("ACTIVE");
        shop.setAvailabilityStatus(availability);
        return shops.save(shop);
    }
    private Product createProduct(Shop shop, String name, String slug, String description, User admin) {
        Product product = new Product(); product.setShop(shop); product.setName(name); product.setSlug(slug);
        product.setDescription(description); product.setStatus("ACTIVE"); product.setApprovalStatus("APPROVED");
        product.setApprovedBy(admin.getUserId()); product.setApprovedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        demoCategories.entrySet().stream().filter(entry -> slug.contains(entry.getKey())).findFirst()
                .ifPresent(entry -> product.getCategories().add(entry.getValue()));
        return products.save(product);
    }
    private ProductVariant createVariant(Product product, String sku, String name, BigDecimal price, String status) {
        ProductVariant variant = new ProductVariant(); variant.setProduct(product); variant.setSku(sku);
        variant.setVariantName(name); variant.setPrice(price); variant.setStatus(status); variant.setLowStockThreshold(5);
        return variants.save(variant);
    }
    private void createBatch(ProductVariant variant, String code, int stock, int reserved, LocalDate expiry) {
        InventoryBatch batch = new InventoryBatch(); batch.setVariant(variant); batch.setBatchCode(code);
        batch.setQuantityOnHand(stock); batch.setReservedQuantity(reserved); batch.setExpiryDate(expiry);
        batches.save(batch);
    }
    private void createCartItem(Cart cart, ProductVariant variant, int quantity, BigDecimal oldPrice) {
        CartItem item = new CartItem(); item.setCart(cart); item.setVariant(variant); item.setQuantity(quantity);
        item.setUnitPriceSnapshot(oldPrice == null ? variant.getPrice() : oldPrice);
        cartItems.save(item); cart.getItems().add(item);
    }
    private void createAddress(User user, String label, String line, boolean defaultAddress) {
        Address address = new Address(); address.setUser(user); address.setRecipientName(user.getFullName() + " — " + label);
        address.setPhone("0900000000"); address.setProvince("Hà Nội"); address.setDistrict("Cầu Giấy"); address.setWard("Dịch Vọng");
        address.setAddressLine(line); address.setIsDefault(defaultAddress); entityManager.persist(address);
    }
}
