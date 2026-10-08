package VietFreshHub.Checkout;

import VietFreshHub.Auth.entity.*;
import VietFreshHub.Cart.entity.*;
import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Checkout.dto.*;
import VietFreshHub.Checkout.entity.CheckoutStockMovement;
import VietFreshHub.Checkout.exception.CheckoutException;
import VietFreshHub.Checkout.service.CheckoutService;
import VietFreshHub.Inventory.entity.InventoryBatch;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.entity.OrderItem;
import VietFreshHub.Order.entity.ShopOrder;
import VietFreshHub.Order.event.OrderPlacedEvent;
import VietFreshHub.Order.service.CustomerOrderQueryService;
import VietFreshHub.Payment.entity.Payment;
import VietFreshHub.Payment.entity.PaymentMethod;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Shop.entity.Shop;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.datasource.url=jdbc:h2:mem:checkouttest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "app.checkout.shipping-fee-per-shop=20000.00"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({VietFreshHub.Checkout.service.impl.CheckoutServiceImpl.class,VietFreshHub.Checkout.adapter.JpaInventoryReservationAdapter.class,
        VietFreshHub.Checkout.adapter.FixedShippingQuoteAdapter.class,VietFreshHub.Cart.service.impl.CartServiceImpl.class,
        VietFreshHub.Product.service.impl.CatalogServiceImpl.class,VietFreshHub.Order.service.impl.CustomerOrderQueryServiceImpl.class,
        CheckoutPersistenceTest.Beans.class,VietFreshHub.Checkout.service.impl.CheckoutAddressServiceImpl.class,
        org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class,
        VietFreshHub.Order.service.impl.CustomerOrderServiceImpl.class,VietFreshHub.Order.adapter.JpaOrderInventoryReleaseAdapter.class})
class CheckoutPersistenceTest {
    @Autowired CheckoutService checkout;
    @Autowired CartService carts;
    @Autowired CustomerOrderQueryService orderQueries;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Tracker tracker;
    @Autowired VietFreshHub.Checkout.service.CheckoutAddressService checkoutAddresses;
    @Autowired VietFreshHub.Order.service.CustomerOrderService customerOrders;
    TransactionTemplate tx;
    Long buyerId,otherBuyerId,addressId,otherAddressId,cartId,firstItemId,firstVariantId,firstBatchId,secondBatchId,firstShopId;

