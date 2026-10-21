package VietFreshHub.loyalty;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.entity.Role;
import VietFreshHub.auth.entity.UserRole;
import VietFreshHub.auth.entity.UserRoleId;
import VietFreshHub.auth.repository.*;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.entity.*;
import VietFreshHub.delivery.repository.*;
import VietFreshHub.delivery.service.*;
import VietFreshHub.loyalty.entity.*;
import VietFreshHub.loyalty.repository.*;
import VietFreshHub.loyalty.services.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.OrderLifecycleLock;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.CustomerOrderServiceImpl;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.cart.repository.*;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.order.service.impl.OrderServiceImpl;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated H2 only. Real Spring transaction proxies, Delivery/Order/Payment/Earn and JPA.
 * Auth, notifications and event transport are mocked. No application DB/config is loaded.
 * Eligible COMPLETED/PAID states are reached exclusively through markDelivered(). */
class CodDeliveryRewardTransactionTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    private AnnotationConfigApplicationContext context;
    private EntityManagerFactory factory;
    private EntityManager em;
    private TransactionTemplate tx;
    private DeliveryStaffActionService actions;
    private DeliveryAssignmentService assignments;
    private PaymentService payments;
    private LoyaltyService loyalty;
    private OrderService orders;
    private OrderLifecycleLock locks;
    private CustomerOrderService customerOrders;
    private DeliveryNotificationService notifications;
    private ApplicationEventPublisher events;
    private ShopService shops;
    private Long customerId, staffId;

    private record Fixture(Long orderId, List<Long> childIds, List<Long> deliveryIds, List<Long> assignmentIds, Long paymentId) {}
    private static BigDecimal money(String s) { return new BigDecimal(s); }
    private static void amount(String s, BigDecimal actual) { assertEquals(0, money(s).compareTo(actual)); }

    @BeforeEach
    void setup() {
        var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.driver_class", "org.h2.Driver",
                "hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MSSQLServer;LOCK_TIMEOUT=10000;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
                "hibernate.connection.pool_size", "8",
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.hbm2ddl.create_namespaces", "false",
                "hibernate.hbm2ddl.halt_on_error", "true")).build();
        var metadata = new MetadataSources(registry);
        Set<Class<?>> entities = new LinkedHashSet<>();
        for (Class<?> type : List.of(Order.class, ShopOrder.class, OrderStatusHistory.class, Payment.class,
                Delivery.class, DeliveryAssignment.class, DeliveryStatusHistory.class,
                LoyaltyAccount.class, LoyaltyTransaction.class, LoyaltyRule.class)) collect(type, entities);
        entities.forEach(metadata::addAnnotatedClass);
        factory = metadata.buildMetadata().buildSessionFactory();
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        var manager = new JpaTransactionManager(factory);
        tx = new TransactionTemplate(manager);
        var repositories = new JpaRepositoryFactory(em);
        var auth = mock(AuthService.class);
        shops = mock(ShopService.class);
        notifications = mock(DeliveryNotificationService.class);
        events = mock(ApplicationEventPublisher.class);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean("transactionManager", JpaTransactionManager.class, () -> manager);
        context.registerBean(AuthService.class, () -> new AuthService(repositories.getRepository(UserRepository.class),
                repositories.getRepository(RoleRepository.class), repositories.getRepository(UserRoleRepository.class), mock(PasswordEncoder.class)));
        context.registerBean(OrderLifecycleLock.class, () -> new OrderLifecycleLock(em));
        context.registerBean(OrderServiceImpl.class, () -> new OrderServiceImpl(
                repositories.getRepository(ShopOrderRepository.class), mock(OrderAddressRepository.class),
                mock(OrderDeliveryReadRepository.class), mock(OrderItemRepository.class), shops,
                mock(DeliveryCreationService.class), auth, em, context.getBean(OrderLifecycleLock.class)));
        context.registerBean(DeliveryService.class, () -> new DeliveryService(
                repositories.getRepository(DeliveryRepository.class), context.getBean(OrderLifecycleLock.class)));
        context.registerBean(DeliveryStatusHistoryWriter.class, () -> new DeliveryStatusHistoryWriter(
                repositories.getRepository(DeliveryStatusHistoryRepository.class)));
        context.registerBean(DeliveryAssignmentService.class, () -> new DeliveryAssignmentService(
                repositories.getRepository(DeliveryAssignmentRepository.class), repositories.getRepository(DeliveryRepository.class),
                context.getBean(DeliveryService.class), context.getBean(DeliveryStatusHistoryWriter.class),
                repositories.getRepository(DeliveryStatusHistoryRepository.class), auth, shops,
                context.getBean(OrderService.class), events, notifications, context.getBean(OrderLifecycleLock.class)));
        context.registerBean(PaymentService.class, () -> new PaymentService(repositories.getRepository(PaymentRepository.class),
                context.getBean(OrderLifecycleLock.class), em));
        context.registerBean(LoyaltyServiceImpl.class, () -> new LoyaltyServiceImpl(
                repositories.getRepository(LoyaltyAccountRepository.class), repositories.getRepository(LoyaltyTransactionRepository.class),
                repositories.getRepository(LoyaltyRuleRepository.class), em));
        context.registerBean(DeliveryStaffActionService.class, () -> new DeliveryStaffActionService(
                context.getBean(DeliveryService.class), context.getBean(DeliveryAssignmentService.class),
                context.getBean(DeliveryStatusHistoryWriter.class), context.getBean(OrderService.class), notifications, events,
                context.getBean(PaymentService.class), context.getBean(LoyaltyService.class), em));
        context.registerBean(CustomerOrderServiceImpl.class, () -> new CustomerOrderServiceImpl(
                repositories.getRepository(OrderRepository.class), repositories.getRepository(ShopOrderRepository.class),
                repositories.getRepository(PaymentRepository.class), mock(OrderDeliveryReadRepository.class),
                mock(OrderDeliveryHistoryReadRepository.class), repositories.getRepository(OrderStatusHistoryRepository.class),
                context.getBean(DeliveryAssignmentService.class), repositories.getRepository(UserRepository.class),
                mock(CartRepository.class), mock(CartItemRepository.class), mock(CatalogVariantRepository.class),
                mock(CatalogService.class), mock(CheckoutInventoryBatchRepository.class), mock(OrderInventoryReleaseService.class), em, events));
        context.refresh();
        actions = context.getBean(DeliveryStaffActionService.class);
        assignments = context.getBean(DeliveryAssignmentService.class);
        payments = context.getBean(PaymentService.class);
        loyalty = context.getBean(LoyaltyService.class);
        orders = context.getBean(OrderService.class);
        locks = context.getBean(OrderLifecycleLock.class);
        customerOrders = context.getBean(CustomerOrderService.class);
        assertTrue(AopUtils.isAopProxy(actions)); assertTrue(AopUtils.isAopProxy(loyalty));
        tx.executeWithoutResult(status -> {
            // H2 equivalent of the SQL Server filtered EARN index; does not alter project DB.
            em.createNativeQuery("ALTER TABLE dbo.loyalty_transactions ADD earn_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN transaction_type = 'EARN' THEN order_id ELSE NULL END)").executeUpdate();
            em.createNativeQuery("CREATE UNIQUE INDEX UX_test_earn ON dbo.loyalty_transactions(loyalty_account_id, earn_order_id)").executeUpdate();
            customerId = user("buyer").getUserId(); staffId = user("shipper").getUserId();
            var rule = new LoyaltyRule(); rule.setRuleId(1L); rule.setEarningPercent(money("1")); rule.setEarningEnabled(true);
            rule.setMinRedemptionAmount(money("1000")); rule.setMaxRedemptionPercent(20); rule.setRedemptionEnabled(true); em.persist(rule);
        });
        when(auth.getCurrentDeliveryStaffId(any())).thenReturn(staffId);
        when(auth.getCurrentUserId(any())).thenReturn(staffId);
    }

    private static void collect(Class<?> type, Set<Class<?>> entities) {
        if (!type.isAnnotationPresent(Entity.class) || !entities.add(type)) return;
        for (var field : type.getDeclaredFields()) {
            collect(field.getType(), entities);
            if (field.getGenericType() instanceof ParameterizedType generic)
                for (var arg : generic.getActualTypeArguments()) if (arg instanceof Class<?> child) collect(child, entities);
        }
    }

    private User user(String name) {
        var user = new User(); user.setEmail(name + UUID.randomUUID() + "@test.invalid"); user.setFullName(name); em.persist(user); return user;
    }

    private Fixture fixture(int shopCount) {
        return tx.execute(status -> {
            var order = new Order(); order.setCustomer(em.getReference(User.class, customerId)); order.setOrderCode(UUID.randomUUID().toString());
            order.setSubtotal(money("200000").multiply(BigDecimal.valueOf(shopCount)));
            order.setDiscountTotal(money("30000").multiply(BigDecimal.valueOf(shopCount)));
            order.setShippingFee(money("15000").multiply(BigDecimal.valueOf(shopCount)));
            order.setGrandTotal(money("185000").multiply(BigDecimal.valueOf(shopCount))); em.persist(order);
            var childIds = new ArrayList<Long>(); var deliveryIds = new ArrayList<Long>(); var assignmentIds = new ArrayList<Long>();
            for (int i = 0; i < shopCount; i++) {
                var shop = new Shop(); shop.setShopName("Shop " + i); em.persist(shop);
                var child = new ShopOrder(); child.setOrder(order); child.setShop(shop); child.setStatus("READY_FOR_DELIVERY");
                child.setSubtotal(money("200000")); child.setDiscountTotal(money("30000"));
                child.setShippingFee(money("15000")); child.setTotalAmount(money("185000")); em.persist(child); childIds.add(child.getShopOrderId());
                var delivery = new Delivery(); delivery.setShopOrder(child); delivery.markAssigned(now().minusMinutes(5)); delivery.accept(); em.persist(delivery); deliveryIds.add(delivery.getDeliveryId());
                var assignment = new DeliveryAssignment(delivery, em.getReference(User.class, staffId), now().minusMinutes(5));
                assignment.accept(now().minusMinutes(4)); em.persist(assignment); assignmentIds.add(assignment.getAssignmentId());
            }
            var method = em.createQuery("select m from PaymentMethod m where m.methodCode = 'COD'", PaymentMethod.class).getResultStream().findFirst().orElse(null);
            if (method == null) { method = new PaymentMethod(); method.setMethodCode("COD"); method.setName("COD"); em.persist(method); }
            var payment = new Payment(); payment.setOrder(order); payment.setPaymentMethod(method); payment.setAmount(order.getGrandTotal()); em.persist(payment);
            return new Fixture(order.getOrderId(), childIds, deliveryIds, assignmentIds, payment.getPaymentId());
        });
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
    private void start(Fixture f, int index) {
        actions.confirmPickup(f.assignmentIds().get(index), null);
        actions.startDelivery(f.assignmentIds().get(index), null);
    }
    private void deliver(Fixture f, int index) { actions.markDelivered(f.assignmentIds().get(index), null); }
    private void change(Fixture f, Consumer<Payment> action) {
        tx.executeWithoutResult(status -> action.accept(em.find(Payment.class, f.paymentId())));
    }
    private long count(String entity) { return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(); }
    private void assertUnpaid(Fixture f, Order.Status state) {
        tx.executeWithoutResult(status -> {
            var order = em.find(Order.class, f.orderId()); assertEquals(state, order.getStatus());
            assertEquals("UNPAID", order.getPaymentStatus());
            var payment = em.find(Payment.class, f.paymentId());
            if (payment != null) { assertNotEquals(Payment.Status.PAID, payment.getStatus()); assertNull(payment.getPaidAt()); }
            assertEquals(0L, count("LoyaltyTransaction")); assertEquals(0L, count("LoyaltyAccount"));
        });
    }
    private void assertEarned(Fixture f, String earned) {
        tx.executeWithoutResult(status -> {
            var order = em.find(Order.class, f.orderId()); assertEquals(Order.Status.COMPLETED, order.getStatus());
            assertEquals("PAID", order.getPaymentStatus()); assertNotNull(order.getCompletedAt());
            var payment = em.find(Payment.class, f.paymentId()); assertEquals(Payment.Status.PAID, payment.getStatus()); assertNotNull(payment.getPaidAt());
            assertEquals(1L, count("LoyaltyTransaction"));
            var transaction = em.createQuery("select t from LoyaltyTransaction t", LoyaltyTransaction.class).getSingleResult();
            assertEquals(LoyaltyTransactionType.EARN, transaction.getTransactionType()); amount(earned, transaction.getAmount());
            amount(earned, transaction.getBalanceAfterAmount()); amount(earned, loyalty.getBalance(customerId).rewardBalance());
            amount(earned, loyalty.getBalance(customerId).totalEarnedAmount());
        });
    }

    @AfterEach
    void close() { if (context != null) context.close(); if (factory != null) factory.close(); }

    @Test
    void oneShopCompletesAndPaysThroughRealDeliveryThenRetriesAreHarmless() {
        var f = fixture(1); start(f, 0);
        doAnswer(call -> {
            assertWriteLocked(em.find(User.class, customerId));
            assertWriteLocked(em.find(Order.class, f.orderId()));
            assertWriteLocked(em.find(Payment.class, f.paymentId()));
            return null;
        }).when(notifications).notifyCompleted(any());
        deliver(f, 0); assertEarned(f, "1700");
        var paidAt = tx.execute(status -> em.find(Payment.class, f.paymentId()).getPaidAt());
        assertThrows(ResponseStatusException.class, () -> deliver(f, 0));
        tx.executeWithoutResult(status -> {
            assertTrue(payments.recordCodCollection(f.orderId(), f.childIds().getFirst()));
            amount("0", loyalty.earnReward(f.orderId()));
            assertEquals(paidAt, em.find(Payment.class, f.paymentId()).getPaidAt());
            assertEquals(1L, em.createQuery("select count(h) from DeliveryStatusHistory h where h.status = :s", Long.class).setParameter("s", DeliveryStatus.DELIVERED).getSingleResult());
        });
        assertEarned(f, "1700");
    }

    @Test
    void onlyLastShopSettlesOrderAndEarns() {
        var f = fixture(2); start(f, 0); start(f, 1); deliver(f, 0);
        assertUnpaid(f, Order.Status.PARTIALLY_COMPLETED);
        deliver(f, 1); assertEarned(f, "3400");
    }

    @Test
    void failureAfterEarnWasFlushedRollsBackDeliveryPaymentAccountAndHistories() {
        var f = fixture(1); start(f, 0);
        doAnswer(call -> {
            assertEquals(1L, count("LoyaltyTransaction")); // Earn really executed before the tail failed.
            assertEquals(Payment.Status.PAID, em.find(Payment.class, f.paymentId()).getStatus());
            throw new IllegalStateException("tail failure");
        }).when(events).publishEvent(isA(DeliveryAssignmentRetryEvent.class));
        assertThrows(IllegalStateException.class, () -> deliver(f, 0));
        assertUnpaid(f, Order.Status.PENDING);
        assertDeliveryRolledBack(f);
        reset(events); deliver(f, 0); assertEarned(f, "1700");
    }

    private void assertDeliveryRolledBack(Fixture f) {
        tx.executeWithoutResult(status -> {
            var delivery = em.find(Delivery.class, f.deliveryIds().getLast());
            assertEquals(DeliveryStatus.OUT_FOR_DELIVERY, delivery.getStatus()); assertNull(delivery.getDeliveredAt());
            assertEquals("OUT_FOR_DELIVERY", em.find(ShopOrder.class, f.childIds().getLast()).getStatus());
            assertEquals(DeliveryAssignmentStatus.ACCEPTED, em.find(DeliveryAssignment.class, f.assignmentIds().getLast()).getStatus());
            assertEquals(0L, em.createQuery("select count(h) from DeliveryStatusHistory h where h.status = :s", Long.class).setParameter("s", DeliveryStatus.DELIVERED).getSingleResult());
            assertEquals(0L, em.createQuery("select count(h) from OrderStatusHistory h where h.status = 'COMPLETED'", Long.class).getSingleResult());
        });
    }

    @Test
    void invalidEarnRuleRollsBackDeliveryAndPayment() {
        var f = fixture(1); start(f, 0);
        tx.executeWithoutResult(status -> em.find(LoyaltyRule.class, 1L).setEarningPercent(money("0")));
        assertThrows(IllegalStateException.class, () -> deliver(f, 0));
        assertUnpaid(f, Order.Status.PENDING); assertDeliveryRolledBack(f);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong-order", "wrong-amount", "online", "duplicate", "cancelled", "failed", "processing", "refunded", "wrong-child-total", "wrong-parent-total"})
    void invalidPaymentOrAmountsRollbackCompletion(String problem) {
        var f = fixture(1); start(f, 0);
        change(f, payment -> {
            switch (problem) {
                case "missing" -> em.remove(payment);
                case "wrong-order" -> {
                    var other = new Order(); other.setCustomer(em.getReference(User.class, customerId)); other.setOrderCode(UUID.randomUUID().toString()); em.persist(other); payment.setOrder(other);
                }
                case "wrong-amount" -> payment.setAmount(money("1"));
                case "online" -> { var method = new PaymentMethod(); method.setMethodCode("ONLINE"); method.setName("Online"); em.persist(method); payment.setPaymentMethod(method); }
                case "duplicate" -> { var copy = new Payment(); copy.setOrder(payment.getOrder()); copy.setPaymentMethod(payment.getPaymentMethod()); copy.setAmount(payment.getAmount()); em.persist(copy); }
                case "cancelled" -> payment.setStatus(Payment.Status.CANCELLED);
                case "failed" -> payment.setStatus(Payment.Status.FAILED);
                case "processing" -> payment.setStatus(Payment.Status.PROCESSING);
                case "refunded" -> payment.setStatus(Payment.Status.REFUNDED);
                case "wrong-child-total" -> em.find(ShopOrder.class, f.childIds().getFirst()).setTotalAmount(money("1"));
                case "wrong-parent-total" -> payment.getOrder().setGrandTotal(money("1"));
                default -> throw new AssertionError(problem);
            }
        });
        assertThrows(ResponseStatusException.class, () -> deliver(f, 0));
        assertUnpaid(f, Order.Status.PENDING); assertDeliveryRolledBack(f);
    }

    @Test
    void failedDeliveryAndGoodsReturnAreNotCashReceipts() {
        var f = fixture(2); start(f, 0); start(f, 1); deliver(f, 0);
        actions.markFailed(f.assignmentIds().get(1), "Customer absent", null);
        tx.executeWithoutResult(status -> when(shops.getManagedShopId(any())).thenReturn(em.find(ShopOrder.class, f.childIds().get(1)).getShopId()));
        assignments.confirmGoodsReturn(f.deliveryIds().get(1), null);
        assertThrows(ResponseStatusException.class, () -> deliver(f, 1));
        assertUnpaid(f, Order.Status.PARTIALLY_COMPLETED);
        tx.executeWithoutResult(status -> {
            assertEquals(DeliveryAssignmentStatus.COMPLETED, em.find(DeliveryAssignment.class, f.assignmentIds().get(1)).getStatus());
            assertEquals(DeliveryStatus.FAILED, em.find(Delivery.class, f.deliveryIds().get(1)).getStatus());
            assertFalse(payments.recordCodCollection(f.orderId(), f.childIds().getFirst()));
        });
    }

    @Test
    void cancelledChildCannotBeCountedOrCompleteParent() {
        var f = fixture(2); start(f, 0);
        tx.executeWithoutResult(status -> em.find(ShopOrder.class, f.childIds().get(1)).setStatus("CANCELLED"));
        deliver(f, 0); assertUnpaid(f, Order.Status.PARTIALLY_COMPLETED);
        assertThrows(ResponseStatusException.class, () -> deliver(f, 1));
    }

    @Test
    void cancelledParentRejectsDeliveryAndEarn() {
        var f = fixture(1); start(f, 0);
        change(f, payment -> { payment.setStatus(Payment.Status.CANCELLED); payment.getOrder().setStatus(Order.Status.CANCELLED); });
        assertThrows(ResponseStatusException.class, () -> deliver(f, 0));
        amount("0", loyalty.earnReward(f.orderId())); assertUnpaid(f, Order.Status.CANCELLED);
    }

    @Test
    void currentWholeOrderCancellationDoesNotEarn() {
        var f = fixture(1);
        // A normal cancellable order has pending children, no delivery and no discounts.
        tx.executeWithoutResult(status -> {
            em.remove(em.find(DeliveryAssignment.class, f.assignmentIds().getFirst()));
            em.flush(); em.remove(em.find(Delivery.class, f.deliveryIds().getFirst()));
            var child = em.find(ShopOrder.class, f.childIds().getFirst()); child.setStatus("PENDING");
            child.setDiscountTotal(BigDecimal.ZERO); child.setTotalAmount(money("215000"));
            var order = em.find(Order.class, f.orderId()); order.setDiscountTotal(BigDecimal.ZERO); order.setGrandTotal(money("215000"));
            em.find(Payment.class, f.paymentId()).setAmount(money("215000"));
        });
        customerOrders.cancel(customerId, f.orderId(), "Changed mind");
        customerOrders.cancel(customerId, f.orderId(), "Retry");
        amount("0", loyalty.earnReward(f.orderId())); assertUnpaid(f, Order.Status.CANCELLED);
        tx.executeWithoutResult(status -> {
            assertEquals("CANCELLED", em.find(ShopOrder.class, f.childIds().getFirst()).getStatus());
            assertEquals(Payment.Status.CANCELLED, em.find(Payment.class, f.paymentId()).getStatus());
        });
    }

    @Test
    void lifecycleLocksAreReentrantAndPreserveUnflushedChanges() {
        var f = fixture(1);
        tx.executeWithoutResult(status -> {
            var order = locks.lock(f.orderId()); order.setStatus(Order.Status.PROCESSING);
            assertEquals(Order.Status.PROCESSING, locks.lock(f.orderId()).getStatus());
            em.flush(); order.setStatus(Order.Status.CONFIRMED);
            assertEquals(Order.Status.CONFIRMED, locks.lock(f.orderId()).getStatus());
        });
    }

    private void assertWriteLocked(Object entity) {
        assertTrue(Set.of(LockModeType.PESSIMISTIC_WRITE, LockModeType.PESSIMISTIC_FORCE_INCREMENT).contains(em.getLockMode(entity)));
    }

    @Test
    void capacityLockUsesRoleInsteadOfOtherCustomerUsers() {
        Integer roleId = tx.execute(status -> {
            var role = new Role(); role.setRoleName("ROLE_DELIVERY_STAFF"); em.persist(role);
            for (Long userId : List.of(customerId, staffId)) {
                var link = new UserRole(); var id = new UserRoleId(); id.setUserId(userId); id.setRoleId(role.getRoleId());
                link.setId(id); link.setUser(em.getReference(User.class, userId)); link.setRole(role); em.persist(link);
            }
            return role.getRoleId();
        });
        var f = fixture(1);
        tx.executeWithoutResult(status -> {
            locks.lock(f.orderId());
            var staff = context.getBean(AuthService.class).lockActiveDeliveryStaffIdsForAssignment();
            assertTrue(staff.containsAll(List.of(customerId, staffId)));
            assertEquals(LockModeType.PESSIMISTIC_WRITE, em.getLockMode(em.find(Role.class, roleId)));
            assertNotEquals(LockModeType.PESSIMISTIC_WRITE, em.getLockMode(em.find(User.class, staffId)));
        });
    }

    @Test
    void databaseUniqueIndexRejectsAnotherEarnForSameOrder() {
        var f = fixture(1); start(f, 0); deliver(f, 0);
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
            var existing = em.createQuery("select t from LoyaltyTransaction t", LoyaltyTransaction.class).getSingleResult();
            em.persist(LoyaltyTransaction.builder().order(existing.getOrder()).loyaltyAccount(existing.getLoyaltyAccount())
                    .transactionType(LoyaltyTransactionType.EARN).amount(money("1")).balanceAfterAmount(money("1701")).build());
            em.flush();
        }));
        assertEarned(f, "1700");
    }

    @Test
    void paymentRejectsWrongChildAndAbsentDeliveryEvidence() {
        var f = fixture(1); var other = fixture(1);
        assertThrows(ResponseStatusException.class, () -> tx.executeWithoutResult(status -> payments.recordCodCollection(f.orderId(), other.childIds().getFirst())));
        assertThrows(ResponseStatusException.class, () -> tx.executeWithoutResult(status -> payments.recordCodCollection(f.orderId(), f.childIds().getFirst())));
        assertUnpaid(f, Order.Status.PENDING);
    }

    @Test
    void concurrentShopsAndConcurrentRetryEarnOnce() throws Exception {
        var f = fixture(2); start(f, 0); start(f, 1);
        runConcurrently(() -> deliver(f, 0), () -> deliver(f, 1)); assertEarned(f, "3400");
        runConcurrently(() -> amount("0", loyalty.earnReward(f.orderId())), () -> amount("0", loyalty.earnReward(f.orderId())));
        assertEarned(f, "3400");
    }

    @Test
    void cachedOtherShopEvidenceIsRefreshedAfterAcquiringParentLock() {
        var f = fixture(2); start(f, 0); start(f, 1);
        tx.executeWithoutResult(status -> {
            assertEquals("OUT_FOR_DELIVERY", em.find(ShopOrder.class, f.childIds().getFirst()).getStatus());
            assertEquals(DeliveryStatus.OUT_FOR_DELIVERY, em.find(Delivery.class, f.deliveryIds().getFirst()).getStatus());
            try {
                CompletableFuture.runAsync(() -> deliver(f, 0)).get(15, TimeUnit.SECONDS);
            } catch (Exception ex) { throw new AssertionError(ex); }
            // This persistence context still contains the first shop's old state.
            deliver(f, 1);
        });
        assertEarned(f, "3400");
    }

    @Test
    void concurrentDuplicateDeliveryHasOneSuccessAndOneConflict() throws Exception {
        var f = fixture(1); start(f, 0);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var conflicts = new java.util.concurrent.atomic.AtomicInteger();
        Runnable action = () -> {
            try { deliver(f, 0); successes.incrementAndGet(); }
            catch (ResponseStatusException ex) { assertEquals(409, ex.getStatusCode().value()); conflicts.incrementAndGet(); }
        };
        runConcurrently(action, action);
        assertEquals(1, successes.get()); assertEquals(1, conflicts.get()); assertEarned(f, "1700");
    }

    @Test
    void concurrentOrdersOfSameCustomerDoNotLoseBalanceOrCreateTwoAccounts() throws Exception {
        var first = fixture(1); var second = fixture(1); start(first, 0); start(second, 0);
        runConcurrently(() -> deliver(first, 0), () -> deliver(second, 0));
        tx.executeWithoutResult(status -> {
            assertEquals(1L, count("LoyaltyAccount")); assertEquals(2L, count("LoyaltyTransaction"));
            amount("3400", loyalty.getBalance(customerId).rewardBalance());
        });
    }

    @Test
    void concurrentCancellationAndDeliveryUseSameCustomerOrderPrefix() throws Exception {
        var f = fixture(1); start(f, 0);
        runConcurrently(() -> deliver(f, 0), () -> assertThrows(CustomerOrderException.class,
                () -> customerOrders.cancel(customerId, f.orderId(), "Too late")));
        assertEarned(f, "1700");
    }

    private void runConcurrently(Runnable first, Runnable second) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = new ArrayList<Future<?>>();
            for (Runnable action : List.of(first, second)) jobs.add(pool.submit(() -> {
                ready.countDown();
                try { if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                action.run();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (Future<?> job : jobs) job.get(20, TimeUnit.SECONDS);
        }
    }
}
