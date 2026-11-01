package VietFreshHub.admin.repository;

import VietFreshHub.auth.entity.Role;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.entity.UserRole;
import VietFreshHub.auth.entity.UserRoleId;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.notification.entity.Notification;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.review.entity.Review;
import VietFreshHub.review.repository.ReviewReminderRepository;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-report;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminDashboardRepositoryTest {
    private static final ZoneId HCMC = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final AtomicLong TEST_IDS = new AtomicLong(10_000);

    @Autowired EntityManager entityManager;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired ShopOrderRepository shopOrders;
    @Autowired DeliveryRepository deliveries;
    @Autowired CatalogProductRepository products;
    @Autowired UserRepository users;
    @Autowired UserRoleRepository userRoles;
    @Autowired InventoryBatchRepository inventory;
    @Autowired ReviewReminderRepository reviewReminders;

    @Test
    void dashboardAggregatesDeliveredValueOncePerShopOrderAndExcludesCancelledSales() {
        LocalDate day = LocalDate.of(2026, 10, 12);
        LocalDateTime deliveredAt = day.atTime(18, 20).atZone(HCMC).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        User customer = customerWithRole("CUSTOMER");
        Shop firstShop = shop("Shop A");
        Shop secondShop = shop("Shop B");
        CatalogProduct firstProduct = product(firstShop, "Táo");
        CatalogVariant firstVariant = variant(firstProduct, "kg", 5);
        CatalogVariant secondVariant = variant(firstProduct, "kg", 5);
        CatalogProduct secondProduct = product(secondShop, "Lê");
        CatalogVariant secondShopVariant = variant(secondProduct, "hộp", 3);

        Order splitOrder = order(customer, "SPLIT-1", Order.Status.COMPLETED, day.atTime(9, 0));
        ShopOrder firstShopOrder = shopOrder(splitOrder, firstShop, "COMPLETED", "100.00", deliveredAt);
        ShopOrder secondShopOrder = shopOrder(splitOrder, secondShop, "COMPLETED", "200.00", deliveredAt.plusHours(1));
        item(firstShopOrder, firstVariant, 2);
        item(firstShopOrder, secondVariant, 1);
        item(secondShopOrder, secondShopVariant, 1);
        delivered(firstShopOrder, deliveredAt);
        delivered(secondShopOrder, deliveredAt.plusHours(1));

        Order cancelledOrder = order(customer, "CANCEL-1", Order.Status.CANCELLED, day.atTime(10, 0));
        ShopOrder cancelledShopOrder = shopOrder(cancelledOrder, firstShop, "CANCELLED", "999.00", null);
        item(cancelledShopOrder, firstVariant, 999);
        Order pendingOrder = order(customer, "PENDING-1", Order.Status.PROCESSING, day.atTime(11, 0));
        ShopOrder pendingShopOrder = shopOrder(pendingOrder, firstShop, "OUT_FOR_DELIVERY", "500.00", null);
        Delivery failedDelivery = new Delivery();
        failedDelivery.setShopOrder(pendingShopOrder);
        item(pendingShopOrder, firstVariant, 777);
        failedDelivery.markFailed("Test delivery failure");
        entityManager.persist(failedDelivery);
        entityManager.flush();

        LocalDateTime from = day.atStartOfDay(HCMC).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = day.plusDays(1).atStartOfDay(HCMC).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        DeliveryRepository.RevenueTotal total = deliveries.getDeliveredRevenue(from, to);
        List<DeliveryRepository.RevenueByHour> byHour = deliveries.getDeliveredRevenueByHour(from, to);
        List<OrderRepository.StatusCount> statuses = orders.countOrdersByStatus(from, to);
        List<OrderItemRepository.AdminBestSellingProduct> best = items.findAdminBestSellers(from, to, PageRequest.of(0, 10));

        assertEquals(new BigDecimal("300.00"), total.getRevenue());
        assertEquals(2L, total.getDeliveredShopOrders());
        assertEquals(2, byHour.size());
        assertEquals(new BigDecimal("300.00"), byHour.stream().map(DeliveryRepository.RevenueByHour::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(byHour.stream().allMatch(row -> LocalDateTime.of(row.getDeliveryYear(), row.getDeliveryMonth(),
                row.getDeliveryDay(), row.getDeliveryHour(), 0).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(HCMC).toLocalDate().equals(day)));
        assertEquals(3L, statuses.stream().mapToLong(OrderRepository.StatusCount::getOrderCount).sum(),
                "split shop orders must not duplicate the parent order count");
        assertTrue(statuses.stream().anyMatch(row -> row.getStatus() == Order.Status.CANCELLED && row.getOrderCount() == 1));
        assertTrue(best.stream().anyMatch(row -> row.getProductName().equals("Táo")
                && row.getUnit().equals("kg") && row.getUnitsSold() == 3L));
        assertTrue(best.stream().noneMatch(row -> row.getUnitsSold() == 999L));
        assertTrue(best.stream().noneMatch(row -> row.getUnitsSold() == 777L),
                "failed delivery items are not counted as best sellers");
    }

    @Test
    void customerAndInventoryQueriesUseRoleAndCurrentAvailabilityRules() {
        User customer = customerWithRole("CUSTOMER");
        addRole(customer, "STORE_MANAGER");
        customerWithRole("ADMIN");
        Shop shop = shop("Inventory Shop");
        CatalogProduct product = product(shop, "Cam");
        CatalogVariant lowVariant = variant(product, "kg", 5);
        variant(product, "thùng", 1);

        batch(lowVariant, 4, 1, LocalDate.now(HCMC).plusDays(2), BatchStatus.ACTIVE);
        batch(lowVariant, 20, 0, LocalDate.now(HCMC).minusDays(1), BatchStatus.ACTIVE);
        batch(lowVariant, 20, 0, LocalDate.now(HCMC).plusDays(2), BatchStatus.BLOCKED);
        entityManager.flush();

        var low = products.findLowStockVariants(LocalDate.now(HCMC), PageRequest.of(0, 10));
        assertEquals(1, users.countCustomers(), "a user with multiple roles is counted once as Customer");
        LocalDate today = LocalDate.now(HCMC);
        assertEquals(1, users.countNewCustomers(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        LocalDateTime utcNow = LocalDateTime.now(ZoneOffset.UTC);
        assertEquals(1, users.countNewCustomersByHour(utcNow.minusDays(1), utcNow.plusDays(1)).stream()
                .mapToLong(UserRepository.NewCustomersByHour::getCustomerCount).sum());
        assertEquals(1, products.countProductsByStatus().stream()
                .mapToLong(CatalogProductRepository.ProductStatusCount::getProductCount).sum());
        assertEquals(1, products.countVariantsWithAvailableStock(LocalDate.now(HCMC)));
        assertEquals(1, low.getTotalElements());
        assertEquals(3L, low.getContent().getFirst().getAvailableQuantity());
        assertEquals(5, low.getContent().getFirst().getLowStockThreshold());
    }

    @Test
    void platformStatisticsCountDeliveredParentOrdersOnceAndExcludeFailedPurchases() {
        LocalDateTime completed = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusDays(2);
        User first = customerWithRole("CUSTOMER");
        User returning = customerWithRole("CUSTOMER");
        User failed = customerWithRole("CUSTOMER");
        Shop shop = shop("Statistics Shop");
        CatalogProduct bought = product(shop, "Bought product");
        bought.setStatus("ACTIVE");
        CatalogVariant variant = variant(bought, "kg", 0);
        CatalogVariant secondVariant = variant(bought, "box", 0);
        CatalogProduct neverOrdered = product(shop, "Never ordered");
        neverOrdered.setStatus("INACTIVE");

        Order firstOrder = deliveredOrder(first, shop, variant, "STATS-1", completed, 1);
        ShopOrder firstChild = shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(firstOrder.getOrderId()).getFirst();
        item(firstChild, secondVariant, 4);
        Review existingReview = new Review();
        existingReview.setUser(first);
        existingReview.setProduct(bought);
        existingReview.setOrderItem(items.findByShopOrderId(firstChild.getShopOrderId()).getFirst());
        existingReview.setRating((byte) 5);
        entityManager.persist(existingReview);
        deliveredOrder(returning, shop, variant, "STATS-2", completed.plusMinutes(1), 2);
        deliveredOrder(returning, shop, variant, "STATS-3", completed.plusMinutes(2), 3);
        Order recent = deliveredOrder(first, shop, variant, "STATS-RECENT",
                LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(2), 5);
        Order failedOrder = order(failed, "STATS-FAILED", Order.Status.PROCESSING, completed);
        ShopOrder failedShopOrder = shopOrder(failedOrder, shop, "OUT_FOR_DELIVERY", "10", null);
        item(failedShopOrder, variant, 100);
        Delivery failedDelivery = new Delivery();
        failedDelivery.setShopOrder(failedShopOrder);
        failedDelivery.markFailed("Failed");
        entityManager.persist(failedDelivery);
        Order cancelledOrder = order(first, "STATS-CANCELLED", Order.Status.CANCELLED, completed);
        shopOrder(cancelledOrder, shop, "CANCELLED", "10", null);
        entityManager.flush();

        LocalDateTime from = completed.minusHours(1);
        LocalDateTime to = completed.plusDays(1);
        assertEquals(3, deliveries.countSuccessfullyDeliveredOrders(from, to));
        assertEquals(2, deliveries.countCancelledOrFailedOrders(from, to));
        assertEquals(5, orders.countOrdersByStatus(from, to).stream()
                .mapToLong(OrderRepository.StatusCount::getOrderCount).sum(),
                "parent order statistics must not multiply split shop orders");
        assertEquals(2, users.countCustomersWithSuccessfulPurchase());
        assertEquals(2, users.countReturningCustomers());
        var productStatusCounts = products.countProductsByStatus();
        assertEquals(2, productStatusCounts.stream().mapToLong(CatalogProductRepository.ProductStatusCount::getProductCount).sum());
        assertEquals(1, productStatusCounts.stream().filter(row -> row.getStatus().equals("ACTIVE"))
                .mapToLong(CatalogProductRepository.ProductStatusCount::getProductCount).sum());
        var productsWithoutOrders = products.findProductsWithoutOrders(PageRequest.of(0, 10));
        assertEquals(1, productsWithoutOrders.getTotalElements());
        assertEquals(List.of("Never ordered"), productsWithoutOrders
                .map(CatalogProductRepository.ProductWithoutOrders::getProductName).toList());
        assertEquals(10L, items.findAdminBestSellers(from, to, PageRequest.of(0, 10)).stream()
                .mapToLong(OrderItemRepository.AdminBestSellingProduct::getUnitsSold).sum());
        assertEquals(1, reviewReminders.findUnreviewedItemsForOrder(firstOrder.getOrderId(), first.getUserId()).size(),
                "a reminder leaves out product items already reviewed for that purchase");
        var dueReminders = reviewReminders.findEligibleOrderIds(completed.plusDays(1), PageRequest.of(0, 10));
        assertEquals(3, dueReminders.size());
        assertFalse(dueReminders.contains(recent.getOrderId()), "the reminder waits for 24 hours after completion");

        entityManager.persist(new Notification(first.getUserId(), "REVIEW_REMINDER", "Review", "Review products",
                "CUSTOMER_ORDER", firstOrder.getOrderId(), completed.plusDays(1)));
        entityManager.flush();
        assertFalse(reviewReminders.findEligibleOrderIds(completed.plusDays(1), PageRequest.of(0, 10))
                .contains(firstOrder.getOrderId()));
        assertEquals(2, reviewReminders.findEligibleOrderIds(completed.plusDays(1), PageRequest.of(0, 10)).size());
    }

    @Test
    void inventoryReportUsesPerProductExpiryRuleAndGroupsMixedUnitsSeparately() {
        Shop shop = shop("Expiry Shop");
        CatalogProduct catalogProduct = product(shop, "Soon to expire");
        catalogProduct.setStatus("ACTIVE");
        CatalogVariant kg = variant(catalogProduct, "kg", 0);
        CatalogVariant box = variant(catalogProduct, "box", 0);
        LocalDate today = LocalDate.now(HCMC);
        batch(kg, 5, 2, today.plusDays(2), BatchStatus.ACTIVE);
        batch(box, 8, 1, today.plusDays(5), BatchStatus.ACTIVE);
        entityManager.flush();

        var grouped = inventory.summarizeByShopAndUnit(today);
        assertEquals(2, grouped.size());
        assertTrue(grouped.stream().anyMatch(row -> row.getUnit().equals("kg")
                && row.getQuantityOnHand() == 5L && row.getAvailableQuantity() == 3L));
        var expiry = inventory.findApproachingExpiry(today, PageRequest.of(0, 10));
        assertEquals(1, expiry.getTotalElements(), "default product warning window is three days");
        assertEquals("Soon to expire", expiry.getContent().getFirst().getProductName());
    }

    private Order deliveredOrder(User customer, Shop shop, CatalogVariant variant, String code,
                                 LocalDateTime deliveredAt, int quantity) {
        Order order = order(customer, code, Order.Status.COMPLETED, deliveredAt.minusHours(1));
        order.setCompletedAt(deliveredAt);
        ShopOrder child = shopOrder(order, shop, "COMPLETED", "10", deliveredAt);
        item(child, variant, quantity);
        delivered(child, deliveredAt);
        return order;
    }

    private User customerWithRole(String roleName) {
        User user = new User();
        user.setEmail(roleName + "-" + System.nanoTime() + "@test.local");
        user.setFullName("Report Test");
        user.setStatus("ACTIVE");
        users.saveAndFlush(user);
        addRole(user, roleName);
        return user;
    }

    private void addRole(User user, String roleName) {
        Role role = entityManager.createQuery("select r from Role r where r.roleName = :name", Role.class)
                .setParameter("name", roleName).getResultStream().findFirst().orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setRoleName(roleName);
                    entityManager.persist(newRole);
                    return newRole;
                });
        UserRole userRole = new UserRole();
        UserRoleId id = new UserRoleId();
        id.setUserId(user.getUserId());
        id.setRoleId(role.getRoleId());
        userRole.setId(id);
        userRole.setUser(user);
        userRole.setRole(role);
        userRoles.save(userRole);
    }

    private Shop shop(String name) {
        Shop shop = new Shop();
        shop.setShopName(name);
        entityManager.persist(shop);
        return shop;
    }

    private CatalogProduct product(Shop shop, String name) {
        CatalogProduct product = new CatalogProduct();
        product.setShop(shop);
        product.setName(name);
        product.setSlug(name + "-" + System.nanoTime());
        entityManager.persist(product);
        return product;
    }

    private CatalogVariant variant(CatalogProduct product, String unit, int threshold) {
        CatalogVariant variant = new CatalogVariant();
        variant.setProduct(product);
        variant.setSku("SKU-" + System.nanoTime());
        variant.setVariantName(unit);
        variant.setThumbnailUrl("/image.png");
        variant.setUnit(unit);
        variant.setPrice(new BigDecimal("10000"));
        variant.setLowStockThreshold(threshold);
        entityManager.persist(variant);
        return variant;
    }

    private Order order(User customer, String code, Order.Status status, LocalDateTime placedAt) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(code);
        order.setStatus(status);
        order.setPlacedAt(placedAt);
        entityManager.persist(order);
        return order;
    }

    private ShopOrder shopOrder(Order order, Shop shop, String status, String total, LocalDateTime completedAt) {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setOrder(order);
        shopOrder.setShop(shop);
        shopOrder.setStatus(status);
        shopOrder.setSubtotal(new BigDecimal(total));
        shopOrder.setTotalAmount(new BigDecimal(total));
        shopOrder.setCompletedAt(completedAt);
        entityManager.persist(shopOrder);
        return shopOrder;
    }

    private void item(ShopOrder shopOrder, CatalogVariant variant, int quantity) {
        entityManager.createNativeQuery("""
                insert into dbo.order_items (order_item_id, shop_order_id, variant_id, product_name_snapshot,
                    variant_name_snapshot, sku_snapshot, unit_price, quantity, discount_amount, total_amount)
                values (:id, :shopOrderId, :variantId, :productName, :variantName, :sku,
                    :unitPrice, :quantity, 0, :total)
                """)
                .setParameter("id", TEST_IDS.incrementAndGet())
                .setParameter("shopOrderId", shopOrder.getShopOrderId())
                .setParameter("variantId", variant.getVariantId())
                .setParameter("productName", variant.getProduct().getName())
                .setParameter("variantName", variant.getVariantName())
                .setParameter("sku", variant.getSku())
                .setParameter("unitPrice", variant.getPrice())
                .setParameter("quantity", quantity)
                .setParameter("total", variant.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .executeUpdate();
    }

    private void delivered(ShopOrder shopOrder, LocalDateTime deliveredAt) {
        Delivery delivery = new Delivery();
        delivery.setShopOrder(shopOrder);
        delivery.setPickedUpAt(deliveredAt.minusMinutes(30));
        delivery.markDelivered(deliveredAt);
        entityManager.persist(delivery);
    }

    private void batch(CatalogVariant variant, int quantity, int reserved, LocalDate expiry, BatchStatus status) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        entityManager.createNativeQuery("""
                insert into dbo.inventory_batches (batch_id, variant_id, quantity_on_hand, reserved_quantity,
                    expiry_date, status, received_at, created_at, updated_at)
                values (:id, :variantId, :quantity, :reserved, :expiry, :status, :receivedAt, :createdAt, :updatedAt)
                """)
                .setParameter("id", TEST_IDS.incrementAndGet())
                .setParameter("variantId", variant.getVariantId())
                .setParameter("quantity", quantity)
                .setParameter("reserved", reserved)
                .setParameter("expiry", expiry)
                .setParameter("status", status.name())
                .setParameter("receivedAt", now)
                .setParameter("createdAt", now)
                .setParameter("updatedAt", now)
                .executeUpdate();
    }
}
