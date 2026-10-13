package VietFreshHub.Order;

import VietFreshHub.Auth.entity.*;
import VietFreshHub.Order.dto.*;
import VietFreshHub.Order.entity.*;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.exception.CustomerOrderException;
import VietFreshHub.Order.repository.*;
import VietFreshHub.Order.service.*;
import VietFreshHub.Order.service.impl.*;
import VietFreshHub.Payment.entity.*;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Shop.entity.Shop;
import VietFreshHub.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false,properties={
        "spring.datasource.url=jdbc:h2:mem:fe052-orders;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true", "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=VietFreshHub.Order.CustomerOrderReadPersistenceTest$SqlCapture",
        "app.seed.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CustomerOrderServiceImpl.class,CustomerOrderQueryServiceImpl.class})
class CustomerOrderReadPersistenceTest {
    @Autowired EntityManager em;
    @Autowired CustomerOrderService history;
    @Autowired CustomerOrderQueryService details;
    @Autowired CustomerOrderReadRepository reads;
    @MockitoBean VietFreshHub.Product.service.CatalogService catalog;
    @MockitoBean VietFreshHub.Order.port.OrderInventoryReleasePort inventory;
    Long buyer,other,small,large,foreign;
    ProductVariant variant;
    public static class SqlCapture implements StatementInspector {
        static final List<String> statements=new ArrayList<>();
        public String inspect(String sql) { statements.add(sql.toLowerCase(Locale.ROOT)); return sql; }
    }
    @BeforeEach void fixture() {
        User customer=user("owner@fe052.invalid"), stranger=user("other@fe052.invalid"); buyer=customer.getUserId(); other=stranger.getUserId();
        for(int i=0;i<25;i++) order(customer,"HISTORY-"+i,LocalDateTime.of(2026,10,1,12,0),Order.Status.values()[i%6]);
        foreign=order(stranger,"HISTORY-PRIVATE",LocalDateTime.of(2026,10,2,12,0),Order.Status.PENDING).getOrderId();
        small=detailOrder(customer,"DETAIL-SMALL",1,1); large=detailOrder(customer,"DETAIL-LARGE",12,3);
        em.flush(); em.clear(); SqlCapture.statements.clear();
    }
    User user(String email) { var user=new User(); user.setEmail(email); user.setFullName("Khách kiểm thử"); em.persist(user); return user; }
    Order order(User customer,String code,LocalDateTime date,Order.Status status) {
        var order=new Order(); order.setCustomer(customer); order.setOrderCode(code); order.setPlacedAt(date); order.setStatus(status);
        order.setGrandTotal(new BigDecimal("20000")); em.persist(order); return order;
    }
    Long detailOrder(User customer,String code,int shopCount,int itemCount) {
        Order order=order(customer,code,LocalDateTime.of(2026,10,10,19,30),Order.Status.PENDING);
        var address=new OrderAddress(); address.setOrder(order); address.setRecipientName("Người nhận snapshot"); address.setPhone("0901234567");
        address.setProvince("Hà Nội"); address.setWard("Phường mẫu"); address.setAddressLine("Địa chỉ đã lưu");
        address.setFormattedAddress("Địa chỉ snapshot đầy đủ"); em.persist(address);
        BigDecimal subtotal=BigDecimal.ZERO,discount=BigDecimal.ZERO,shipping=BigDecimal.ZERO;
        for(int s=0;s<shopCount;s++) {
            var shop=new Shop(); shop.setShopName(code+" Shop "+s); em.persist(shop);
            var product=new Product(); product.setShop(shop); product.setName("Tên catalog hiện tại"); product.setSlug(code+"-"+s); em.persist(product);
            variant=new ProductVariant(); variant.setProduct(product); variant.setSku(code+"-SKU-"+s); variant.setVariantName("Phân loại hiện tại");
            variant.setUnit("kg"); variant.setThumbnailUrl("/images/buoi-da-xanh.jpg"); variant.setPrice(new BigDecimal("30000")); em.persist(variant);
            var child=new ShopOrder(); child.setOrder(order); child.setShop(shop); child.setStatus(ShopOrder.Status.CONFIRMED);
            child.setSubtotal(new BigDecimal("20000.25").multiply(BigDecimal.valueOf(itemCount)));
            child.setDiscountTotal(new BigDecimal("500.25").multiply(BigDecimal.valueOf(itemCount))); child.setShippingFee(new BigDecimal("20000"));
            child.setTotalAmount(child.getSubtotal().subtract(child.getDiscountTotal()).add(child.getShippingFee())); em.persist(child);
            for(int i=0;i<itemCount;i++) {
                var item=new OrderItem(); item.setShopOrder(child); item.setVariant(variant); item.setProductNameSnapshot("Tên đã mua "+i);
                item.setVariantNameSnapshot("Phân loại đã mua"); item.setSkuSnapshot("SNAPSHOT-SKU-"+i); item.setQuantity(1);
                item.setUnitPrice(new BigDecimal("20000.25")); item.setDiscountAmount(new BigDecimal("500.25")); item.setTotalAmount(new BigDecimal("19500")); em.persist(item);
            }
            subtotal=subtotal.add(child.getSubtotal()); discount=discount.add(child.getDiscountTotal()); shipping=shipping.add(child.getShippingFee());
        }
        order.setSubtotal(subtotal); order.setDiscountTotal(discount); order.setShippingFee(shipping); order.setGrandTotal(subtotal.subtract(discount).add(shipping));
        var method=new PaymentMethod(); method.setMethodCode(code+"-COD"); method.setName(code+" COD"); em.persist(method);
        var payment=new Payment(); payment.setOrder(order); payment.setPaymentMethod(method); payment.setAmount(order.getGrandTotal()); em.persist(payment);
        return order.getOrderId();
    }
    OrderFilterRequest filter() { var filter=new OrderFilterRequest(); filter.setKeyword("HISTORY-"); return filter; }
    @Test void paginationUsesDatabaseLimitProjectionAndStableSort() {
        var page=history.history(buyer,filter()); assertEquals(25,page.getTotalElements()); assertEquals(10,page.getContent().size()); assertEquals(3,page.getTotalPages());
        assertTrue(SqlCapture.statements.stream().anyMatch(s -> s.contains("fetch first")),"SQL must paginate at the database: "+SqlCapture.statements);
        var ids=page.getContent().stream().map(OrderListRow::orderId).toList(); var sorted=new ArrayList<>(ids); sorted.sort(Comparator.reverseOrder()); assertEquals(sorted,ids);
        var next=filter(); next.setPage(3); assertEquals(5,history.history(buyer,next).getContent().size());
        assertTrue(SqlCapture.statements.stream().anyMatch(sql -> sql.contains("offset") && sql.contains("fetch first")));
    }
    @Test void combinedFiltersKeepOnlyOwnedOrdersAndLiteralKeyword() {
        var filter=filter(); filter.setStatus("CONFIRMED"); filter.setFromDate(LocalDate.of(2026,10,1)); filter.setToDate(LocalDate.of(2026,10,1));
        assertEquals(4,history.history(buyer,filter).getTotalElements());
        filter.setStatus(""); filter.setKeyword("HISTORY-PRIVATE"); assertEquals(0,history.history(buyer,filter).getTotalElements());
        filter.setKeyword("%"); assertEquals(0,history.history(buyer,filter).getTotalElements());
        filter.setKeyword("  history-  "); assertEquals(25,history.history(buyer,filter).getTotalElements()); assertEquals("history-",filter.getKeyword());
    }
    @Test void vietnamCalendarDayUsesInclusiveStartAndExclusiveNextDay() {
        var owner=em.getReference(User.class,buyer);
        order(owner,"BOUNDARY-BEFORE",LocalDateTime.of(2026,10,31,16,59,59),Order.Status.PENDING);
        order(owner,"BOUNDARY-START",LocalDateTime.of(2026,10,31,17,0),Order.Status.PENDING);
        order(owner,"BOUNDARY-END",LocalDateTime.of(2026,11,1,16,59,59),Order.Status.PENDING);
        order(owner,"BOUNDARY-AFTER",LocalDateTime.of(2026,11,1,17,0),Order.Status.PENDING); em.flush();
        var filter=new OrderFilterRequest(); filter.setKeyword("BOUNDARY-"); filter.setFromDate(LocalDate.of(2026,11,1)); filter.setToDate(LocalDate.of(2026,11,1));
        assertEquals(Set.of("BOUNDARY-START","BOUNDARY-END"),history.history(buyer,filter).getContent().stream().map(OrderListRow::orderCode).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void outOfRangePageReturnsLastDatabasePageAndEmptyReturnsFirstPage() {
        var filter=filter(); filter.setPage(1000); var page=history.history(buyer,filter); assertEquals(2,page.getNumber()); assertEquals(5,page.getContent().size());
        filter.setKeyword("NO-MATCH"); assertEquals(0,history.history(buyer,filter).getNumber());
    }
    @Test void invalidFiltersAreRejectedBeforeQuerying() {
        var filter=filter(); filter.setStatus("DELIVERED"); assertThrows(CustomerOrderException.class,() -> history.history(buyer,filter));
        filter.setStatus(""); filter.setFromDate(LocalDate.of(2026,10,2)); filter.setToDate(LocalDate.of(2026,10,1)); assertThrows(CustomerOrderException.class,() -> history.history(buyer,filter));
        filter.setFromDate(null); filter.setToDate(LocalDate.MAX); assertThrows(CustomerOrderException.class,() -> history.history(buyer,filter));
        assertTrue(SqlCapture.statements.isEmpty());
    }
    @Test void sqlDatetimeDateLimitsDoNotOverflow() {
        var filter=filter(); filter.setFromDate(LocalDate.of(1,1,1)); filter.setToDate(LocalDate.of(9999,12,31)); assertEquals(25,history.history(buyer,filter).getTotalElements());
    }
    @Test void foreignMissingAndInvalidOrdersCannotExposeDetails() {
        assertThrows(ResourceNotFoundException.class,() -> details.getDetails(buyer,foreign));
        assertThrows(ResourceNotFoundException.class,() -> details.getDetails(other,small));
        assertThrows(ResourceNotFoundException.class,() -> details.getDetails(buyer,Long.MAX_VALUE));
        assertThrows(ResourceNotFoundException.class,() -> details.getDetails(buyer,0L));
        assertTrue(reads.findItems(buyer,foreign).isEmpty()); assertTrue(reads.findShops(buyer,foreign).isEmpty()); assertTrue(reads.findPayments(buyer,foreign).isEmpty());
    }
    @Test void detailSnapshotsAndStoredTotalsSurviveCatalogChanges() {
        var loaded=em.createQuery("select i from OrderItem i where i.shopOrder.order.orderId=:id",OrderItem.class).setParameter("id",small).getSingleResult();
        loaded.getVariant().setPrice(new BigDecimal("90000")); loaded.getVariant().setVariantName("Đổi phân loại"); loaded.getVariant().getProduct().setName("Đổi tên catalog"); em.flush(); em.clear();
        var result=details.getDetails(buyer,small); var item=result.shops().getFirst().items().getFirst();
        assertEquals("Tên đã mua 0",item.productName()); assertEquals("Phân loại đã mua",item.variantName()); assertEquals("SNAPSHOT-SKU-0",item.sku());
        assertEquals(new BigDecimal("20000.25"),item.unitPrice()); assertEquals(new BigDecimal("500.25"),item.discountAmount());
        assertEquals("Địa chỉ snapshot đầy đủ",result.addressText()); assertEquals(new BigDecimal("39500.00"),result.grandTotal());
        assertEquals(new BigDecimal("39500.00"),result.shops().getFirst().totalAmount()); assertEquals(new BigDecimal("20000.00"),result.shippingFee());
        assertEquals("Chưa thu tiền",result.paymentStatusLabel()); assertEquals("PENDING",result.payments().getFirst().status()); assertNull(result.payments().getFirst().paidAt());
    }
    @Test void detailQueryCountDoesNotGrowWithShopOrItemCount() {
        var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); stats.clear(); em.clear();
        var first=details.getDetails(buyer,small); long smallQueries=stats.getPrepareStatementCount(); assertEquals(1,first.shops().size());
        em.clear(); stats.clear(); var many=details.getDetails(buyer,large); assertEquals(12,many.shops().size()); assertEquals(36,many.shops().stream().mapToInt(s -> s.items().size()).sum());
        assertEquals(5,smallQueries); assertEquals(smallQueries,stats.getPrepareStatementCount());
        details.getCartBadgeTotal(buyer); assertEquals(6,stats.getPrepareStatementCount());
    }
    @Test void cancelledOrderStillShowsActualRefundAndPaymentRecords() {
        var order=em.find(Order.class,small); order.setStatus(Order.Status.CANCELLED); order.setPaymentStatus(Order.PaymentState.REFUNDED);
        var payment=em.createQuery("select p from Payment p where p.order.orderId=:id",Payment.class).setParameter("id",small).getSingleResult();
        payment.setStatus(Payment.Status.REFUNDED); payment.setPaidAt(LocalDateTime.of(2026,10,10,20,1)); em.flush(); em.clear();
        var result=details.getDetails(buyer,small); assertEquals("Đã hủy",result.statusLabel()); assertEquals("Đã hoàn tiền",result.paymentStatusLabel());
        assertEquals("REFUNDED",result.payments().getFirst().status()); assertEquals(LocalDateTime.of(2026,10,11,3,1),result.payments().getFirst().paidAt());
    }

    @Test void paymentAttemptsStaySeparateAndUseActualStatuses() {
        var order=em.find(Order.class,small); order.setPaymentStatus(Order.PaymentState.PAID);
        var original=em.createQuery("select p from Payment p where p.order.orderId=:id",Payment.class).setParameter("id",small).getSingleResult();
        var failed=new Payment(); failed.setOrder(order); failed.setPaymentMethod(original.getPaymentMethod()); failed.setAmount(order.getGrandTotal()); failed.setStatus(Payment.Status.FAILED); em.persist(failed);
        var paid=new Payment(); paid.setOrder(order); paid.setPaymentMethod(original.getPaymentMethod()); paid.setAmount(order.getGrandTotal()); paid.setStatus(Payment.Status.PAID); paid.setPaidAt(LocalDateTime.of(2026,10,10,20,0)); em.persist(paid); em.flush(); em.clear();
        var result=details.getDetails(buyer,small); assertEquals("Đã thanh toán",result.paymentStatusLabel());
        assertEquals(List.of("PAID","FAILED","PENDING"),result.payments().stream().map(OrderDetailsResponse.PaymentInfo::status).toList());
    }
    @Test void oneSidedDateFiltersAreSupported() {
        var filter=filter(); filter.setFromDate(LocalDate.of(2026,10,1)); assertEquals(25,history.history(buyer,filter).getTotalElements());
        filter.setFromDate(LocalDate.of(2026,10,2)); assertEquals(0,history.history(buyer,filter).getTotalElements());
        filter.setFromDate(null); filter.setToDate(LocalDate.of(2026,10,1)); assertEquals(25,history.history(buyer,filter).getTotalElements());
        filter.setToDate(LocalDate.of(2026,9,30)); assertEquals(0,history.history(buyer,filter).getTotalElements());
    }
    @Test void cartBadgeCountsOnlyOwnersActiveCartWithoutIntegerOverflow() {
        var variants=em.createQuery("select v from ProductVariant v",ProductVariant.class).setMaxResults(2).getResultList();
        var cart=new VietFreshHub.Cart.entity.Cart(); cart.setUser(em.getReference(User.class,buyer)); em.persist(cart);
        var old=new VietFreshHub.Cart.entity.Cart(); old.setUser(em.getReference(User.class,buyer)); old.setStatus("CHECKED_OUT"); em.persist(old);
        var stranger=new VietFreshHub.Cart.entity.Cart(); stranger.setUser(em.getReference(User.class,other)); em.persist(stranger);
        for(int i=0;i<4;i++) {
            var item=new VietFreshHub.Cart.entity.CartItem(); item.setCart(i<2?cart:i==2?old:stranger); item.setVariant(variants.get(i%2));
            item.setQuantity(i==0?Integer.MAX_VALUE:i==1?2:100); item.setUnitPriceSnapshot(new BigDecimal("30000")); em.persist(item);
        }
        em.flush(); em.clear();
        assertEquals(2147483649L,details.getCartBadgeTotal(buyer)); assertEquals(100L,details.getCartBadgeTotal(other));
    }
}