    @TestConfiguration static class Beans { @Bean Tracker tracker() { return new Tracker(); } }
    static class Tracker {
        final List<OrderPlacedEvent> events=new CopyOnWriteArrayList<>();
        final List<VietFreshHub.Order.event.OrderCancelledEvent> cancellations=new CopyOnWriteArrayList<>();
        @TransactionalEventListener public void committed(OrderPlacedEvent event) { events.add(event); }
        @TransactionalEventListener public void cancelled(VietFreshHub.Order.event.OrderCancelledEvent event) { cancellations.add(event); }
    }
    @BeforeEach void fixture() {
        tx=new TransactionTemplate(manager); tracker.events.clear(); tracker.cancellations.clear();
        tx.executeWithoutResult(status -> {
            User buyer=user("checkout.owner@example.com"); User other=user("checkout.other@example.com");
            buyerId=buyer.getUserId(); otherBuyerId=other.getUserId(); addressId=address(buyer,"Đường gốc").getAddressId();
            otherAddressId=address(other,"Địa chỉ người khác").getAddressId();
            PaymentMethod method=new PaymentMethod(); method.setMethodCode("COD"); method.setName("COD"); em.persist(method);
            Cart cart=new Cart(); cart.setUser(buyer); em.persist(cart); cartId=cart.getCartId();
            for(int i=0;i<2;i++) {
                SellerApplication app=new SellerApplication(); app.setUser(buyer); app.setBusinessName("Shop "+i);
                app.setStatus(SellerApplicationStatus.APPROVED); em.persist(app);
                Shop shop=new Shop(); shop.setShopName("Shop "+i); shop.setApplicationId(app.getApplicationId()); em.persist(shop);
                Category category=new Category(); category.setName("Trái cây "+i); category.setSlug("fruit-"+i); em.persist(category);
                Product product=new Product(); product.setShop(shop); product.setName(i==0 ? "Bưởi gốc" : "Xoài");
                product.setSlug("fruit-"+i); product.setApprovalStatus("APPROVED"); product.getCategories().add(category); em.persist(product);
                ProductVariant variant=new ProductVariant(); variant.setProduct(product); variant.setSku("TEST-CHECKOUT-"+i);
                variant.setVariantName("Hộp 1kg"); variant.setPrice(new BigDecimal(i==0 ? "79000" : "65000")); em.persist(variant);
                InventoryBatch batch=new InventoryBatch(); batch.setVariant(variant); batch.setBatchCode("FRESH-"+i);
                batch.setQuantityOnHand(10); batch.setExpiryDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(10)); em.persist(batch);
                CartItem item=new CartItem(); item.setCart(cart); item.setVariant(variant); item.setQuantity(i==0 ? 2 : 1);
                item.setUnitPriceSnapshot(variant.getPrice()); em.persist(item); cart.getItems().add(item);
                if(i==0) { firstItemId=item.getCartItemId(); firstVariantId=variant.getVariantId(); firstBatchId=batch.getBatchId(); firstShopId=shop.getShopId(); }
                else secondBatchId=batch.getBatchId();
            }
        });
    }
    private User user(String email) { User u=new User(); u.setEmail(email); u.setFullName("Người kiểm thử"); em.persist(u); return u; }
    private Address address(User user,String line) {
        Address a=new Address(); a.setUser(user); a.setRecipientName("Người nhận gốc"); a.setPhone("0900000000");
        a.setProvince("Hà Nội"); a.setDistrict("Cầu Giấy"); a.setWard("Dịch Vọng"); a.setAddressLine(line); a.setIsDefault(true); em.persist(a); return a;
    }
    private CheckoutDraft draft(Long userId) {
        var preview=checkout.preview(userId);
        return new CheckoutDraft(UUID.randomUUID().toString().replace("-",""),userId,preview.cart().getCartId(),preview.fingerprint(),preview.shippingFeePerShop(),
                preview.cart().getItems().stream().map(VietFreshHub.Cart.dto.CartItemDto::getCartItemId).toList());
    }
    private CheckoutDraft selectionDraft(Long userId,List<Long> ids) {
        var preview=checkout.preview(userId,ids);
        return new CheckoutDraft(UUID.randomUUID().toString().replace("-",""),userId,preview.cart().getCartId(),preview.fingerprint(),
                preview.shippingFeePerShop(),preview.cart().getItems().stream().map(VietFreshHub.Cart.dto.CartItemDto::getCartItemId).toList());
    }
    private CheckoutRequest request(CheckoutDraft draft,Long address) { var r=new CheckoutRequest(); r.setAddressId(address); r.setCheckoutToken(draft.token()); return r; }
    private long count(String table) { return jdbc.queryForObject("select count(*) from dbo."+table,Long.class); }
    private int reserved(Long batchId) { return jdbc.queryForObject("select reserved_quantity from dbo.inventory_batches where batch_id=?",Integer.class,batchId); }
    private void assertNoOrder() { assertEquals(0,count("orders")); assertEquals(0,count("payments")); assertEquals(0,count("inventory_transactions")); }

    @Test void createsMultiShopOrderSnapshotsAndPendingCod() {
        var draft=draft(buyerId); var result=checkout.confirm(buyerId,draft,request(draft,addressId));
        assertEquals(new BigDecimal("263000.00"),result.grandTotal()); assertEquals(1,count("orders")); assertEquals(2,count("shop_orders"));
        assertEquals(2,count("order_items")); assertEquals(2,count("order_status_history")); assertEquals(2,count("inventory_transactions"));
        assertEquals(2,reserved(firstBatchId)); assertEquals(1,reserved(secondBatchId));
        tx.executeWithoutResult(status -> {
            Order order=em.find(Order.class,result.orderId()); assertEquals(Order.Status.PENDING,order.getStatus());
            assertEquals(Order.PaymentState.UNPAID,order.getPaymentStatus());
            Payment payment=em.createQuery("select p from Payment p",Payment.class).getSingleResult();
            assertEquals(Payment.Status.PENDING,payment.getStatus()); assertNull(payment.getPaidAt());
            assertEquals("CHECKED_OUT",em.find(Cart.class,cartId).getStatus());
        });
        assertEquals(1,tracker.events.size()); assertEquals(2,tracker.events.getFirst().shopOrderIds().size());
        assertTrue(carts.getActiveCart(buyerId).getItems().isEmpty());
    }
    @Test void duplicateSubmitReturnsSameOrderWithoutReservingAgain() {
        var draft=draft(buyerId); var request=request(draft,addressId);
        var first=checkout.confirm(buyerId,draft,request); var second=checkout.confirm(buyerId,draft,request);
        assertEquals(first.orderId(),second.orderId()); assertEquals(1,count("orders")); assertEquals(2,reserved(firstBatchId)); assertEquals(1,tracker.events.size());
    }
    @Test void twoConcurrentIdenticalSubmitsAreIdempotent() throws Exception {
        var draft=draft(buyerId); var request=request(draft,addressId); var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<CheckoutResult> work=() -> { start.await(); return checkout.confirm(buyerId,draft,request); };
            var a=executor.submit(work); var b=executor.submit(work); start.countDown();
            assertEquals(a.get(15,TimeUnit.SECONDS).orderId(),b.get(15,TimeUnit.SECONDS).orderId());
            assertEquals(1,count("orders")); assertEquals(2,reserved(firstBatchId));
        } finally { executor.shutdownNow(); }
    }
    @Test void shortageAtSecondShopRollsBackFirstReservationAndAllOrderRows() {
        var draft=draft(buyerId);
        tx.executeWithoutResult(status -> em.find(InventoryBatch.class,secondBatchId).setQuantityOnHand(0));
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request(draft,addressId)));
        assertNoOrder(); assertEquals(0,reserved(firstBatchId)); assertEquals(0,count("shop_orders")); assertEquals(0,count("order_items"));
        assertEquals("ACTIVE",jdbc.queryForObject("select status from dbo.carts where cart_id=?",String.class,cartId)); assertTrue(tracker.events.isEmpty());
    }
    @Test void twoCustomersCannotOversellTheLastItem() throws Exception {
        tx.executeWithoutResult(status -> {
            Cart original=em.find(Cart.class,cartId);
            for(CartItem item:new ArrayList<>(original.getItems())) {
                if(item.getVariant().getVariantId().equals(firstVariantId)) item.setQuantity(1);
                else { original.getItems().remove(item); em.remove(item); }
            }
            em.find(InventoryBatch.class,firstBatchId).setQuantityOnHand(1);
            Cart otherCart=new Cart(); otherCart.setUser(em.getReference(User.class,otherBuyerId)); em.persist(otherCart);
            CartItem item=new CartItem(); item.setCart(otherCart); item.setVariant(em.getReference(ProductVariant.class,firstVariantId));
            item.setQuantity(1); item.setUnitPriceSnapshot(item.getVariant().getPrice()); em.persist(item); otherCart.getItems().add(item);
        });
        var first=draft(buyerId); var second=draft(otherBuyerId);
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(() -> { start.await(); try { checkout.confirm(buyerId,first,request(first,addressId)); return true; } catch(CheckoutException ex) { return false; } });
            var b=executor.submit(() -> { start.await(); try { checkout.confirm(otherBuyerId,second,request(second,otherAddressId)); return true; } catch(CheckoutException ex) { return false; } });
            start.countDown(); assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));
            assertEquals(1,count("orders")); assertEquals(1,reserved(firstBatchId)); assertEquals(1,count("payments"));
        } finally { executor.shutdownNow(); }
    }
    @Test void allocatesEarlierExpiryFirstAndRecordsEachOrderItemBatch() {
        Long[] earlyId={null};
        tx.executeWithoutResult(status -> {
            InventoryBatch early=new InventoryBatch(); early.setVariant(em.getReference(ProductVariant.class,firstVariantId));
            early.setBatchCode("EARLY"); early.setQuantityOnHand(1); early.setExpiryDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1));
            em.persist(early); earlyId[0]=early.getBatchId();
        });
        var draft=draft(buyerId); checkout.confirm(buyerId,draft,request(draft,addressId));
        assertEquals(1,reserved(earlyId[0])); assertEquals(1,reserved(firstBatchId)); assertEquals(3,count("inventory_transactions"));
        Long itemId=jdbc.queryForObject("select order_item_id from dbo.order_items where variant_id=?",Long.class,firstVariantId);
        assertEquals(2,jdbc.queryForObject("select sum(quantity) from dbo.inventory_transactions where reference_type='ORDER_ITEM' and reference_id=?",Integer.class,itemId));
    }
    @Test void changedPriceRequiresNewReview() {
        var draft=draft(buyerId); tx.executeWithoutResult(status -> em.find(ProductVariant.class,firstVariantId).setPrice(new BigDecimal("99000")));
        var error=assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request(draft,addressId)));
        assertTrue(error.getMessage().contains("Giá hoặc số lượng")); assertNoOrder();
    }
    @Test void changedQuantityRequiresNewReview() {
        var draft=draft(buyerId); carts.updateCartItemQuantity(buyerId,firstVariantId,3);
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request(draft,addressId))); assertNoOrder();
    }
    @Test void foreignAddressCannotBeUsed() {
        var draft=draft(buyerId); assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request(draft,otherAddressId))); assertNoOrder();
    }
    @Test void shopClosedAfterReviewCannotReceiveOrder() {
        var draft=draft(buyerId); tx.executeWithoutResult(status -> em.find(Shop.class,firstShopId).setAvailabilityStatus("CLOSED"));
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request(draft,addressId))); assertNoOrder();
    }
    @Test void batchExpiresAfterReviewCannotBeReserved() {
        var draft=draft(buyerId); tx.executeWithoutResult(status -> em.find(InventoryBatch.class,firstBatchId).setExpiryDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1)));
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request(draft,addressId))); assertNoOrder();
    }
    @Test void snapshotsSurviveLaterCatalogAndAddressChanges() {
        var draft=draft(buyerId); var result=checkout.confirm(buyerId,draft,request(draft,addressId));
        tx.executeWithoutResult(status -> { em.find(Address.class,addressId).setAddressLine("Đường mới");
            em.find(ProductVariant.class,firstVariantId).setPrice(new BigDecimal("100000")); em.find(ProductVariant.class,firstVariantId).getProduct().setName("Tên mới"); });
        var details=orderQueries.getDetails(buyerId,result.orderId());
        assertTrue(details.addressText().contains("Đường gốc")); assertEquals("Bưởi gốc",details.shops().getFirst().items().getFirst().productName());
        assertEquals(new BigDecimal("79000.00"),details.shops().getFirst().items().getFirst().unitPrice());
        assertThrows(VietFreshHub.exception.ResourceNotFoundException.class,() -> orderQueries.getDetails(otherBuyerId,result.orderId()));
    }
    @Test void malformedOrForeignDraftCannotPlaceOrder() {
        var draft=draft(buyerId); var request=request(draft,addressId); request.setCheckoutToken("f".repeat(32));
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request));
        assertThrows(CheckoutException.class,() -> checkout.confirm(otherBuyerId,draft,request(draft,otherAddressId))); assertNoOrder();
    }
    @Test void unsupportedMethodAndChangedFeeRejected() {
        var draft=draft(buyerId); var request=request(draft,addressId); request.setPaymentMethodCode("VNPAY");
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,draft,request));
        var changedFee=new CheckoutDraft(draft.token(),draft.customerId(),draft.cartId(),draft.fingerprint(),new BigDecimal("30000"),draft.selectedItemIds());
        assertThrows(CheckoutException.class,() -> checkout.confirm(buyerId,changedFee,request(draft,addressId))); assertNoOrder();
    }
    @Test void nextAddCreatesFreshActiveCart() {
        var draft=draft(buyerId); checkout.confirm(buyerId,draft,request(draft,addressId)); carts.addToCart(buyerId,new AddToCartRequest(firstVariantId,1));
        assertEquals(2,count("carts")); assertEquals(1,carts.getActiveCart(buyerId).getItems().getFirst().getQuantity());
    }
    @Test void selectedShopOnlyIsChargedAndUnselectedCartLineRemains() {
        var draft=selectionDraft(buyerId,List.of(firstItemId));
        var result=checkout.confirm(buyerId,draft,request(draft,addressId));
        assertEquals(new BigDecimal("178000.00"),result.grandTotal()); assertEquals(1,count("shop_orders")); assertEquals(1,count("order_items"));
        assertEquals(2,reserved(firstBatchId)); assertEquals(0,reserved(secondBatchId));
        var remaining=carts.getActiveCart(buyerId); assertEquals(cartId,remaining.getCartId()); assertEquals(1,remaining.getItems().size());
        assertEquals("ACTIVE",remaining.getStatus()); assertNotEquals(firstVariantId,remaining.getItems().getFirst().getVariantId());
        assertEquals(result.orderId(),checkout.confirm(buyerId,draft,request(draft,addressId)).orderId());
        assertEquals(1,count("orders")); assertEquals(2,reserved(firstBatchId));
    }
    @Test void unavailableUnselectedProductDoesNotBlockCheckout() {
        tx.executeWithoutResult(status -> em.find(InventoryBatch.class,secondBatchId).getVariant().setStatus("INACTIVE"));
        var draft=selectionDraft(buyerId,List.of(firstItemId)); checkout.confirm(buyerId,draft,request(draft,addressId));
        assertEquals(1,count("orders")); assertEquals(0,reserved(secondBatchId)); assertEquals(1,carts.getActiveCart(buyerId).getItems().size());
    }
    @Test void changesToUnselectedProductDoNotChangeSelectedReview() {
        var draft=selectionDraft(buyerId,List.of(firstItemId));
        tx.executeWithoutResult(status -> em.find(InventoryBatch.class,secondBatchId).getVariant().setPrice(new BigDecimal("999000")));
        assertEquals(new BigDecimal("178000.00"),checkout.confirm(buyerId,draft,request(draft,addressId)).grandTotal());
    }
    @Test void emptyOrForeignCartSelectionRejected() {
        assertThrows(CheckoutException.class,() -> checkout.preview(buyerId,List.of()));
        assertThrows(CheckoutException.class,() -> checkout.preview(otherBuyerId,List.of(firstItemId)));
        assertThrows(CheckoutException.class,() -> checkout.preview(buyerId,List.of(firstItemId,999999L))); assertNoOrder();
    }
    @Test void addingFirstAddressUsesAuthenticatedOwnerAndPreservesDefault() {
        tx.executeWithoutResult(status -> em.remove(em.find(Address.class,otherAddressId)));
        var form=new CheckoutAddressRequest(); form.setCheckoutToken("a".repeat(32)); form.setRecipientName("Người nhận mới");
        form.setPhone("0901234567"); form.setProvince("Hà Nội"); form.setDistrict("Cầu Giấy"); form.setWard("Dịch Vọng"); form.setAddressLine("12 Đường mẫu");
        Long first=checkoutAddresses.create(otherBuyerId,form); Long second=checkoutAddresses.create(otherBuyerId,form);
        tx.executeWithoutResult(status -> {
            assertEquals(otherBuyerId,em.find(Address.class,first).getUser().getUserId()); assertTrue(em.find(Address.class,first).getIsDefault());
            assertFalse(em.find(Address.class,second).getIsDefault());
        });
        form.setRecipientName(" "); assertThrows(CheckoutException.class,() -> checkoutAddresses.create(otherBuyerId,form));
    }
    @AfterEach void cleanupIsolatedH2Database() {
        for(String table:List.of("delivery_status_history","deliveries","payments","order_status_history","order_addresses","order_items","shop_orders","orders",
                "inventory_transactions","cart_items","carts","inventory_batches","product_categories","product_variants","products",
                "categories","shops","seller_applications","addresses","users","payment_methods")) jdbc.update("delete from dbo."+table);
    }
    private CheckoutResult placeOrder() { var draft=draft(buyerId); return checkout.confirm(buyerId,draft,request(draft,addressId)); }
    @Test void cancellationReleasesOnlyThisOrderAndIsIdempotent() {
        var placed=placeOrder();
        tx.executeWithoutResult(status -> em.find(InventoryBatch.class,firstBatchId).setReservedQuantity(5)); // 3 units belong to other work.
        customerOrders.cancel(buyerId,placed.orderId(),"Đổi kế hoạch"); customerOrders.cancel(buyerId,placed.orderId(),"Gửi lại");
        assertEquals(3,reserved(firstBatchId)); assertEquals(0,reserved(secondBatchId));
        assertEquals(2,jdbc.queryForObject("select count(*) from dbo.inventory_transactions where transaction_type='RELEASE'",Integer.class));
        assertEquals("CANCELLED",jdbc.queryForObject("select status from dbo.orders where order_id=?",String.class,placed.orderId()));
        assertEquals("CANCELLED",jdbc.queryForObject("select status from dbo.payments where order_id=?",String.class,placed.orderId()));
        assertEquals(1,tracker.cancellations.size());
    }
    @Test void anyPreparingShopBlocksWholeCancellationWithoutChangingStock() {
        var placed=placeOrder();
        tx.executeWithoutResult(status -> em.createQuery("select s from ShopOrder s where s.order.orderId=:id",ShopOrder.class)
                .setParameter("id",placed.orderId()).getResultList().getFirst().setStatus(ShopOrder.Status.PREPARING));
        assertFalse(customerOrders.actions(buyerId,placed.orderId()).cancellable());
        assertThrows(VietFreshHub.Order.exception.CustomerOrderException.class,() -> customerOrders.cancel(buyerId,placed.orderId(),""));
        assertEquals(2,reserved(firstBatchId)); assertEquals(0,jdbc.queryForObject("select count(*) from dbo.inventory_transactions where transaction_type='RELEASE'",Integer.class));
    }
    @Test void corruptReservationFailsAtomicallyInsteadOfFreeingAnotherOrderStock() {
        var placed=placeOrder();
        tx.executeWithoutResult(status -> em.find(InventoryBatch.class,secondBatchId).setReservedQuantity(0));
        assertThrows(VietFreshHub.Order.exception.CustomerOrderException.class,() -> customerOrders.cancel(buyerId,placed.orderId(),""));
        assertEquals(2,reserved(firstBatchId)); assertEquals("PENDING",jdbc.queryForObject("select status from dbo.orders where order_id=?",String.class,placed.orderId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from dbo.inventory_transactions where transaction_type='RELEASE'",Integer.class));
    }
    @Test void concurrentCancelDoesNotReleaseTwice() throws Exception {
        var placed=placeOrder(); var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try { Callable<Void> work=() -> { start.await(); customerOrders.cancel(buyerId,placed.orderId(),""); return null; };
            var a=executor.submit(work); var b=executor.submit(work); start.countDown(); a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
            assertEquals(0,reserved(firstBatchId)); assertEquals(2,jdbc.queryForObject("select count(*) from dbo.inventory_transactions where transaction_type='RELEASE'",Integer.class));
        } finally { executor.shutdownNow(); }
    }
    @Test void reorderUsesCurrentPricesSkipsUnavailableAndCreatesNoNewOrder() {
        var placed=placeOrder(); customerOrders.cancel(buyerId,placed.orderId(),"");
        tx.executeWithoutResult(status -> { em.find(ProductVariant.class,firstVariantId).setPrice(new BigDecimal("99000"));
            em.find(InventoryBatch.class,secondBatchId).getVariant().setStatus("INACTIVE"); });
        var result=customerOrders.reorder(buyerId,placed.orderId());
        assertEquals(1,result.addedLines()); assertEquals(1,result.skipped().size()); assertEquals(1,count("orders"));
        var cart=carts.getActiveCart(buyerId); assertEquals(2,cart.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("99000.00"),cart.getItems().getFirst().getUnitPriceSnapshot()); assertEquals(0,reserved(firstBatchId));
    }
    @Test void reorderDoesNotOverfillExistingCartAndDoesNotCreateEmptyCart() {
        var placed=placeOrder();
        tx.executeWithoutResult(status -> { em.find(InventoryBatch.class,firstBatchId).setQuantityOnHand(2); em.find(InventoryBatch.class,secondBatchId).setQuantityOnHand(1); });
        var result=customerOrders.reorder(buyerId,placed.orderId()); assertEquals(0,result.addedLines()); assertEquals(2,result.skipped().size()); assertEquals(1,count("carts"));
    }
    @Test void historyPaginatesFiltersAndNeverListsOtherCustomerOrders() {
        Long placedId=null;
        for(int i=0;i<12;i++) { if(i>0) carts.addToCart(buyerId,new AddToCartRequest(firstVariantId,1));
            tx.executeWithoutResult(status -> em.find(InventoryBatch.class,firstBatchId).setQuantityOnHand(100));
            var placed=placeOrder(); placedId=placed.orderId(); }
        var filter=new VietFreshHub.Order.dto.OrderFilterRequest();
        assertEquals(12,customerOrders.history(buyerId,filter).getTotalElements()); assertEquals(10,customerOrders.history(buyerId,filter).getContent().size());
        filter.setPage(2); assertEquals(2,customerOrders.history(buyerId,filter).getContent().size()); assertEquals(0,customerOrders.history(otherBuyerId,filter).getTotalElements());
        filter.setPage(1); filter.setStatus("CANCELLED"); assertEquals(0,customerOrders.history(buyerId,filter).getTotalElements());
        customerOrders.cancel(buyerId,placedId,""); assertEquals(1,customerOrders.history(buyerId,filter).getTotalElements());
    }
    @Test void trackingReadsActualDeliveryHistoryAndFailureReason() {
        var placed=placeOrder(); Long shopId=jdbc.queryForObject("select min(shop_order_id) from dbo.shop_orders where order_id=?",Long.class,placed.orderId());
        tx.executeWithoutResult(status -> {
            var delivery=new VietFreshHub.Order.entity.OrderDeliveryView(); delivery.setShopOrder(em.getReference(ShopOrder.class,shopId));
            delivery.setStatus("FAILED"); delivery.setFailureReason("Không liên hệ được người nhận");
            var now=LocalDateTime.now(ZoneOffset.UTC).withNano(0); delivery.setCreatedAt(now); delivery.setUpdatedAt(now); em.persist(delivery);
            var entry=new VietFreshHub.Order.entity.OrderDeliveryHistoryView(); entry.setDelivery(delivery); entry.setStatus("FAILED");
            entry.setNote("Giao thất bại"); entry.setCreatedAt(now.plusMinutes(1)); em.persist(entry);
        });
        var tracking=customerOrders.tracking(buyerId,placed.orderId()); assertEquals(2,tracking.shops().size());
        var first=tracking.shops().getFirst(); assertEquals("Giao thất bại",first.deliveryStatus()); assertTrue(first.failureReason().contains("Không liên hệ"));
        assertTrue(first.events().stream().anyMatch(event -> event.kind().equals("DELIVERY")));
    }
    @Test void foreignOrderMutationsAndTrackingAreDenied() {
        var placed=placeOrder(); assertThrows(VietFreshHub.exception.ResourceNotFoundException.class,() -> customerOrders.cancel(otherBuyerId,placed.orderId(),""));
        assertThrows(VietFreshHub.exception.ResourceNotFoundException.class,() -> customerOrders.reorder(otherBuyerId,placed.orderId()));
        assertThrows(VietFreshHub.exception.ResourceNotFoundException.class,() -> customerOrders.tracking(otherBuyerId,placed.orderId())); assertEquals(2,reserved(firstBatchId));
    }
    @Test void paidOrderIsNotFalselyRefundedByCancellation() {
        var placed=placeOrder(); tx.executeWithoutResult(status -> em.find(Order.class,placed.orderId()).setPaymentStatus(Order.PaymentState.PAID));
        assertThrows(VietFreshHub.Order.exception.CustomerOrderException.class,() -> customerOrders.cancel(buyerId,placed.orderId(),"")); assertEquals(2,reserved(firstBatchId));
    }
}
