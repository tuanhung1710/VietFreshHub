package VietFreshHub.order;

import VietFreshHub.order.dto.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.impl.CustomerOrderServiceImpl;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.exception.CustomerOrderException;
import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real batch queries, ownership and fixed query count on a private in-memory database. */
class CustomerOrderHistorySummaryTest {
    static SessionFactory factory;
    @BeforeAll static void database() throws Exception {
        var config = new Configuration();
        config.setProperty("hibernate.connection.url","jdbc:h2:mem:historyCards;DB_CLOSE_DELAY=-1");
        config.setProperty("hibernate.connection.driver_class","org.h2.Driver");
        config.setProperty("hibernate.hbm2ddl.auto","none");
        config.setProperty("hibernate.generate_statistics","true");
        config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bean : scanner.findCandidateComponents("VietFreshHub")) config.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
        factory = config.buildSessionFactory();
        try (var session = factory.openSession()) {
            session.doWork(connection -> {
                try (var sql = connection.createStatement()) {
                    sql.execute("create schema dbo");
                    // Create only the isolated fixture's tables; never export a schema to the app database.
                    for (Class<?> entity : List.of(Order.class,OrderAddress.class,ShopOrder.class,OrderItem.class,
                            OrderDeliveryView.class,Payment.class,PaymentMethod.class,
                            VietFreshHub.catalog.entity.CatalogVariant.class,VietFreshHub.shop.entity.Shop.class,
                            VietFreshHub.catalog.entity.CatalogProduct.class,VietFreshHub.auth.entity.User.class,
                            VietFreshHub.cart.entity.Cart.class,VietFreshHub.cart.entity.CartItem.class,
                            OrderStatusHistory.class,OrderDeliveryHistoryView.class)) {
                        Map<String,String> columns = new LinkedHashMap<>();
                        for (var field : entity.getDeclaredFields()) {
                            var column = field.getAnnotation(Column.class);
                            var join = field.getAnnotation(JoinColumn.class);
                            if (column == null && join == null) continue;
                            String name = join != null ? join.name() : column.name().isEmpty()
                                    ? field.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT) : column.name();
                            Class<?> type = field.getType();
                            if (join != null) type = Arrays.stream(type.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Id.class)).findFirst().orElseThrow().getType();
                            String ddlType = type==Long.class ? "bigint" : type==Integer.class || type==int.class ? "int"
                                    : type==BigDecimal.class ? "decimal(18,2) default 0" : type==LocalDateTime.class ? "timestamp"
                                    : type==boolean.class || type==Boolean.class ? "boolean default false" : "varchar(2000)";
                            columns.putIfAbsent(name,ddlType+(field.isAnnotationPresent(Id.class)?" primary key":""));
                        }
                        sql.execute("create table dbo."+entity.getAnnotation(Table.class).name()+"("+
                                columns.entrySet().stream().map(e -> e.getKey()+" "+e.getValue()).collect(java.util.stream.Collectors.joining(","))+")");
                    }
                    sql.execute("insert into dbo.orders(order_id,customer_id,order_code,status,payment_status,placed_at) values(10,7,'VF-10','PENDING','UNPAID',current_timestamp),(11,7,'VF-11','PARTIALLY_COMPLETED','PAID',current_timestamp),(12,99,'VF-12','PENDING','UNPAID',current_timestamp)");
                    sql.execute("insert into dbo.shops(shop_id,shop_name,status) values(1,'Vườn Xanh','ACTIVE'),(2,'Fresh Market','ACTIVE')");
                    sql.execute("insert into dbo.shop_orders(shop_order_id,order_id,shop_id,status) values(100,10,1,'PENDING'),(110,11,1,'COMPLETED'),(111,11,2,'OUT_FOR_DELIVERY'),(120,12,1,'PENDING')");
                    sql.execute("insert into dbo.product_variants(variant_id,variant_name,thumbnail_url) values(1,'Tên mới','/fruit.png')");
                    for (int n=1;n<=4;n++) sql.execute("insert into dbo.order_items(order_item_id,shop_order_id,variant_id,product_name_snapshot,variant_name_snapshot,quantity) values("+n+",100,1,'Tên lúc mua','Loại lúc mua',2)");
                    sql.execute("insert into dbo.order_items(order_item_id,shop_order_id,variant_id,product_name_snapshot,variant_name_snapshot,quantity) values(5,111,1,'Táo','Hộp',3)");
                    sql.execute("insert into dbo.payment_methods(payment_method_id,name,method_code,status) values(1,'Thanh toán khi nhận hàng','COD','ACTIVE'),(2,'Online','VNPAY','ACTIVE')");
                    sql.execute("insert into dbo.payments(payment_id,order_id,payment_method_id,status,amount,created_at) values(1,10,1,'PENDING',100,current_timestamp),(2,11,2,'PAID',200,current_timestamp),(3,12,1,'PENDING',100,current_timestamp)");
                    sql.execute("insert into dbo.deliveries(delivery_id,shop_order_id,status) values(1,111,'FAILED')");
                    sql.execute("insert into dbo.order_addresses(order_address_id,order_id,recipient_name,phone,formatted_address) values(1,11,'Nguyễn An','0900000000','Địa chỉ lúc đặt')");
                    sql.execute("update dbo.orders set subtotal=380000,discount_total=10000,shipping_fee=11000,grand_total=381000 where order_id=11");
                    sql.execute("update dbo.order_items set unit_price=50000,total_amount=149000,discount_amount=1000 where order_item_id=5");
                    sql.execute("update dbo.payments set transaction_reference='VN-2' where payment_id=2");
                    sql.execute("insert into dbo.users(user_id,email,full_name,status) values(7,'buyer@test.invalid','Buyer','ACTIVE'),(99,'other@test.invalid','Other','ACTIVE')");
                    sql.execute("insert into dbo.order_addresses(order_address_id,order_id,recipient_name,phone,formatted_address) values(2,10,'Nguyễn An','0900000000','Địa chỉ COD lúc đặt')");
                    sql.execute("update dbo.orders set subtotal=400000,shipping_fee=20000,grand_total=420000 where order_id=10");
                    sql.execute("update dbo.shop_orders set subtotal=400000,shipping_fee=20000,total_amount=420000 where shop_order_id=100");
                    sql.execute("update dbo.order_items set unit_price=50000,total_amount=100000 where shop_order_id=100");
                    sql.execute("update dbo.payments set amount=420000 where payment_id=1");
                    sql.execute("insert into dbo.order_status_history(history_id,shop_order_id,status,created_at) values(1,100,'PENDING','2026-10-20 03:00:00'),(2,110,'COMPLETED','2026-10-20 05:00:00')");
                    sql.execute("insert into dbo.delivery_status_history(history_id,delivery_id,status,created_at) values(1,1,'FAILED','2026-10-20 06:00:00')");
                    sql.execute("update dbo.shop_orders set subtotal=200000,discount_total=9000,shipping_fee=6000,total_amount=197000 where shop_order_id=110");
                    sql.execute("update dbo.shop_orders set subtotal=180000,discount_total=1000,shipping_fee=5000,total_amount=184000 where shop_order_id=111");
                    sql.execute("insert into dbo.order_items(order_item_id,shop_order_id,variant_id,product_name_snapshot,variant_name_snapshot,quantity,unit_price,discount_amount,total_amount) values(6,111,1,'Ổi','Túi',1,30000,0,30000),(7,110,1,'Cam','Hộp',1,200000,9000,191000)");
                    sql.execute("update dbo.payments set amount=381000 where payment_id=2");
                }
            });
        }
    }
    @AfterAll static void shutdown() { if (factory!=null) factory.close(); }

    @Test void batchesSnapshotsMethodsDeliveryAndExactCancellationPolicyWithoutNPlusOne() {
        try (var session = factory.openSession()) {
            var repos = new JpaRepositoryFactory(session);
            var service = new CustomerOrderServiceImpl(repos.getRepository(OrderRepository.class),
                    repos.getRepository(ShopOrderRepository.class),repos.getRepository(PaymentRepository.class),
                    repos.getRepository(OrderDeliveryReadRepository.class),null,null,null,null,null,null,null,null,null,null,null,null);
            factory.getStatistics().clear();
            var cards = service.historySummaries(7L,List.of(10L,11L,12L));
            assertEquals(Set.of(10L,11L),cards.keySet());
            var cod = cards.get(10L);
            assertEquals(4,cod.productCount()); assertEquals(8,cod.unitCount());
            assertEquals(3,cod.products().size());
            assertEquals("Tên lúc mua",cod.products().getFirst().productName());
            assertEquals("Loại lúc mua",cod.products().getFirst().variantName());
            assertEquals("/fruit.png",cod.products().getFirst().imageUrl());
            assertEquals("Chờ thu COD",cod.payments().getFirst().statusLabel());
            assertTrue(cod.cancellable()); assertTrue(cod.reorderable()); assertTrue(cod.trackable());
            var mixed = cards.get(11L);
            assertEquals(2,mixed.shops().size());
            assertEquals("Hoàn thành",mixed.shops().getFirst().fulfillmentLabel());
            assertEquals("Giao hàng thất bại",mixed.shops().get(1).deliveryLabel());
            assertEquals("Đã thanh toán",mixed.payments().getFirst().statusLabel());
            assertFalse(mixed.cancellable());
            assertEquals(4,factory.getStatistics().getPrepareStatementCount(),"No lazy per-order/product queries");
        }
    }
    @Test void emptyOrForeignPageDoesNotLoadExtraData() {
        try (var session = factory.openSession()) {
            var repos = new JpaRepositoryFactory(session);
            var service = new CustomerOrderServiceImpl(repos.getRepository(OrderRepository.class),null,null,null,
                    null,null,null,null,null,null,null,null,null,null,null,null);
            factory.getStatistics().clear();
            assertTrue(service.historySummaries(7L,List.of()).isEmpty());
            assertEquals(0,factory.getStatistics().getPrepareStatementCount());
            assertTrue(service.historySummaries(7L,List.of(12L)).isEmpty());
            assertEquals(1,factory.getStatistics().getPrepareStatementCount());
            assertThrows(CustomerOrderException.class,() -> service.historySummaries(7L,Collections.nCopies(11,10L)));
        }
    }
    @Test void detailProjectionsAreOwnedUseSnapshotsAndHaveFixedQueryCount() {
        try (var session=factory.openSession()) {
            var repos=new JpaRepositoryFactory(session);
            var reads=repos.getRepository(CustomerOrderReadRepository.class);
            var service=new VietFreshHub.order.service.impl.CustomerOrderQueryServiceImpl(repos.getRepository(OrderRepository.class),
                    repos.getRepository(OrderAddressRepository.class),reads);
            factory.getStatistics().clear();
            var detail=service.getDetails(7L,11L);
            assertEquals("Vận chuyển",detail.statusLabel());
            assertEquals("Địa chỉ lúc đặt",detail.addressText());
            assertEquals(0,new BigDecimal("381000").compareTo(detail.grandTotal()));
            assertEquals(0,new BigDecimal("380000").compareTo(detail.subtotal()));
            assertEquals(2,detail.shops().size());
            assertNull(detail.shops().getFirst().delivery());
            var second=detail.shops().get(1);
            assertEquals("Giao hàng thất bại",second.delivery().statusLabel());
            assertEquals("Táo",second.items().getFirst().productName());
            assertEquals(0,new BigDecimal("50000").compareTo(second.items().getFirst().unitPrice()));
            assertEquals(0,new BigDecimal("149000").compareTo(second.items().getFirst().totalAmount()));
            assertEquals("VN-2",detail.payments().getFirst().transactionReference());
            assertNotNull(detail.payments().getFirst().createdAt());
            assertEquals(7,factory.getStatistics().getPrepareStatementCount());
            assertThrows(VietFreshHub.exception.ResourceNotFoundException.class,() -> service.getDetails(99L,11L));
            assertTrue(reads.findDeliveries(99L,11L).isEmpty());
            assertTrue(reads.findPayments(99L,11L).isEmpty());
            assertTrue(reads.findItems(99L,11L).isEmpty());
            assertTrue(reads.findShops(99L,11L).isEmpty());
        }
    }
    CustomerOrderServiceImpl lifecycle(org.hibernate.Session session) {
        var repos=new JpaRepositoryFactory(session);
        return new CustomerOrderServiceImpl(repos.getRepository(OrderRepository.class),repos.getRepository(ShopOrderRepository.class),
                repos.getRepository(PaymentRepository.class),repos.getRepository(OrderDeliveryReadRepository.class),
                repos.getRepository(OrderDeliveryHistoryReadRepository.class),repos.getRepository(OrderStatusHistoryRepository.class),
                mock(VietFreshHub.delivery.service.DeliveryAssignmentService.class),repos.getRepository(VietFreshHub.auth.repository.UserRepository.class),
                null,null,null,null,null,null,session,mock(org.springframework.context.ApplicationEventPublisher.class));
    }
    VietFreshHub.customer.CustomerHttpHarness customerHttp(org.hibernate.Session session) {
        var repos=new JpaRepositoryFactory(session);
        var authUsers=mock(VietFreshHub.auth.repository.UserRepository.class);
        var roleRepo=mock(VietFreshHub.auth.repository.UserRoleRepository.class);
        var buyer=new VietFreshHub.auth.entity.User();buyer.setUserId(7L);buyer.setStatus("ACTIVE");buyer.setEmail("buyer@test.invalid");
        when(authUsers.findByEmailIgnoreCase(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(roleRepo.findRoleNamesByUserId(7L)).thenReturn(List.of("ROLE_CUSTOMER"));
        var details=new VietFreshHub.order.service.impl.CustomerOrderQueryServiceImpl(repos.getRepository(OrderRepository.class),
                repos.getRepository(OrderAddressRepository.class),repos.getRepository(CustomerOrderReadRepository.class));
        var paymentService=new VietFreshHub.payment.service.impl.CustomerPaymentHistoryServiceImpl(
                repos.getRepository(VietFreshHub.payment.repository.CustomerPaymentReadRepository.class));
        return new VietFreshHub.customer.CustomerHttpHarness(authUsers,roleRepo,mock(VietFreshHub.cart.service.CartService.class),
                mock(VietFreshHub.catalog.service.CatalogService.class),mock(VietFreshHub.checkout.service.CheckoutService.class),
                mock(VietFreshHub.checkout.service.CheckoutShippingQuoteService.class),new VietFreshHub.checkout.service.CheckoutDraftStore(),
                mock(VietFreshHub.customer.service.CustomerAddressService.class),lifecycle(session),details,paymentService);
    }
    org.springframework.test.web.servlet.MvcResult visit(VietFreshHub.customer.CustomerHttpHarness http,String path) throws Exception {
        return http.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("buyer@test.invalid").roles("CUSTOMER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
    }
    @Test void samePersistedCodOrderSurvivesNavigationAcrossAllFivePagesWithoutWrites() throws Exception {
        try (var session=factory.openSession();var http=customerHttp(session)) {
            factory.getStatistics().clear();
            var listModel=visit(http,"/customer/orders").getModelAndView().getModel();
            var page=(org.springframework.data.domain.Page<?>)listModel.get("orderPage");
            var row=(OrderListRow)page.stream().filter(r -> ((OrderListRow)r).orderId()==10L).findFirst().orElseThrow();
            var summary=(OrderHistorySummary)((Map<?,?>)listModel.get("orderSummaries")).get(10L);
            var detailModel=visit(http,"/customer/orders/10").getModelAndView().getModel();
            var detail=(OrderDetailsResponse)detailModel.get("order");
            assertEquals(summary.cancellable(),((OrderActionsResponse)detailModel.get("actions")).cancellable());
            assertTrue(summary.cancellable());
            var confirmation=(OrderDetailsResponse)visit(http,"/customer/orders/10/confirmation").getModelAndView().getModel().get("order");
            var tracking=(OrderTrackingResponse)visit(http,"/customer/orders/10/tracking").getModelAndView().getModel().get("tracking");
            var paymentPage=(org.springframework.data.domain.Page<?>)visit(http,"/customer/payments?keyword=VF-10&status=PENDING")
                    .getModelAndView().getModel().get("paymentPage");
            var payment=(VietFreshHub.payment.dto.PaymentHistoryRow)paymentPage.getContent().getFirst();
            assertEquals("WAITING_CONFIRMATION",row.stage());assertEquals("Chờ xác nhận",detail.statusLabel());
            assertEquals(row.orderCode(),detail.orderCode());assertEquals(detail,confirmation);
            assertEquals(detail.orderCode(),tracking.orderCode());assertEquals(detail.orderCode(),payment.orderCode());
            assertEquals(row.grandTotal(),detail.grandTotal());assertEquals(detail.grandTotal(),payment.amount());
            assertEquals("Chờ thu COD",summary.payments().getFirst().statusLabel());
            assertEquals(VietFreshHub.payment.entity.Payment.Status.PENDING,payment.status());assertNull(payment.paidAt());
            assertEquals("Chưa thu tiền",detail.paymentStatusLabel());
            assertEquals(summary.unitCount(),detail.shops().stream().flatMap(shop -> shop.items().stream()).mapToLong(OrderDetailsResponse.Item::quantity).sum());
            assertEquals("Tên lúc mua",detail.shops().getFirst().items().getFirst().productName());
            assertEquals(row.placedAtLocal(),detail.placedAt());
            var mixedModel=visit(http,"/customer/orders/11").getModelAndView().getModel();
            var mixed=(OrderDetailsResponse)mixedModel.get("order");
            assertEquals("Vận chuyển",mixed.statusLabel());
            assertEquals("COMPLETED",mixed.shops().getFirst().statusCode());
            assertEquals("OUT_FOR_DELIVERY",mixed.shops().get(1).statusCode());
            assertEquals("FAILED",mixed.shops().get(1).delivery().status());
            assertEquals(0,mixed.grandTotal().compareTo(mixed.shops().stream().map(OrderDetailsResponse.ShopGroup::totalAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));
            assertFalse(((OrderActionsResponse)mixedModel.get("actions")).cancellable());
            assertEquals(0,factory.getStatistics().getEntityInsertCount());
            assertEquals(0,factory.getStatistics().getEntityUpdateCount());
            assertEquals(0,factory.getStatistics().getEntityDeleteCount());
            assertEquals(3,((Number)session.createNativeQuery("select count(*) from dbo.orders",Integer.class).getSingleResult()).intValue());
            assertEquals(3,((Number)session.createNativeQuery("select count(*) from dbo.payments",Integer.class).getSingleResult()).intValue());
        }
    }
    @Test void realHttpOwnershipProtectsDetailsTrackingPaymentsCancelAndReorder() throws Exception {
        try (var session=factory.openSession();var http=customerHttp(session)) {
            var user=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("buyer@test.invalid").roles("CUSTOMER");
            for (String path:List.of("/customer/orders/12","/customer/orders/12/tracking","/customer/orders/12/confirmation")) {
                http.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).with(user))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
            }
            var result=visit(http,"/customer/payments?customerId=99&keyword=VF-12");
            assertTrue(((org.springframework.data.domain.Page<?>)result.getModelAndView().getModel().get("paymentPage")).isEmpty());
            var transaction=session.beginTransaction();
            try {
                for (String suffix:List.of("cancel","reorder")) {
                    http.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/customer/orders/12/"+suffix).with(user)
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
                    http.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/customer/orders/10/"+suffix).with(user))
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
                }
            } finally { transaction.rollback(); }
        }
    }
    @Test void measureCustomerReadQueryCountsInColdPersistenceContexts() throws Exception {
        Map<String,Long> counts=new LinkedHashMap<>();
        for (var entry:Map.of("myOrders","/customer/orders","payments","/customer/payments", "detail","/customer/orders/11",
                "tracking","/customer/orders/11/tracking").entrySet()) {
            try (var session=factory.openSession();var http=customerHttp(session)) {
                factory.getStatistics().clear();
                visit(http,entry.getValue());
                counts.put(entry.getKey(),factory.getStatistics().getPrepareStatementCount());
                assertEquals(0,factory.getStatistics().getEntityInsertCount());
                assertEquals(0,factory.getStatistics().getEntityUpdateCount());
            }
        }
        assertTrue(counts.get("myOrders")<=7,counts.toString());
        assertTrue(counts.get("payments")<=3,counts.toString());
        assertTrue(counts.get("detail")<=18,counts.toString());
        assertTrue(counts.get("tracking")<=6,counts.toString());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(".document-analysis"));
        java.nio.file.Files.writeString(java.nio.file.Path.of(".document-analysis/phase6-query-counts.json"),
                counts.entrySet().stream().map(e -> "\""+e.getKey()+"\":"+e.getValue()).collect(java.util.stream.Collectors.joining(",","{","}")));
    }
}
