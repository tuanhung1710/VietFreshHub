package VietFreshHub.checkout;

import VietFreshHub.auth.repository.*;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.cart.repository.*;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.cart.service.impl.CartServiceImpl;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.catalog.repository.*;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.catalog.service.impl.CatalogServiceImpl;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.repository.*;
import VietFreshHub.checkout.service.*;
import VietFreshHub.checkout.service.impl.CheckoutServiceImpl;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.OrderPlacedEvent;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.shop.repository.*;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.vietmap.service.VietMapService;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpSession;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** Opt-in, real SQL Server/JPA/transactions. Refuses databases without our disposable-test marker. */
@EnabledIfEnvironmentVariable(named="TV3_TEST_JDBC_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckoutSqlServerIntegrationTest {
    SessionFactory factory;
    EntityManager em;
    JdbcTemplate jdbc;
    JpaTransactionManager transactions;
    JpaRepositoryFactory repositories;
    CatalogService catalog;
    CartService cart;
    CustomerAddressService addresses;
    CheckoutShippingQuoteService shipping;
    InventoryReservationService inventory;
    List<Long> variants;
    final ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);

    @BeforeAll void connectOnlyToMarkedTestDatabase() throws Exception {
        String url=System.getenv("TV3_TEST_JDBC_URL");
        assertTrue(url.matches("(?i)^jdbc:sqlserver://[^;]+;databaseName=VietFreshHub_TV3_Test_[a-f0-9]{32};.*"),"Disposable test URL required");
        var datasource=new DriverManagerDataSource(url,System.getenv("TV3_TEST_SQL_USER"),System.getenv("TV3_TEST_SQL_PASSWORD"));
        jdbc=new JdbcTemplate(datasource);
        String name=jdbc.queryForObject("select db_name()",String.class);
        assertTrue(name.matches("VietFreshHub_TV3_Test_[a-f0-9]{32}"),"Connected database is not a disposable TV3 database");
        assertEquals(name,jdbc.queryForObject("select cast(value as nvarchar(128)) from sys.extended_properties where class=0 and name='TV3_TASK3_TEST'",String.class));
        assertEquals(60,jdbc.queryForObject("select count(*) from sys.tables where is_ms_shipped=0",Integer.class));
        String paymentSql=java.nio.file.Files.readString(java.nio.file.Path.of("scripts/tv3-vnpay-sandbox.sql"));
        for(String batch:paymentSql.split("(?im)^GO\\s*$")) if(!batch.isBlank()) jdbc.execute(batch);
        var configuration=new Configuration();
        configuration.getProperties().put("hibernate.connection.datasource",datasource);
        configuration.setProperty("hibernate.hbm2ddl.auto","none");
        configuration.setProperty("hibernate.jdbc.time_zone","UTC");
        configuration.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for(var bean:scanner.findCandidateComponents("VietFreshHub")) configuration.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
        factory=configuration.buildSessionFactory();
        transactions=new JpaTransactionManager(factory);
        em=SharedEntityManagerCreator.createSharedEntityManager(factory);
        repositories=new JpaRepositoryFactory(em);
        var users=repo(UserRepository.class);
        catalog=transactional(new CatalogServiceImpl(repo(CatalogProductRepository.class),repo(CatalogVariantRepository.class),repo(CheckoutInventoryBatchRepository.class)));
        cart=transactional(new CartServiceImpl(repo(CartRepository.class),repo(CartItemRepository.class),repo(CatalogVariantRepository.class),repo(CheckoutInventoryBatchRepository.class),users,catalog));
        addresses=transactional(new CustomerAddressService(repo(AddressRepository.class),users,em));
        var shops=transactional(new ShopService(repo(ShopLocationRepository.class),repo(ShopRepository.class),em,mock(AuthService.class),repo(ShopMemberRepository.class)));
        var map=mock(VietMapService.class);
        when(map.motorcycleRoadDistanceMeters(any(),any(),any(),any())).thenReturn(new BigDecimal("2000"));
        shipping=transactional(new CheckoutShippingQuoteService(addresses,shops,map,new ShippingFeeService()));
        inventory=transactional(new InventoryReservationService(em));
        variants=read(() -> {
            var result=new ArrayList<Long>(); var shopsSeen=new HashSet<Long>();
            for(Long id:jdbc.queryForList("select variant_id from dbo.product_variants order by variant_id",Long.class)) {
                var variant=repo(CatalogVariantRepository.class).findById(id).orElseThrow();
                if(catalog.isPurchasable(variant) && repo(CheckoutInventoryBatchRepository.class).getAvailableStockByVariantId(id)>=10
                        && shopsSeen.add(variant.getProduct().getShop().getShopId())) result.add(id);
                if(result.size()==2) break;
            }
            assertEquals(2,result.size(),"Seed must offer stock in two open shops"); return result;
        });
        // Give this suite its own stock: adding read-model fixtures must not exhaust seed SKUs
        // or make other cases depend on JUnit's execution order.
        var fixtureVariants = new ArrayList<Long>();
        for (int shopIndex = 0; shopIndex < variants.size(); shopIndex++) {
            long variant = freshVariant(shopIndex);
            batch(variant, 100, "ACTIVE", null);
            fixtureVariants.add(variant);
        }
        variants = fixtureVariants;
    }
    <T> T repo(Class<T> type) { return repositories.getRepository(type); }
    @SuppressWarnings("unchecked") <T> T transactional(T target) {
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        var interceptor=new TransactionInterceptor(); interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); proxy.addAdvice(interceptor);
        return (T)proxy.getProxy();
    }
    <T> T read(java.util.function.Supplier<T> body) {
        var transaction=new TransactionTemplate(transactions); transaction.setReadOnly(true);
        return transaction.execute(status -> body.get());
    }
    CheckoutService checkout(InventoryReservationService reservation) {
        return transactional(new CheckoutServiceImpl(repo(UserRepository.class),repo(CartRepository.class),cart,repo(CheckoutAddressRepository.class),
                addresses,repo(PaymentMethodRepository.class),repo(OrderRepository.class),catalog,reservation,shipping,em,events,mock(VietFreshHub.payment.service.VnpayPaymentService.class)));
    }
    long insert(String sql,Object... parameters) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<parameters.length;i++) statement.setObject(i+1,parameters[i]);
            return statement;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    record Buyer(long user,long address) {}
    Buyer buyer() {
        long user=insert("insert dbo.users(email,full_name,status) values(?,N'TV3 Integration','ACTIVE')",UUID.randomUUID()+"@tv3-test.invalid");
        long address=insert("insert dbo.addresses(user_id,recipient_name,phone,province,district,ward,address_line,province_id,district_id,ward_id,formatted_address,latitude,longitude,is_default) "
                +"select top(1) ?,recipient_name,phone,province,district,ward,address_line,province_id,district_id,ward_id,formatted_address,latitude,longitude,1 from dbo.addresses where latitude is not null and longitude is not null order by address_id",user);
        return new Buyer(user,address);
    }
    CheckoutDraft draft(Buyer buyer,Long... selection) {
        var checkout=checkout(inventory);
        var preview=selection.length==0 ? checkout.preview(buyer.user()) : checkout.preview(buyer.user(),List.of(selection));
        return new CheckoutDraftStore().issue(new MockHttpSession(),buyer.user(),shipping.quote(buyer.user(),preview,buyer.address()));
    }
    CheckoutRequest request(Buyer buyer,CheckoutDraft draft) {
        var form=new CheckoutRequest(); form.setAddressId(buyer.address()); form.setCheckoutToken(draft.token()); return form;
    }
    long count(String table,String predicate,Object... values) {
        return Objects.requireNonNull(jdbc.queryForObject("select count_big(*) from dbo."+table+" where "+predicate,Long.class,values));
    }
    List<Map<String,Object>> stock() { return jdbc.queryForList("select batch_id,quantity_on_hand,reserved_quantity from dbo.inventory_batches order by batch_id"); }
    long freshVariant(int shopIndex) {
        return insert("insert dbo.product_variants(product_id,sku,variant_name,thumbnail_url,unit,price,status) "
                +"select product_id,?,variant_name,thumbnail_url,unit,price,'ACTIVE' from dbo.product_variants where variant_id=?",
                "TV3-"+UUID.randomUUID(),variants.get(shopIndex));
    }
    long batch(long variant,int quantity,String status,LocalDate expiry) {
        long id=insert("insert dbo.inventory_batches(variant_id,quantity_on_hand,reserved_quantity,status,expiry_date) values(?,?,0,?,?)",variant,quantity,status,expiry);
        jdbc.update("insert dbo.inventory_transactions(variant_id,batch_id,transaction_type,quantity,reference_type,note) values(?,?,'RESTOCK',?,'TV3_TEST',N'Isolated test fixture')",variant,id,quantity);
        return id;
    }
    void assertRolledBack(Buyer buyer,List<Map<String,Object>> before) {
        assertEquals(0,count("orders","customer_id=?",buyer.user())); assertEquals(before,stock());
        assertEquals(0,count("inventory_transactions","created_by=?",buyer.user()));
        assertEquals("ACTIVE",cart.getActiveCart(buyer.user()).getStatus());
        verify(events,never()).publishEvent(argThat((Object event) -> event instanceof OrderPlacedEvent placed
                && placed.customerId().equals(buyer.user())));
    }

    @Test void realMultiShopCheckoutCreatesSnapshotsPaymentAndMatchingStockLedger() {
        var buyer=buyer(); cart.addToCart(buyer.user(),new AddToCartRequest(variants.get(0),2));
        cart.addToCart(buyer.user(),new AddToCartRequest(variants.get(1),1));
        var draft=draft(buyer); var result=checkout(inventory).confirm(buyer.user(),draft,request(buyer,draft));
        assertEquals(2,count("shop_orders","order_id=?",result.orderId()));
        assertEquals(1,count("order_addresses","order_id=? and latitude is not null and recipient_name<>''",result.orderId()));
        assertEquals(1,count("payments","order_id=? and status='PENDING' and paid_at is null and amount=?",result.orderId(),result.grandTotal()));
        assertEquals(0,result.grandTotal().compareTo(jdbc.queryForObject("select sum(total_amount) from dbo.shop_orders where order_id=?",BigDecimal.class,result.orderId())));
        assertEquals(3,jdbc.queryForObject("select sum(a.quantity) from dbo.stock_allocations a join dbo.order_items i on i.order_item_id=a.order_item_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and a.status='RESERVED'",Integer.class,result.orderId()));
        assertEquals(3,jdbc.queryForObject("select sum(t.quantity) from dbo.inventory_transactions t join dbo.order_items i on i.order_item_id=t.reference_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and t.reference_type='ORDER_ITEM' and t.transaction_type='RESERVE'",Integer.class,result.orderId()));
        assertEquals(1,count("carts","user_id=? and status='CHECKED_OUT'",buyer.user()));
    }
    @Test void partialCheckoutKeepsOtherShopInActiveCart() {
        var buyer=buyer(); var selected=cart.addToCart(buyer.user(),new AddToCartRequest(variants.get(0),1));
        cart.addToCart(buyer.user(),new AddToCartRequest(variants.get(1),1));
        var draft=draft(buyer,selected.getCartItemId());
        var result=checkout(inventory).confirm(buyer.user(),draft,request(buyer,draft));
        assertEquals(1,count("shop_orders","order_id=?",result.orderId()));
        var remaining=cart.getActiveCart(buyer.user());
        assertEquals(1,remaining.getItems().size()); assertEquals(variants.get(1),remaining.getItems().getFirst().getVariantId());
        assertEquals("ACTIVE",remaining.getStatus());
    }
    @Test void failureAfterReservationRollsBackEveryOrderPaymentCartAndStockWrite() {
        var buyer=buyer(); cart.addToCart(buyer.user(),new AddToCartRequest(variants.getFirst(),2));
        var draft=draft(buyer); var before=stock();
        var failAfterReservation=mock(InventoryReservationService.class);
        doAnswer(call -> { inventory.reserve(call.getArgument(0),call.getArgument(1)); throw new CheckoutException("Injected failure after actual reserve"); })
                .when(failAfterReservation).reserve(anyList(),eq(buyer.user()));
        assertThrows(CheckoutException.class,() -> checkout(failAfterReservation).confirm(buyer.user(),draft,request(buyer,draft)));
        assertEquals(0,count("orders","customer_id=?",buyer.user())); assertEquals(before,stock());
        assertEquals(1,cart.getActiveCart(buyer.user()).getItems().size());
        assertEquals(0,count("inventory_transactions","created_by=?",buyer.user()));
        verify(events,never()).publishEvent(argThat((Object event) -> event instanceof OrderPlacedEvent placed
                && placed.customerId().equals(buyer.user())));
    }
    List<Object> concurrent(Callable<Object> first,Callable<Object> second) throws Exception {
        var start=new CountDownLatch(1); var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var results=new ArrayList<Future<Object>>();
            for(var call:List.of(first,second)) results.add(pool.submit(() -> { ready.countDown(); start.await(); try { return call.call(); } catch(Exception exception) { return exception; } }));
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            var result=new ArrayList<Object>(); for(var future:results) result.add(future.get(60,TimeUnit.SECONDS)); return result;
        }
    }
    @Test void concurrentDuplicateConfirmCreatesOneOrderAndOneReservation() throws Exception {
        var buyer=buyer(); cart.addToCart(buyer.user(),new AddToCartRequest(variants.getFirst(),1));
        var draft=draft(buyer); var service=checkout(inventory);
        var result=concurrent(() -> service.confirm(buyer.user(),draft,request(buyer,draft)),() -> service.confirm(buyer.user(),draft,request(buyer,draft)));
        assertTrue(result.stream().allMatch(CheckoutResult.class::isInstance),result::toString);
        assertEquals(((CheckoutResult)result.get(0)).orderId(),((CheckoutResult)result.get(1)).orderId());
        assertEquals(1,count("orders","customer_id=?",buyer.user()));
        assertEquals(1,count("payments","order_id=?",((CheckoutResult)result.getFirst()).orderId()));
        assertEquals(1,jdbc.queryForObject("select sum(quantity) from dbo.inventory_transactions where transaction_type='RESERVE' and created_by=?",Integer.class,buyer.user()));
    }
    @Test void insufficientSecondShopRollsBackFirstShopsReservation() {
        long first=freshVariant(0),second=freshVariant(1);
        batch(first,1,"ACTIVE",null); long secondBatch=batch(second,1,"ACTIVE",null);
        var buyer=buyer(); cart.addToCart(buyer.user(),new AddToCartRequest(first,1)); cart.addToCart(buyer.user(),new AddToCartRequest(second,1));
        var draft=draft(buyer);
        jdbc.update("update dbo.inventory_batches set quantity_on_hand=0 where batch_id=?",secondBatch);
        jdbc.update("insert dbo.inventory_transactions(variant_id,batch_id,transaction_type,quantity,reference_type) values(?,?,'ADJUSTMENT',-1,'TV3_TEST')",second,secondBatch);
        var before=stock(); long allocations=count("stock_allocations","1=1");
        assertThrows(CheckoutException.class,() -> checkout(inventory).confirm(buyer.user(),draft,request(buyer,draft)));
        assertRolledBack(buyer,before); assertEquals(allocations,count("stock_allocations","1=1"));
        assertEquals(2,cart.getActiveCart(buyer.user()).getItems().size());
    }
    @Test void actualPaymentConstraintFailureRollsBackOrderAndReservations() {
        var buyer=buyer(); cart.addToCart(buyer.user(),new AddToCartRequest(variants.getFirst(),1));
        var draft=draft(buyer); var before=stock(); long payments=count("payments","1=1"),allocations=count("stock_allocations","1=1");
        // Test database only: reject new payment rows without revalidating the seed's existing rows.
        jdbc.execute("alter table dbo.payments with nocheck add constraint TV3_Task3_RejectPayment check (amount<0)");
        try {
            RuntimeException failure=assertThrows(RuntimeException.class,() -> checkout(inventory).confirm(buyer.user(),draft,request(buyer,draft)));
            Throwable root=failure; while(root.getCause()!=null) root=root.getCause();
            assertInstanceOf(java.sql.SQLException.class,root); assertEquals(547,((java.sql.SQLException)root).getErrorCode());
            assertRolledBack(buyer,before);
            assertEquals(payments,count("payments","1=1")); assertEquals(allocations,count("stock_allocations","1=1"));
        } finally { jdbc.execute("alter table dbo.payments drop constraint TV3_Task3_RejectPayment"); }
    }
    @Test void twoCustomersCompetingForLastUnitGetOneSuccessAndOneStockRejection() throws Exception {
        long variant=freshVariant(0); long batch=batch(variant,1,"ACTIVE",null);
        var first=buyer(); var second=buyer();
        cart.addToCart(first.user(),new AddToCartRequest(variant,1)); cart.addToCart(second.user(),new AddToCartRequest(variant,1));
        var firstDraft=draft(first); var secondDraft=draft(second); var service=checkout(inventory);
        var result=concurrent(() -> service.confirm(first.user(),firstDraft,request(first,firstDraft)),
                () -> service.confirm(second.user(),secondDraft,request(second,secondDraft)));
        assertEquals(1,result.stream().filter(CheckoutResult.class::isInstance).count(),result::toString);
        assertEquals(1,result.stream().filter(CheckoutException.class::isInstance).count(),result::toString);
        assertEquals(1,count("orders","customer_id in (?,?)",first.user(),second.user()));
        assertEquals(1,jdbc.queryForObject("select reserved_quantity from dbo.inventory_batches where batch_id=?",Integer.class,batch));
        assertEquals(1,jdbc.queryForObject("select quantity_on_hand from dbo.inventory_batches where batch_id=?",Integer.class,batch));
        assertEquals(1,count("stock_allocations","batch_id=? and status='RESERVED'",batch));
    }
    @Test void fefoExcludesExpiredAndBlockedBatchesAndDoesNotDeductPhysicalStock() {
        long variant=freshVariant(0); LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        long noExpiry=batch(variant,3,"ACTIVE",null),later=batch(variant,2,"ACTIVE",today.plusDays(5));
        long earliest=batch(variant,1,"ACTIVE",today.plusDays(1));
        long expired=batch(variant,5,"ACTIVE",today.minusDays(1)),blocked=batch(variant,5,"BLOCKED",today);
        var buyer=buyer(); cart.addToCart(buyer.user(),new AddToCartRequest(variant,4)); var draft=draft(buyer);
        var result=checkout(inventory).confirm(buyer.user(),draft,request(buyer,draft));
        var allocations=jdbc.queryForList("select a.batch_id,a.quantity from dbo.stock_allocations a join dbo.order_items i on i.order_item_id=a.order_item_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? order by a.allocation_id",result.orderId());
        assertEquals(List.of(earliest,later,noExpiry),allocations.stream().map(row -> ((Number)row.get("batch_id")).longValue()).toList());
        assertEquals(List.of(1,2,1),allocations.stream().map(row -> ((Number)row.get("quantity")).intValue()).toList());
        assertEquals(0,count("stock_allocations","batch_id in (?,?)",expired,blocked));
        assertEquals(16,jdbc.queryForObject("select sum(quantity_on_hand) from dbo.inventory_batches where variant_id=?",Integer.class,variant));
        assertEquals(4,jdbc.queryForObject("select sum(reserved_quantity) from dbo.inventory_batches where variant_id=?",Integer.class,variant));
        assertEquals(0,count("inventory_transactions","variant_id=? and transaction_type='SALE'",variant));
        assertEquals(0,count("order_items","shop_order_id in (select shop_order_id from dbo.shop_orders where order_id=?) and requires_preparation_check_snapshot <> (select p.requires_preparation_check from dbo.products p join dbo.product_variants v on v.product_id=p.product_id where v.variant_id=?)",result.orderId(),variant));
    }
    // Reuse the marked database and real checkout fixtures to exercise cancellation end to end.
    VietFreshHub.order.service.impl.CustomerOrderServiceImpl cancellation() {
        return transactional(new VietFreshHub.order.service.impl.CustomerOrderServiceImpl(
                repo(OrderRepository.class), repo(VietFreshHub.order.repository.ShopOrderRepository.class),
                repo(VietFreshHub.payment.repository.PaymentRepository.class),
                repo(VietFreshHub.order.repository.OrderDeliveryReadRepository.class), null, null, null,
                repo(UserRepository.class), null, null, null, null, null,
                transactional(new VietFreshHub.order.service.OrderInventoryReleaseService(em)), em, events));
    }

    @Test void cancellationReleasesMixedPendingConfirmedShopsExactlyOnce() {
        var buyer = buyer();
        var before = stock();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.get(0), 2));
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.get(1), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        jdbc.update("update dbo.shop_orders set status='CONFIRMED' where shop_order_id=(select min(shop_order_id) from dbo.shop_orders where order_id=?)", orderId);
        var service = cancellation();
        assertTrue(service.actions(buyer.user(), orderId).cancellable());
        service.cancel(buyer.user(), orderId, "Đổi kế hoạch");
        assertEquals(before, stock(), "Reserved stock returns to its initial value; physical stock stays unchanged");
        assertEquals(2, count("shop_orders", "order_id=? and status='CANCELLED'", orderId));
        assertEquals(1, count("payments", "order_id=? and status='CANCELLED'", orderId));
        assertEquals(2, count("order_status_history", "shop_order_id in (select shop_order_id from dbo.shop_orders where order_id=?) and status='CANCELLED'", orderId));
        long releases = count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user());
        assertTrue(releases > 0);
        assertEquals(0, count("stock_allocations", "order_item_id in (select i.order_item_id from dbo.order_items i join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=?) and status<>'RELEASED'", orderId));
        service.cancel(buyer.user(), orderId, null);
        assertEquals(releases, count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user()));
        assertEquals(before, stock());
    }

    @Test void preparingOrderRejectsCancellationWithoutStockChanges() {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        jdbc.update("update dbo.shop_orders set status='PREPARING' where order_id=?", orderId);
        var before = stock();
        assertThrows(VietFreshHub.exception.CustomerOrderException.class, () -> cancellation().cancel(buyer.user(), orderId, null));
        assertEquals(before, stock());
        assertEquals(0, count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user()));
    }

    @Test void concurrentCancellationReleasesStockOnlyOnce() throws Exception {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        var service = cancellation();
        var results = concurrent(() -> { service.cancel(buyer.user(), orderId, null); return "OK"; },
                () -> { service.cancel(buyer.user(), orderId, null); return "OK"; });
        assertEquals(List.of("OK", "OK"), results);
        assertEquals(-1, jdbc.queryForObject("select sum(quantity) from dbo.inventory_transactions where created_by=? and transaction_type='RELEASE'", Integer.class, buyer.user()));
    }

    @Test void failureAfterInventoryReleaseRollsBackCancellationAndLedger() {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        var before = stock();
        // Private test database only: force a real SQL failure after inventory.release has run.
        jdbc.execute("alter table dbo.payments with nocheck add constraint TV3_Task4_RejectCancel check (status<>'CANCELLED')");
        try {
            assertThrows(RuntimeException.class, () -> cancellation().cancel(buyer.user(), orderId, null));
            assertEquals(before, stock());
            assertEquals(1, count("orders", "order_id=? and status='PENDING'", orderId));
            assertEquals(1, count("shop_orders", "order_id=? and status='PENDING'", orderId));
            assertEquals(0, count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user()));
        } finally {
            jdbc.execute("alter table dbo.payments drop constraint TV3_Task4_RejectCancel");
        }
    }
    @Test void soldAllocationRejectsCancellationEvenIfOrderStatusIsStale() {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        long itemId = jdbc.queryForObject("select i.order_item_id from dbo.order_items i join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=?", Long.class, orderId);
        var before = stock();
        // Deliberately inconsistent fixture: cancellation must still reject the SOLD allocation.
        jdbc.update("update dbo.stock_allocations set status='SOLD' where order_item_id=?", itemId);
        try {
            assertThrows(VietFreshHub.exception.CustomerOrderException.class, () -> cancellation().cancel(buyer.user(), orderId, null));
            assertEquals(before, stock());
            assertEquals(1, count("orders", "order_id=? and status='PENDING'", orderId));
            assertEquals(0, count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user()));
        } finally {
            jdbc.update("update dbo.stock_allocations set status='RESERVED' where order_item_id=?", itemId);
        }
    }

    @Test void mismatchedReservationLedgerRejectsCancellation() {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        var before = stock();
        jdbc.update("update dbo.inventory_transactions set quantity=quantity+1 where created_by=? and transaction_type='RESERVE'", buyer.user());
        try {
            assertThrows(VietFreshHub.exception.CustomerOrderException.class, () -> cancellation().cancel(buyer.user(), orderId, null));
            assertEquals(before, stock());
            assertEquals(1, count("orders", "order_id=? and status='PENDING'", orderId));
            assertEquals(0, count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user()));
        } finally {
            jdbc.update("update dbo.inventory_transactions set quantity=quantity-1 where created_by=? and transaction_type='RESERVE'", buyer.user());
        }
    }

    @Test void cancellationAndRealShopPreparationCannotBothSucceed() throws Exception {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        jdbc.update("update dbo.shop_orders set status='CONFIRMED' where order_id=?", orderId);
        long childId = jdbc.queryForObject("select shop_order_id from dbo.shop_orders where order_id=?", Long.class, orderId);
        long shopId = jdbc.queryForObject("select shop_id from dbo.shop_orders where order_id=?", Long.class, orderId);
        var shopAccess = mock(ShopService.class);
        var auth = mock(AuthService.class);
        when(shopAccess.getManagedShopId(any())).thenReturn(shopId);
        when(auth.getCurrentUserId(any())).thenReturn(buyer.user());
        var fulfillment = transactional(new VietFreshHub.order.service.impl.OrderServiceImpl(
                repo(VietFreshHub.order.repository.ShopOrderRepository.class), null, null, null,
                shopAccess, null, auth, em));
        var service = cancellation();
        var results = concurrent(() -> { service.cancel(buyer.user(), orderId, null); return "CANCELLED"; },
                () -> { fulfillment.startPreparing(childId, null); return "PREPARING"; });
        assertEquals(1, results.stream().filter(String.class::isInstance).count(), results::toString);
        String state = jdbc.queryForObject("select status from dbo.shop_orders where shop_order_id=?", String.class, childId);
        assertTrue(List.of("CANCELLED", "PREPARING").contains(state));
        long releases = count("inventory_transactions", "created_by=? and transaction_type='RELEASE'", buyer.user());
        assertEquals("CANCELLED".equals(state) ? 1 : 0, releases);
        assertEquals("CANCELLED".equals(state) ? "CANCELLED" : "PENDING",
                jdbc.queryForObject("select status from dbo.orders where order_id=?", String.class, orderId));
    }
    @Test void trackingReadsRealFailureReasonWithoutModifyingDeliveryOrStock() {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        long customerId = buyer.user();
        long childId = jdbc.queryForObject("select shop_order_id from dbo.shop_orders where order_id=?", Long.class, orderId);
        String reason = "Không liên lạc được khách tại địa chỉ giao hàng";
        // Read-model fixture only; this test does not execute TV4's delivery mutation workflow.
        insert("insert dbo.deliveries(shop_order_id,status,failure_reason) values(?,'FAILED',?)", childId, reason);
        var service = transactional(new VietFreshHub.order.service.impl.CustomerOrderServiceImpl(
                repo(OrderRepository.class), repo(VietFreshHub.order.repository.ShopOrderRepository.class), null,
                repo(VietFreshHub.order.repository.OrderDeliveryReadRepository.class),
                repo(VietFreshHub.order.repository.OrderDeliveryHistoryReadRepository.class),
                repo(VietFreshHub.order.repository.OrderStatusHistoryRepository.class),
                mock(VietFreshHub.delivery.service.DeliveryAssignmentService.class),
                null, null, null, null, null, null, null, em, null));
        var before = stock();
        var deliveriesBefore = jdbc.queryForList("select * from dbo.deliveries order by delivery_id");
        var result = service.tracking(customerId, orderId).shops().stream()
                .filter(shop -> shop.shopOrderId().equals(childId)).findFirst().orElseThrow();
        assertEquals("FAILED", result.deliveryStatusCode());
        assertEquals(reason, result.failureReason());
        assertThrows(VietFreshHub.exception.ResourceNotFoundException.class, () -> service.tracking(Long.MAX_VALUE, orderId));
        assertEquals(before, stock());
        assertEquals(deliveriesBefore, jdbc.queryForList("select * from dbo.deliveries order by delivery_id"));
    }
    VietFreshHub.order.service.impl.CustomerOrderServiceImpl customerOrders() {
        return transactional(new VietFreshHub.order.service.impl.CustomerOrderServiceImpl(
                repo(OrderRepository.class), repo(VietFreshHub.order.repository.ShopOrderRepository.class),
                null, null, null, null, null, repo(UserRepository.class), repo(CartRepository.class),
                repo(CartItemRepository.class), repo(CatalogVariantRepository.class), catalog,
                repo(CheckoutInventoryBatchRepository.class), null, em, null));
    }

    @Test void reorderUsesCurrentPriceCombinesCartAndReportsUnavailableItemWithoutReserving() {
        var buyer = buyer();
        long available = freshVariant(0), unavailable = freshVariant(1);
        batch(available, 10, "ACTIVE", null); batch(unavailable, 10, "ACTIVE", null);
        cart.addToCart(buyer.user(), new AddToCartRequest(available, 2));
        cart.addToCart(buyer.user(), new AddToCartRequest(unavailable, 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        var snapshots = jdbc.queryForList("select i.* from dbo.order_items i join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? order by i.order_item_id", orderId);
        jdbc.update("update dbo.product_variants set price=12345 where variant_id=?", available);
        jdbc.update("update dbo.product_variants set status='INACTIVE' where variant_id=?", unavailable);
        cart.addToCart(buyer.user(), new AddToCartRequest(available, 1));
        var before = stock();
        long orderCount = count("orders", "customer_id=?", buyer.user());
        long ledgerCount = count("inventory_transactions", "created_by=?", buyer.user());
        long allocationCount = count("stock_allocations", "1=1");
        var result = customerOrders().reorder(buyer.user(), orderId);
        assertEquals(1, result.addedLines()); assertEquals(2, result.addedUnits()); assertEquals(1, result.skipped().size());
        var line = jdbc.queryForMap("select i.quantity,i.unit_price_snapshot from dbo.cart_items i join dbo.carts c on c.cart_id=i.cart_id where c.user_id=? and c.status='ACTIVE' and i.variant_id=?", buyer.user(), available);
        assertEquals(3, ((Number) line.get("quantity")).intValue());
        assertEquals(0, new BigDecimal("12345").compareTo((BigDecimal) line.get("unit_price_snapshot")));
        assertEquals(before, stock()); assertEquals(orderCount, count("orders", "customer_id=?", buyer.user()));
        assertEquals(ledgerCount, count("inventory_transactions", "created_by=?", buyer.user()));
        assertEquals(allocationCount, count("stock_allocations", "1=1"));
        assertEquals(snapshots, jdbc.queryForList("select i.* from dbo.order_items i join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? order by i.order_item_id", orderId));
        var other = buyer();
        assertThrows(VietFreshHub.exception.ResourceNotFoundException.class, () -> customerOrders().reorder(other.user(), orderId));
    }

    @Test void concurrentReorderAddsBothRequestsToOneActiveCartWithoutLosingQuantity() throws Exception {
        var buyer = buyer();
        cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        var before = stock(); var service = customerOrders();
        var results = concurrent(() -> service.reorder(buyer.user(), orderId), () -> service.reorder(buyer.user(), orderId));
        assertTrue(results.stream().allMatch(VietFreshHub.order.dto.ReorderResult.class::isInstance), results::toString);
        assertEquals(1, count("carts", "user_id=? and status='ACTIVE'", buyer.user()));
        assertEquals(2, jdbc.queryForObject("select sum(i.quantity) from dbo.cart_items i join dbo.carts c on c.cart_id=i.cart_id where c.user_id=? and c.status='ACTIVE'", Integer.class, buyer.user()));
        assertEquals(1, count("orders", "customer_id=?", buyer.user())); assertEquals(before, stock());
    }

    long historyOrder(long customerId, String prefix, String placedAt) {
        return insert("insert dbo.orders(customer_id,order_code,placed_at) values(?,?,convert(datetime2(0),?))",
                customerId, prefix + UUID.randomUUID().toString().substring(0, 8), placedAt);
    }
    @Test void historyQueryEnforcesOwnershipDateBoundariesLiteralKeywordAndPagination() {
        var buyer = buyer(); var other = buyer(); String prefix = "TV3_%_" + UUID.randomUUID().toString().substring(0, 8);
        historyOrder(buyer.user(), prefix, "2026-10-19T16:59:59");
        long start = historyOrder(buyer.user(), prefix, "2026-10-19T17:00:00");
        long end = historyOrder(buyer.user(), prefix, "2026-10-20T16:59:59");
        historyOrder(buyer.user(), prefix, "2026-10-20T17:00:00");
        for (int i=0; i<9; i++) historyOrder(buyer.user(), prefix, "2026-10-18T00:00:00");
        historyOrder(other.user(), prefix, "2026-10-20T00:00:00");
        var filter = new VietFreshHub.order.dto.CustomerOrderFilterRequest();
        filter.setKeyword("  " + prefix.toLowerCase(Locale.ROOT) + "  ");
        filter.setFromDate(LocalDate.of(2026,10,20)); filter.setToDate(LocalDate.of(2026,10,20));
        var service = customerOrders(); var result = service.history(buyer.user(), filter);
        assertEquals(List.of(end, start), result.stream().map(VietFreshHub.order.dto.OrderListRow::orderId).toList());
        filter.setFromDate(LocalDate.of(1,1,1)); filter.setToDate(LocalDate.of(9999,12,31)); filter.setPage(999);
        var last = service.history(buyer.user(), filter);
        assertEquals(13, last.getTotalElements()); assertEquals(1, last.getNumber()); assertEquals(3, last.getNumberOfElements());
        assertEquals(1, service.history(other.user(), filter).getTotalElements());
    }

    @Test void deliveringFilterUsesShopAndDeliveryStatusEvenWhenParentIsStillPending() {
        var buyer = buyer(); cart.addToCart(buyer.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(buyer);
        long orderId = checkout(inventory).confirm(buyer.user(), draft, request(buyer, draft)).orderId();
        long childId = jdbc.queryForObject("select shop_order_id from dbo.shop_orders where order_id=?", Long.class, orderId);
        jdbc.update("update dbo.shop_orders set status='OUT_FOR_DELIVERY' where shop_order_id=?", childId);
        long deliveryId = insert("insert dbo.deliveries(shop_order_id,status,failure_reason) values(?,'FAILED',N'Test failure')", childId);
        var filter = new VietFreshHub.order.dto.CustomerOrderFilterRequest(); filter.setStatus("OUT_FOR_DELIVERY");
        var service = customerOrders(); assertTrue(service.history(buyer.user(), filter).isEmpty());
        jdbc.update("update dbo.deliveries set status='OUT_FOR_DELIVERY' where delivery_id=?", deliveryId);
        assertEquals(List.of(orderId), service.history(buyer.user(), filter).stream().map(VietFreshHub.order.dto.OrderListRow::orderId).toList());
        jdbc.update("update dbo.orders set status='CANCELLED' where order_id=?", orderId);
        try { assertTrue(service.history(buyer.user(), filter).isEmpty()); }
        finally { jdbc.update("update dbo.orders set status='PENDING' where order_id=?", orderId); }
    }
    Buyer securedBuyer() {
        var buyer = buyer();
        assertEquals(1, jdbc.update("insert dbo.user_roles(user_id,role_id) select ?,role_id from dbo.roles where role_name='ROLE_CUSTOMER'", buyer.user()));
        return buyer;
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor customer(Buyer buyer) {
        String email = jdbc.queryForObject("select email from dbo.users where user_id=?", String.class, buyer.user());
        return user(email).roles("CUSTOMER");
    }
    VietFreshHub.customer.CustomerHttpHarness customerHttp() {
        var lifecycle = transactional(new VietFreshHub.order.service.impl.CustomerOrderServiceImpl(
                repo(OrderRepository.class), repo(VietFreshHub.order.repository.ShopOrderRepository.class),
                repo(VietFreshHub.payment.repository.PaymentRepository.class),
                repo(VietFreshHub.order.repository.OrderDeliveryReadRepository.class),
                repo(VietFreshHub.order.repository.OrderDeliveryHistoryReadRepository.class),
                repo(VietFreshHub.order.repository.OrderStatusHistoryRepository.class),
                mock(VietFreshHub.delivery.service.DeliveryAssignmentService.class), repo(UserRepository.class),
                repo(CartRepository.class), repo(CartItemRepository.class), repo(CatalogVariantRepository.class), catalog,
                repo(CheckoutInventoryBatchRepository.class), transactional(new VietFreshHub.order.service.OrderInventoryReleaseService(em)), em, events));
        var details = transactional(new VietFreshHub.order.service.impl.CustomerOrderQueryServiceImpl(
                repo(OrderRepository.class), repo(VietFreshHub.order.repository.OrderAddressRepository.class),
                repo(VietFreshHub.order.repository.CustomerOrderReadRepository.class)));
        return new VietFreshHub.customer.CustomerHttpHarness(repo(UserRepository.class), repo(UserRoleRepository.class),
                cart, catalog, checkout(inventory), shipping, new CheckoutDraftStore(), addresses, lifecycle, details,
                transactional(new VietFreshHub.payment.service.impl.CustomerPaymentHistoryServiceImpl(
                        repo(VietFreshHub.payment.repository.CustomerPaymentReadRepository.class))));
    }

    @Test void securedHttpCustomerFlowFromCartThroughCheckoutHistoryTrackingReorderAndCancel() throws Exception {
        var buyer = securedBuyer(); var session = new MockHttpSession(); var before = stock();
        try (var http = customerHttp()) {
            http.mvc.perform(post("/cart/add").session(session).with(customer(buyer)).with(csrf())
                    .param("variantId", variants.getFirst().toString()).param("quantity", "2").param("customerId", "999999"))
                    .andExpect(redirectedUrl("/cart"));
            http.mvc.perform(get("/cart").session(session).with(customer(buyer))).andExpect(status().isOk());
            var preview = http.mvc.perform(get("/customer/checkout").session(session).with(customer(buyer))
                    .param("addressId", Long.toString(buyer.address()))).andExpect(status().isOk()).andReturn();
            var form = (CheckoutRequest) preview.getModelAndView().getModel().get("form");
            http.mvc.perform(post("/customer/checkout").session(session).with(customer(buyer)).with(csrf())
                    .param("checkoutToken", form.getCheckoutToken()).param("addressId", Long.toString(buyer.address())).param("paymentMethodCode", "COD"))
                    .andExpect(status().is3xxRedirection());
            long orderId = jdbc.queryForObject("select order_id from dbo.orders where customer_id=? and order_code=?", Long.class,
                    buyer.user(), "VF-" + form.getCheckoutToken());
            for (String path : List.of("/customer/orders", "/customer/orders/" + orderId,
                    "/customer/orders/" + orderId + "/confirmation", "/customer/orders/" + orderId + "/tracking")) {
                http.mvc.perform(get(path).session(session).with(customer(buyer))).andExpect(status().isOk());
            }
            http.mvc.perform(post("/customer/orders/" + orderId + "/reorder").session(session).with(customer(buyer)).with(csrf()))
                    .andExpect(redirectedUrl("/cart"));
            assertEquals(2, cart.getActiveCart(buyer.user()).getTotalItems());
            http.mvc.perform(post("/customer/orders/" + orderId + "/cancel").session(session).with(customer(buyer)).with(csrf())
                    .param("reason", "Đổi kế hoạch")).andExpect(redirectedUrl("/customer/orders/" + orderId));
            assertEquals(1, count("orders", "customer_id=? and status='CANCELLED'", buyer.user()));
            assertEquals(before, stock());
            assertEquals(1, count("payments", "order_id=? and status='CANCELLED'", orderId));
        }
    }

    @Test void securedHttpOtherCustomerCannotReadCancelOrReorderOwnersOrder() throws Exception {
        var owner = securedBuyer(); var stranger = securedBuyer();
        cart.addToCart(owner.user(), new AddToCartRequest(variants.getFirst(), 1));
        var draft = draft(owner);
        long orderId = checkout(inventory).confirm(owner.user(), draft, request(owner, draft)).orderId();
        var before = stock();
        try (var http = customerHttp()) {
            for (String suffix : List.of("", "/confirmation", "/tracking")) {
                http.mvc.perform(get("/customer/orders/" + orderId + suffix).with(customer(stranger))).andExpect(status().isNotFound());
            }
            for (String action : List.of("cancel", "reorder")) {
                http.mvc.perform(post("/customer/orders/" + orderId + "/" + action).with(customer(stranger)).with(csrf())
                        .param("customerId", Long.toString(owner.user()))).andExpect(status().isNotFound());
            }
            var history = http.mvc.perform(get("/customer/orders").with(customer(stranger)).param("keyword", "VF-" + draft.token()))
                    .andExpect(status().isOk()).andReturn();
            assertTrue(((org.springframework.data.domain.Page<?>) history.getModelAndView().getModel().get("orderPage")).isEmpty());
            assertEquals(before, stock()); assertEquals(0, count("carts", "user_id=?", stranger.user()));
            assertEquals(1, count("orders", "order_id=? and status='PENDING'", orderId));
        }
    }

    @Test void securedHttpRejectsOtherCustomersAddressAndDraftWithoutCreatingOrder() throws Exception {
        var owner = securedBuyer(); var stranger = securedBuyer(); var session = new MockHttpSession();
        cart.addToCart(owner.user(), new AddToCartRequest(variants.getFirst(), 1));
        try (var http = customerHttp()) {
            var preview = http.mvc.perform(get("/customer/checkout").session(session).with(customer(owner))
                    .param("addressId", Long.toString(owner.address()))).andExpect(status().isOk()).andReturn();
            var form = (CheckoutRequest) preview.getModelAndView().getModel().get("form");
            var before = stock();
            http.mvc.perform(get("/customer/checkout/address/" + stranger.address() + "/edit")
                    .session(session).with(customer(owner)).param("checkoutToken", form.getCheckoutToken()))
                    .andExpect(redirectedUrl("/cart"));
            http.mvc.perform(post("/customer/checkout").session(session).with(customer(owner)).with(csrf())
                    .param("checkoutToken", form.getCheckoutToken()).param("addressId", Long.toString(stranger.address())).param("paymentMethodCode", "COD"))
                    .andExpect(status().isOk()).andExpect(model().hasErrors());
            http.mvc.perform(post("/customer/checkout").session(session).with(customer(stranger)).with(csrf())
                    .param("checkoutToken", form.getCheckoutToken()).param("addressId", Long.toString(stranger.address())).param("paymentMethodCode", "COD"))
                    .andExpect(redirectedUrl("/cart"));
            assertEquals(0, count("orders", "customer_id in (?,?)", owner.user(), stranger.user()));
            assertEquals(before, stock());
        }
    }
    @Test void paymentHistoryHttpShowsOnlyOwnedRecordsWithStatusDateAndStablePagination() throws Exception {
        var buyer = securedBuyer(); var other = securedBuyer();
        long orderId = historyOrder(buyer.user(), "TV3-PAY-", "2026-10-20T00:00:00");
        long otherOrder = historyOrder(other.user(), "TV3-PAY-", "2026-10-20T00:00:00");
        int methodId = jdbc.queryForObject("select payment_method_id from dbo.payment_methods where method_code='COD'", Integer.class);
        long first = insert("insert dbo.payments(order_id,payment_method_id,amount,status,created_at) values(?,?,0,'PENDING',convert(datetime2(0),'2026-10-19T17:00:00'))", orderId, methodId);
        insert("insert dbo.payments(order_id,payment_method_id,amount,status,created_at,paid_at) values(?,?,0,'PAID',convert(datetime2(0),'2026-10-20T16:59:59'),convert(datetime2(0),'2026-10-20T16:59:59'))", orderId, methodId);
        for (int i=0; i<10; i++) insert("insert dbo.payments(order_id,payment_method_id,amount,status,created_at) values(?,?,0,'FAILED',convert(datetime2(0),'2026-10-18T00:00:00'))", orderId, methodId);
        insert("insert dbo.payments(order_id,payment_method_id,amount,status) values(?,?,0,'PENDING')", otherOrder, methodId);
        jdbc.update("insert dbo.payment_transactions(payment_id,event_type,amount,status,provider_payload) values(?,'TEST',0,'PENDING',N'PRIVATE_PAYLOAD_MUST_NOT_LEAK')", first);
        var before = jdbc.queryForList("select * from dbo.payments order by payment_id"); var stockBefore = stock();
        try (var http = customerHttp()) {
            var response = http.mvc.perform(get("/customer/payments").with(customer(buyer)).param("fromDate", "2026-10-20")
                    .param("toDate", "2026-10-20")).andExpect(status().isOk()).andReturn();
            var page = (org.springframework.data.domain.Page<?>) response.getModelAndView().getModel().get("paymentPage");
            assertEquals(2, page.getTotalElements());
            var paid = (VietFreshHub.payment.dto.PaymentHistoryRow) page.getContent().getFirst();
            assertEquals(orderId, paid.orderId()); assertEquals("Đã thanh toán", paid.statusLabel());
            assertEquals(java.time.LocalDateTime.of(2026,10,20,23,59,59), paid.paidAtLocal());
            assertFalse(page.getContent().toString().contains("PRIVATE_PAYLOAD_MUST_NOT_LEAK"));
            var pending = http.mvc.perform(get("/customer/payments").with(customer(buyer)).param("status", "PENDING"))
                    .andExpect(status().isOk()).andReturn();
            assertEquals(1, ((org.springframework.data.domain.Page<?>) pending.getModelAndView().getModel().get("paymentPage")).getTotalElements());
            var all = http.mvc.perform(get("/customer/payments").with(customer(buyer)).param("page", "999")
                    .param("fromDate", "0001-01-01").param("toDate", "9999-12-31")).andExpect(status().isOk()).andReturn();
            var last = (org.springframework.data.domain.Page<?>) all.getModelAndView().getModel().get("paymentPage");
            assertEquals(12, last.getTotalElements()); assertEquals(1, last.getNumber()); assertEquals(2, last.getNumberOfElements());
            var stranger = http.mvc.perform(get("/customer/payments").with(customer(other)).param("keyword",
                    jdbc.queryForObject("select order_code from dbo.orders where order_id=?", String.class, orderId)))
                    .andExpect(status().isOk()).andReturn();
            assertTrue(((org.springframework.data.domain.Page<?>) stranger.getModelAndView().getModel().get("paymentPage")).isEmpty());
            http.mvc.perform(post("/customer/payments").with(customer(buyer)).with(csrf()).param("status", "PAID"))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertEquals(before, jdbc.queryForList("select * from dbo.payments order by payment_id"));
        assertEquals(stockBefore, stock());
    }
    long recommendationProduct(long categoryId, int shopIndex) {
        long productId = insert("insert dbo.products(shop_id,name,slug,status,approval_status) select p.shop_id,N'TV3 Recommendation',?,'INACTIVE','APPROVED' from dbo.product_variants v join dbo.products p on p.product_id=v.product_id where v.variant_id=?",
                "tv3-rec-" + UUID.randomUUID(), variants.get(shopIndex));
        jdbc.update("insert dbo.product_categories(product_id,category_id) values(?,?)", productId, categoryId);
        return productId;
    }
    long recommendationVariant(long productId, BigDecimal price) {
        long variantId = insert("insert dbo.product_variants(product_id,sku,variant_name,thumbnail_url,unit,price,status) values(?,?,N'Test variant',N'/images/variants/variant-1.svg',N'kg',?,'ACTIVE')",
                productId, "TV3-REC-" + UUID.randomUUID(), price);
        // Publication trigger requires category and SKU to exist before activating a product.
        jdbc.update("update dbo.products set status='ACTIVE' where product_id=?", productId);
        return variantId;
    }
    @Test void recommendationsPrioritizeCategoriesExcludeUnavailableAndNeverChangeStock() {
        BigDecimal lowPrice = new BigDecimal("10000"), highPrice = new BigDecimal("20000");
        long category = insert("insert dbo.categories(name,slug,status) values(N'TV3 Related',?,'ACTIVE')", "tv3-related-" + UUID.randomUUID());
        long source = recommendationProduct(category, 0); recommendationVariant(source, highPrice);
        long related = recommendationProduct(category, 1);
        long expensive = recommendationVariant(related, highPrice); batch(expensive, 5, "ACTIVE", null);
        long cheap = recommendationVariant(related, lowPrice); batch(cheap, 5, "ACTIVE", null);
        long empty = recommendationProduct(category, 0); recommendationVariant(empty, lowPrice);
        long expired = recommendationProduct(category, 0);
        batch(recommendationVariant(expired, lowPrice), 5, "ACTIVE", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1));
        long blocked = recommendationProduct(category, 0);
        batch(recommendationVariant(blocked, lowPrice), 5, "ACTIVE", null);
        jdbc.update("update dbo.products set approval_status='PENDING' where product_id=?", blocked);
        var service = transactional(new VietFreshHub.recommendation.service.impl.ProductRecommendationServiceImpl(
                repo(VietFreshHub.recommendation.repository.ProductRecommendationRepository.class), repo(CatalogProductRepository.class), catalog));
        var before = stock(); long orderCount = count("orders", "1=1"), cartCount = count("carts", "1=1"), ledgerCount = count("inventory_transactions", "1=1");
        var result = service.relatedTo(source);
        assertFalse(result.isEmpty()); assertTrue(result.size() <= 6);
        assertEquals(related, result.getFirst().productId()); assertEquals(cheap, result.getFirst().variantId());
        assertEquals(result.size(), result.stream().map(VietFreshHub.recommendation.dto.ProductRecommendation::productId).distinct().count());
        assertTrue(result.stream().noneMatch(card -> List.of(source, empty, expired, blocked).contains(card.productId())));
        assertEquals(before, stock()); assertEquals(orderCount, count("orders", "1=1"));
        assertEquals(cartCount, count("carts", "1=1")); assertEquals(ledgerCount, count("inventory_transactions", "1=1"));
    }
    VietFreshHub.payment.service.VnpayPaymentService vnpay() throws Exception {
        var settings=new VietFreshHub.payment.config.VnpayProperties(true,"DEMOV210","test-secret",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html","http://localhost:8080/payments/vnpay/return");
        return transactional(new VietFreshHub.payment.service.impl.VnpayPaymentServiceImpl(settings,
                new VietFreshHub.payment.service.impl.VnpayGatewayImpl(settings),
                repo(VietFreshHub.payment.repository.PaymentRepository.class),repo(PaymentMethodRepository.class),
                repo(VietFreshHub.payment.repository.PaymentTransactionRepository.class),repo(OrderRepository.class),
                repo(VietFreshHub.order.repository.ShopOrderRepository.class),
                transactional(new VietFreshHub.order.service.OrderInventoryReleaseService(em)),em,events,15));
    }
    CheckoutResult onlineOrder(Buyer buyer,VietFreshHub.payment.service.VnpayPaymentService online) {
        cart.addToCart(buyer.user(),new AddToCartRequest(variants.getFirst(),1));
        var draft=draft(buyer); var form=request(buyer,draft); form.setPaymentMethodCode("VNPAY");
        var service=transactional(new CheckoutServiceImpl(repo(UserRepository.class),repo(CartRepository.class),cart,
                repo(CheckoutAddressRepository.class),addresses,repo(PaymentMethodRepository.class),repo(OrderRepository.class),
                catalog,inventory,shipping,em,events,online));
        var result=service.confirm(buyer.user(),draft,form);
        assertEquals(result,service.confirm(buyer.user(),draft,form));
        assertEquals(1,count("payments","order_id=?",result.orderId()));
        return result;
    }
    Map<String,List<String>> signedVnpay(long orderId,String response,String transaction) throws Exception {
        var p=jdbc.queryForMap("select top(1) transaction_reference,amount from dbo.payments where order_id=? order by payment_id desc",orderId);
        Map<String,List<String>> data=new TreeMap<>();
        data.put("vnp_TmnCode",List.of("DEMOV210")); data.put("vnp_TxnRef",List.of(p.get("transaction_reference").toString()));
        data.put("vnp_Amount",List.of(((BigDecimal)p.get("amount")).movePointRight(2).toBigIntegerExact().toString()));
        data.put("vnp_ResponseCode",List.of(response)); data.put("vnp_TransactionStatus",List.of("00".equals(response) ? "00" : "02"));
        data.put("vnp_TransactionNo",List.of(transaction));
        String query=data.entrySet().stream().map(e -> e.getKey()+"="+java.net.URLEncoder.encode(e.getValue().getFirst(),java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        var mac=javax.crypto.Mac.getInstance("HmacSHA512");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA512"));
        data.put("vnp_SecureHash",List.of(HexFormat.of().formatHex(mac.doFinal(query.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        return data;
    }
    @Test void vnpayRealCheckoutGateSignedIpnAndDuplicateAreTransactional() throws Exception {
        var online=vnpay(); var buyer=buyer(); var result=onlineOrder(buyer,online);
        assertTrue(online.available()); assertTrue(online.page(buyer.user(),result.orderId()).canPay());
        var uri=online.start(buyer.user(),result.orderId(),"127.0.0.1");
        assertEquals("sandbox.vnpayment.vn",uri.getHost()); assertTrue(uri.getRawQuery().contains("vnp_ExpireDate="));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update dbo.shop_orders set status='CONFIRMED' where order_id=?",result.orderId()));
        var signed=signedVnpay(result.orderId(),"00",Long.toString(System.nanoTime()));
        assertTrue(online.validReturn(signed)); assertEquals(1,count("orders","order_id=? and payment_status='UNPAID'",result.orderId()));
        var outcomes=concurrent(() -> online.receiveIpn(signed),() -> online.receiveIpn(signed));
        assertTrue(outcomes.stream().allMatch(o -> o instanceof Map),outcomes.toString());
        assertEquals(Set.of("00","02"),outcomes.stream().map(o -> ((Map<?,?>)o).get("RspCode")).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1,count("payments","order_id=? and status='PAID' and paid_at is not null",result.orderId()));
        assertEquals(1,count("orders","order_id=? and payment_status='PAID'",result.orderId()));
        assertEquals(1,count("payment_transactions","event_type='VNPAY_RECEIPT' and payment_id in(select payment_id from dbo.payments where order_id=?)",result.orderId()));
        assertFalse(online.page(buyer.user(),result.orderId()).canPay());
        jdbc.update("update dbo.shop_orders set status='CONFIRMED' where order_id=?",result.orderId());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> online.page(buyer().user(),result.orderId()));
    }
    @Test void vnpayFailedRetryUsesNewReferenceAndOldSuccessBlocksFurtherPayment() throws Exception {
        var online=vnpay(); var buyer=buyer(); var order=onlineOrder(buyer,online);
        var oldSuccess=signedVnpay(order.orderId(),"00",Long.toString(System.nanoTime()));
        assertEquals("00",online.receiveIpn(signedVnpay(order.orderId(),"24","0")).get("RspCode"));
        var first=online.page(buyer.user(),order.orderId()); assertEquals("FAILED",first.status());
        online.start(buyer.user(),order.orderId(),"127.0.0.1");
        assertEquals(2,count("payments","order_id=?",order.orderId()));
        assertEquals(first.expiresAt(),online.page(buyer.user(),order.orderId()).expiresAt());
        assertEquals("00",online.receiveIpn(oldSuccess).get("RspCode"));
        assertEquals("REVIEW",online.page(buyer.user(),order.orderId()).status());
        assertFalse(online.page(buyer.user(),order.orderId()).canPay());
        assertThrows(CheckoutException.class,() -> online.start(buyer.user(),order.orderId(),"127.0.0.1"));
    }
    @Test void vnpayExpiryReleasesStockAndLateReceiptCannotResurrectOrder() throws Exception {
        var online=vnpay(); var buyer=buyer(); var before=stock(); var order=onlineOrder(buyer,online);
        var success=signedVnpay(order.orderId(),"00",Long.toString(System.nanoTime()));
        jdbc.update("update dbo.payment_transactions set provider_payload=? where event_type='VNPAY_INIT' and payment_id in(select payment_id from dbo.payments where order_id=?)",
                java.time.Instant.now().minusSeconds(1).toString(),order.orderId());
        online.expire(order.orderId()); online.expire(order.orderId());
        assertEquals(before,stock()); assertEquals(1,count("orders","order_id=? and status='CANCELLED'",order.orderId()));
        assertEquals("00",online.receiveIpn(success).get("RspCode"));
        assertEquals(1,count("orders","order_id=? and status='CANCELLED' and payment_status='UNPAID'",order.orderId()));
        assertEquals("REVIEW",online.page(buyer.user(),order.orderId()).status()); assertEquals(before,stock());
    }
    @Test void vnpayReceiptFailureRollsBackPaymentAndCanBeRetried() throws Exception {
        var online=vnpay(); var buyer=buyer(); var order=onlineOrder(buyer,online);
        long paymentId=jdbc.queryForObject("select payment_id from dbo.payments where order_id=?",Long.class,order.orderId());
        var signed=signedVnpay(order.orderId(),"00",Long.toString(System.nanoTime()));
        jdbc.execute("alter table dbo.payment_transactions add constraint TV3_Vnpay_RejectReceipt check(payment_id<>"+paymentId+" or event_type<>'VNPAY_RECEIPT')");
        try { assertThrows(RuntimeException.class,() -> online.receiveIpn(signed)); }
        finally { jdbc.execute("alter table dbo.payment_transactions drop constraint TV3_Vnpay_RejectReceipt"); }
        assertEquals(1,count("orders","order_id=? and payment_status='UNPAID'",order.orderId()));
        assertEquals(1,count("payments","order_id=? and status='PENDING'",order.orderId()));
        assertEquals("00",online.receiveIpn(signed).get("RspCode"));
    }
    @Test void vnpayCustomerCanCancelUnpaidAndLateSuccessStaysInReview() throws Exception {
        var online=vnpay(); var buyer=buyer(); var before=stock(); var order=onlineOrder(buyer,online);
        var success=signedVnpay(order.orderId(),"00",Long.toString(System.nanoTime()));
        var customerOrders=cancellation();
        customerOrders.cancel(buyer.user(),order.orderId(),"Chưa cần mua nữa");
        assertEquals(before,stock()); assertEquals(1,count("orders","order_id=? and status='CANCELLED'",order.orderId()));
        assertEquals("00",online.receiveIpn(success).get("RspCode"));
        assertEquals("REVIEW",online.page(buyer.user(),order.orderId()).status()); assertEquals(before,stock());
    }
    @AfterAll void close() { if(em!=null) em.close(); if(factory!=null) factory.close(); }
}
