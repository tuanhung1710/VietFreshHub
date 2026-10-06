package VietFreshHub.config;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.entity.Cart;
import VietFreshHub.Cart.entity.CartItem;
import VietFreshHub.Cart.repository.CartItemRepository;
import VietFreshHub.Cart.repository.CartRepository;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.entity.Product;
import VietFreshHub.Product.entity.ProductVariant;
import VietFreshHub.Product.repository.ProductRepository;
import VietFreshHub.Product.repository.ProductVariantRepository;
import VietFreshHub.Shop.entity.Shop;
import VietFreshHub.Shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Checking & Initializing Data Seed for VietFresh Hub...");

        // 1. Ensure Default User Exists
        User defaultUser = userRepository.findById(1L).orElseGet(() -> {
            User user = new User();
            user.setEmail("customer@vietfreshhub.com");
            user.setPasswordHash("encoded_password");
            user.setFullName("Khách Hàng VietFresh");
            user.setPhone("0901234567");
            user.setStatus("ACTIVE");
            return userRepository.save(user);
        });

        // 1.1. Ensure Default Shop Exists
        Shop defaultShop = shopRepository.findByShopName("VietFresh Official Store").orElseGet(() -> {
            Shop s = new Shop();
            s.setShopName("VietFresh Official Store");
            s.setDescription("Cửa hàng nông sản tươi sạch chính hãng");
            s.setStatus("ACTIVE");
            return shopRepository.save(s);
        });

        // 2. Create distinct Products
        Product pBuoi = createProductIfMissing("Bưởi da xanh Bến Tre", "buoi-da-xanh-ben-tre", "buoi", "Bưởi da xanh tép hồng mọng nước, vị ngọt thanh tự nhiên. Thu hoạch trực tiếp từ trang trại chuẩn VietGAP tại Bến Tre.", "/images/buoi-da-xanh.jpg", defaultShop);
        Product pSauRieng = createProductIfMissing("Sầu riêng", "sau-rieng", "sau", "Sầu riêng cơm vàng hạt lép, béo ngậy chín cây tự nhiên.", "/images/sau-rieng.jpg", defaultShop);
        Product pXoai = createProductIfMissing("Xoài cát", "xoai-cat", "xoai", "Xoài cát thơm ngọt đậm đà, vỏ mỏng mịn chuẩn trái cây hữu cơ.", "/images/xoai-cat.png", defaultShop);
        Product pVai = createProductIfMissing("Vải thiều Bắc Giang", "vai-thieu-bac-giang", "vai", "Vải thiều Bắc Giang quả to tròn, vỏ đỏ mọng, mọt nước.", "/images/vai-thieu.png", defaultShop);

        // 3. Create Variants for each Product
        // Product 1: Bưởi Da Xanh
        ProductVariant vBuoi1 = createVariantIfMissing(pBuoi, "VF-BUOI-1KG", "Hộp 1kg (Tép Hồng Mọng Nước)", new BigDecimal("150000.00"), "ACTIVE");
        ProductVariant vBuoi2 = createVariantIfMissing(pBuoi, "VF-BUOI-5KG", "Thùng 5kg (Tiết Kiệm Gia Đình)", new BigDecimal("680000.00"), "ACTIVE");
        ProductVariant vBuoi3 = createVariantIfMissing(pBuoi, "VF-BUOI-3KG", "Túi 3kg Biếu Tặng (Tạm Hết Hàng)", new BigDecimal("420000.00"), "OUT_OF_STOCK");
        ProductVariant vBuoi4 = createVariantIfMissing(pBuoi, "VF-BUOI-10KG", "Hộp 10kg Special (Đã Hết Vụ)", new BigDecimal("1200000.00"), "INACTIVE");

        // Product 2: Sầu Riêng
        ProductVariant vSauRieng1 = createVariantIfMissing(pSauRieng, "VF-SAURIENG-1KG", "Hộp 1kg (Cơm Vàng Hạt Lép)", new BigDecimal("220000.00"), "ACTIVE");
        ProductVariant vSauRieng2 = createVariantIfMissing(pSauRieng, "VF-SAURIENG-5KG", "Thùng 5kg (Nguyên Trái Chín Cây)", new BigDecimal("950000.00"), "ACTIVE");
        ProductVariant vSauRieng3 = createVariantIfMissing(pSauRieng, "VF-SAURIENG-500G", "Khay 500g Tách Múi (Tạm Hết Hàng)", new BigDecimal("120000.00"), "OUT_OF_STOCK");

        // Product 3: Xoài Cát
        ProductVariant vXoai1 = createVariantIfMissing(pXoai, "VF-XOAI-1KG", "Hộp 1kg (Trái Lớn 400g+)", new BigDecimal("110000.00"), "OUT_OF_STOCK");
        ProductVariant vXoai2 = createVariantIfMissing(pXoai, "VF-XOAI-3KG", "Thùng 3kg (Đã Hết Mùa)", new BigDecimal("300000.00"), "INACTIVE");

        // Product 4: Vải Thiều Bắc Giang
        ProductVariant vVai1 = createVariantIfMissing(pVai, "VF-VAITHIEU-1KG", "Túi 1kg (Đã Ngưng Bán / Hết Vụ)", new BigDecimal("90000.00"), "INACTIVE");

        // 4. Ensure Inventory Batches Exist
        createBatchIfMissing(vBuoi1, 15, 0);       // 15 available stock
        createBatchIfMissing(vBuoi2, 5, 0);        // 5 available stock
        createBatchIfMissing(vBuoi3, 0, 0);        // Out of stock
        createBatchIfMissing(vBuoi4, 0, 0);        // Inactive

        createBatchIfMissing(vSauRieng1, 10, 0);   // 10 available stock
        createBatchIfMissing(vSauRieng2, 3, 0);    // 3 available stock (low stock)
        createBatchIfMissing(vSauRieng3, 0, 0);   // Out of stock

        createBatchIfMissing(vXoai1, 0, 0);        // Out of stock
        createBatchIfMissing(vXoai2, 0, 0);        // Inactive

        createBatchIfMissing(vVai1, 0, 0);         // Inactive

        // 5. Ensure Active Cart and Cart Item Exist for View Cart Scenario
        Cart activeCart = cartRepository.findByUserUserIdAndStatus(defaultUser.getUserId(), "ACTIVE")
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(defaultUser);
                    cart.setStatus("ACTIVE");
                    return cartRepository.save(cart);
                });

        if (cartItemRepository.findByCartCartIdAndVariantVariantId(activeCart.getCartId(), vBuoi1.getVariantId()).isEmpty()) {
            CartItem item = new CartItem();
            item.setCart(activeCart);
            item.setVariant(vBuoi1);
            item.setQuantity(2);
            item.setUnitPriceSnapshot(vBuoi1.getPrice());
            cartItemRepository.save(item);
            log.info("Seeded initial CartItem in Cart id={}", activeCart.getCartId());
        }

        log.info("Data Seed Initialized Successfully!");
    }

    private Product createProductIfMissing(String name, String slug, String keyword, String description, String imageUrl, Shop shop) {
        List<Product> allProducts = productRepository.findAll();
        Product p = allProducts.stream()
                .filter(prod -> prod.getSlug() != null && prod.getSlug().equalsIgnoreCase(slug))
                .findFirst()
                .orElseGet(() -> allProducts.stream()
                        .filter(prod -> (prod.getSlug() != null && prod.getSlug().toLowerCase().contains(keyword))
                                     || (prod.getName() != null && prod.getName().toLowerCase().contains(keyword))
                                     || (prod.getName() != null && prod.getName().toLowerCase().contains(name.toLowerCase())))
                        .findFirst()
                        .orElseGet(() -> {
                            Product newP = new Product();
                            newP.setShop(shop);
                            return newP;
                        }));

        p.setName(name);
        p.setSlug(slug);
        p.setDescription(description);
        p.setImageUrl(imageUrl);
        p.setStatus("ACTIVE");
        p.setShop(shop);
        return productRepository.save(p);
    }

    private ProductVariant createVariantIfMissing(Product parentProduct, String sku, String name, BigDecimal price, String status) {
        return productVariantRepository.findAll().stream()
                .filter(v -> (v.getSku() != null && v.getSku().equalsIgnoreCase(sku)) || (name.equalsIgnoreCase(v.getVariantName()) && v.getProduct().getProductId().equals(parentProduct.getProductId())))
                .findFirst()
                .orElseGet(() -> {
                    ProductVariant variant = new ProductVariant();
                    variant.setProduct(parentProduct);
                    variant.setSku(sku);
                    variant.setVariantName(name);
                    variant.setPrice(price);
                    variant.setStatus(status);
                    return productVariantRepository.save(variant);
                });
    }

    private void createBatchIfMissing(ProductVariant variant, int onHand, int reserved) {
        if (inventoryBatchRepository.getAvailableStockByVariantId(variant.getVariantId()) == 0 && onHand > 0) {
            InventoryBatch batch = new InventoryBatch();
            batch.setVariant(variant);
            batch.setBatchCode("BATCH-" + (variant.getSku() != null ? variant.getSku() : variant.getVariantId()));
            batch.setQuantityOnHand(onHand);
            batch.setReservedQuantity(reserved);
            batch.setExpiryDate(LocalDate.now().plusMonths(1));
            inventoryBatchRepository.save(batch);
        }
    }
}
