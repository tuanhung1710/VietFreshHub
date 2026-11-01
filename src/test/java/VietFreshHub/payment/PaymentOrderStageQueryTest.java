package VietFreshHub.payment;

import VietFreshHub.order.dto.CustomerOrderStage;
import VietFreshHub.order.dto.OrderListRow;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.payment.dto.PaymentHistoryRow;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.CustomerPaymentReadRepository;
import jakarta.persistence.Entity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the real JPQL projections, count queries and pagination on an isolated H2 database. */
class PaymentOrderStageQueryTest {
    record Scenario(int id, String parent, String payment, boolean online, String shops,
                    String delivery, String expected) {}
    static Stream<Scenario> scenarios() {
        return Stream.of(
            new Scenario(1,"PENDING","PENDING",true,"PENDING",null,"WAITING_PAYMENT"),
            new Scenario(2,"PENDING","PENDING",false,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(3,"PENDING","PAID",true,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(4,"PENDING","PENDING",false,"READY_FOR_DELIVERY",null,"AWAITING_DELIVERY"),
            new Scenario(5,"PENDING","PAID",false,"COMPLETED",null,"COMPLETED"),
            new Scenario(6,"CANCELLED","CANCELLED",false,"CANCELLED",null,"CANCELLED"),
            new Scenario(7,"PROCESSING","PENDING",false,"OUT_FOR_DELIVERY","FAILED","SHIPPING"),
            new Scenario(8,"COMPLETED","REFUNDED",true,"COMPLETED",null,"COMPLETED"),
            new Scenario(9,"PENDING","PENDING",false,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(10,"PENDING","FAILED",true,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(11,"PENDING","PENDING",false,"PREPARING",null,"PREPARING"),
            new Scenario(12,"PENDING","PENDING",false,"OUT_FOR_DELIVERY","OUT_FOR_DELIVERY","SHIPPING"),
            new Scenario(13,"COMPLETED","PAID",true,"COMPLETED,PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(14,"PARTIALLY_COMPLETED","PENDING",false,"COMPLETED,PREPARING",null,"PREPARING"),
            new Scenario(15,"PARTIALLY_COMPLETED","PENDING",false,"COMPLETED,READY_FOR_DELIVERY",null,"AWAITING_DELIVERY"),
            new Scenario(16,"PARTIALLY_COMPLETED","PENDING",false,"COMPLETED,OUT_FOR_DELIVERY",null,"SHIPPING"),
            new Scenario(17,"PARTIALLY_COMPLETED","PAID",false,"COMPLETED,CANCELLED",null,"COMPLETED"),
            new Scenario(18,"CANCELLED","PENDING",false,"CANCELLED,PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(19,"PENDING","CANCELLED",false,"CANCELLED,CANCELLED",null,"CANCELLED"),
            new Scenario(20,"PROCESSING","PENDING",false,"PREPARING,OUT_FOR_DELIVERY",null,"PREPARING"),
            new Scenario(21,"PROCESSING","PENDING",false,"READY_FOR_DELIVERY,OUT_FOR_DELIVERY",null,"AWAITING_DELIVERY"),
            new Scenario(22,"PROCESSING","PENDING",false,"PENDING,OUT_FOR_DELIVERY",null,"WAITING_CONFIRMATION"),
            new Scenario(23,"PROCESSING","PENDING",true,"OUT_FOR_DELIVERY",null,"SHIPPING"),
            new Scenario(24,"PENDING","PENDING",false,"CONFIRMED",null,"PREPARING"),
            new Scenario(25,"COMPLETED","PAID",false,"OUT_FOR_DELIVERY",null,"SHIPPING"),
            new Scenario(26,"PENDING","PENDING",false,"",null,"WAITING_CONFIRMATION"),
            new Scenario(27,"COMPLETED","PENDING",false,"COMPLETED",null,"COMPLETED"),
            new Scenario(28,"PENDING","PROCESSING",true,"PENDING",null,"WAITING_PAYMENT"),
            new Scenario(29,"PENDING","FAILED",true,"PENDING",null,"WAITING_PAYMENT"),
            new Scenario(30,"PENDING","CANCELLED",true,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(31,"PENDING","PAID",true,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(32,"PENDING","PENDING",false,"READY_FOR_DELIVERY","ACCEPTED","AWAITING_DELIVERY"),
            new Scenario(33,"PROCESSING","PENDING",false,"OUT_FOR_DELIVERY","DELIVERED","SHIPPING"),
            new Scenario(34,"COMPLETED","PARTIALLY_REFUNDED",true,"COMPLETED",null,"COMPLETED"),
            new Scenario(35,"PENDING","PENDING",false,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(36,"PENDING","PENDING",false,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(37,"PENDING","PENDING",false,"PENDING",null,"WAITING_CONFIRMATION"),
            new Scenario(38,"PENDING","PENDING",false,"READY_FOR_DELIVERY","PICKED_UP","SHIPPING"),
            new Scenario(39,"PROCESSING","PENDING",false,"OUT_FOR_DELIVERY","REDELIVERY_SCHEDULED","SHIPPING"),
            new Scenario(40,"PENDING","PENDING",false,"PENDING,PREPARING",null,"WAITING_CONFIRMATION")
        );
    }
    static SessionFactory factory;
    Session session;
    OrderRepository orders;
    CustomerPaymentReadRepository payments;
    @BeforeAll static void database() throws Exception {
        var config = new Configuration();
        config.setProperty("hibernate.connection.url", "jdbc:h2:mem:phase2Stages;DB_CLOSE_DELAY=-1");
        config.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        config.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bean : scanner.findCandidateComponents("VietFreshHub")) config.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
        factory = config.buildSessionFactory();
        try (var setup = factory.openSession()) {
            setup.doWork(connection -> {
                try (var sql = connection.createStatement()) {
                    sql.execute("create schema dbo");
                    sql.execute("create table dbo.orders(order_id bigint primary key, customer_id bigint, order_code varchar(50), status varchar(30), payment_status varchar(30), grand_total decimal(18,2), placed_at timestamp)");
                    sql.execute("create table dbo.payment_methods(payment_method_id int primary key, name varchar(50), method_code varchar(50))");
                    sql.execute("create table dbo.payments(payment_id bigint primary key,order_id bigint,payment_method_id int,amount decimal(18,2),status varchar(30),created_at timestamp,paid_at timestamp)");
                    sql.execute("create table dbo.shop_orders(shop_order_id bigint primary key,order_id bigint,status varchar(30))");
                    sql.execute("create table dbo.deliveries(delivery_id bigint primary key,shop_order_id bigint,status varchar(30))");
                    sql.execute("insert into dbo.payment_methods values(1,'COD','COD'),(2,'Online','VNPAY')");
                    for (var s : scenarios().toList()) {
                        String date = s.id()==35 ? "2026-10-19 16:59:59" : s.id()==36 ? "2026-10-20 17:00:00"
                                : s.id()==37 ? "2026-10-20 16:59:59" : "2026-10-19 17:00:00";
                        sql.execute("insert into dbo.orders values("+s.id()+","+(s.id()==9?99:7)+",'VF-"+s.id()+"','"+s.parent()+"','UNPAID',100,'"+date+"')");
                        sql.execute("insert into dbo.payments values("+s.id()+","+s.id()+","+(s.online()?2:1)+",100,'"+s.payment()+"','"+date+"',null)");
                        if (!s.shops().isEmpty()) {
                            int child=0;
                            for (String state : s.shops().split(",")) {
                                sql.execute("insert into dbo.shop_orders values("+(s.id()*10+child++)+","+s.id()+",'"+state+"')");
                            }
                        }
                        if (s.delivery()!=null) sql.execute("insert into dbo.deliveries values("+s.id()+","+(s.id()*10)+",'"+s.delivery()+"')");
                    }
                    sql.execute("insert into dbo.payments values(100,10,2,100,'PAID','2026-10-19 17:00:00','2026-10-19 17:00:00'),(101,31,2,100,'FAILED','2026-10-19 17:00:00',null)");
                    sql.execute("alter table dbo.payments add transaction_reference varchar(255)");
                    sql.execute("update dbo.payments set transaction_reference='VN-100' where payment_id=100");
                }
            });
        }
    }
    @BeforeEach void repositories() {
        session = factory.openSession();
        var repos = new JpaRepositoryFactory(session);
        orders = repos.getRepository(OrderRepository.class);
        payments = repos.getRepository(CustomerPaymentReadRepository.class);
    }
    @AfterEach void closeSession() { session.close(); }
    @AfterAll static void closeFactory() { if (factory != null) factory.close(); }

    @ParameterizedTest @MethodSource("scenarios")
    void detailAndListShareFulfillmentStage(Scenario s) {
        long owner = s.id()==9 ? 99L : 7L;
        assertEquals(s.expected(), orders.findCustomerStage((long)s.id(), owner).orElseThrow());
        var rows = orders.findHistory(owner, s.expected(), "", null, null, PageRequest.of(0,100));
        var row = rows.stream().filter(r -> r.orderId()==s.id()).findFirst().orElseThrow();
        assertEquals(CustomerOrderStage.valueOf(s.expected()).label(), row.statusLabel());
    }
    @ParameterizedTest @EnumSource(CustomerOrderStage.class)
    void orderFiltersAndCountStayInDatabase(CustomerOrderStage stage) {
        var expected = scenarios().filter(s -> s.id()!=9 && s.expected().equals(stage.name())).map(s -> (long)s.id()).sorted().toList();
        var first = orders.findHistory(7L,stage.name(),"",null,null,PageRequest.of(0,2,Sort.by("orderId")));
        assertEquals(expected.size(), first.getTotalElements());
        assertEquals(expected.stream().limit(2).toList(), first.stream().map(OrderListRow::orderId).toList());
        if (expected.size()>2) {
            var next = orders.findHistory(7L,stage.name(),"",null,null,PageRequest.of(1,2,Sort.by("orderId")));
            assertEquals(expected.stream().skip(2).limit(2).toList(), next.stream().map(OrderListRow::orderId).toList());
        }
    }
    @ParameterizedTest @EnumSource(Payment.Status.class)
    void paymentFiltersUseOnlyPaymentStatus(Payment.Status status) {
        var expected = new ArrayList<>(scenarios().filter(s -> s.id()!=9 && s.payment().equals(status.name())).map(s -> (long)s.id()).toList());
        if (status==Payment.Status.PAID) expected.add(100L);
        if (status==Payment.Status.FAILED) expected.add(101L);
        Collections.sort(expected);
        var page = payments.findHistory(7L,status,"",null,null,PageRequest.of(0,2,Sort.by("paymentId")));
        assertEquals(expected.size(), page.getTotalElements());
        assertEquals(expected.stream().limit(2).toList(), page.stream().map(PaymentHistoryRow::paymentId).toList());
        var all = payments.findHistory(7L,status,"",null,null,PageRequest.of(0,100));
        assertEquals(new HashSet<>(expected), new HashSet<>(all.stream().map(PaymentHistoryRow::paymentId).toList()));
        assertTrue(all.stream().allMatch(r -> r.status()==status));
    }
    @Test void paymentAttemptsRemainSeparateWithReferencesAndCombinedFilters() {
        var from = LocalDateTime.of(2026,10,19,17,0);
        var until = LocalDateTime.of(2026,10,20,17,0);
        var first = payments.findHistory(7L,null,"VF-10",from,until,PageRequest.of(0,1,Sort.by("paymentId")));
        var second = payments.findHistory(7L,null,"VF-10",from,until,PageRequest.of(1,1,Sort.by("paymentId")));
        assertEquals(2,first.getTotalElements()); assertEquals(2,second.getTotalElements());
        assertEquals(10L,first.getContent().getFirst().paymentId());
        assertEquals(100L,second.getContent().getFirst().paymentId());
        assertEquals(10L,first.getContent().getFirst().orderId());
        assertEquals(10L,second.getContent().getFirst().orderId());
        assertNull(first.getContent().getFirst().transactionReference());
        assertEquals("VN-100",second.getContent().getFirst().transactionReference());
        assertEquals(LocalDateTime.of(2026,10,20,0,0),second.getContent().getFirst().paidAtLocal());
        var paid = payments.findHistory(7L,Payment.Status.PAID,"VF-10",from,until,PageRequest.of(0,2));
        assertEquals(List.of(100L),paid.stream().map(PaymentHistoryRow::paymentId).toList());
        assertTrue(payments.findHistory(99L,null,"VF-10",from,until,PageRequest.of(0,2)).isEmpty());
    }
    @Test void combinedOrderFiltersKeepCustomerKeywordStageAndDates() {
        var from = LocalDateTime.of(2026,10,19,17,0);
        var until = LocalDateTime.of(2026,10,20,17,0);
        var page = orders.findHistory(7L,"WAITING_CONFIRMATION","VF-37",from,until,PageRequest.of(0,2));
        assertEquals(List.of(37L),page.stream().map(OrderListRow::orderId).toList());
        assertEquals(1,page.getTotalElements());
        assertTrue(orders.findHistory(7L,"SHIPPING","VF-37",from,until,PageRequest.of(0,2)).isEmpty());
        assertTrue(orders.findHistory(99L,"WAITING_CONFIRMATION","VF-37",from,until,PageRequest.of(0,2)).isEmpty());
    }
    @Test void allFiltersOwnershipAndHalfOpenDateBoundaries() {
        var from = LocalDateTime.of(2026,10,19,17,0);
        var until = LocalDateTime.of(2026,10,20,17,0);
        var expected = scenarios().filter(s -> s.id()!=9 && s.id()!=35 && s.id()!=36).map(s -> (long)s.id()).collect(java.util.stream.Collectors.toSet());
        var orderPage = orders.findHistory(7L,"","",from,until,PageRequest.of(0,100));
        assertEquals(expected, new HashSet<>(orderPage.stream().map(OrderListRow::orderId).toList()));
        var paymentPage = payments.findHistory(7L,null,"",from,until,PageRequest.of(0,100));
        expected.add(100L); expected.add(101L);
        assertEquals(expected,new HashSet<>(paymentPage.stream().map(PaymentHistoryRow::paymentId).toList()));
        assertTrue(orders.findCustomerStage(9L,7L).isEmpty());
        assertTrue(orders.findHistory(7L,"","VF-9",null,null,PageRequest.of(0,2)).isEmpty());
        assertTrue(payments.findHistory(7L,null,"VF-9",null,null,PageRequest.of(0,2)).isEmpty());
        assertEquals(1,orders.findHistory(99L,"","",null,null,PageRequest.of(0,2)).getTotalElements());
        assertEquals(1,payments.findHistory(99L,null,"",null,null,PageRequest.of(0,2)).getTotalElements());
    }
}
