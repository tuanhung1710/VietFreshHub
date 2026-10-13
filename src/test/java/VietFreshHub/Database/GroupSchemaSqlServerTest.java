package VietFreshHub.Database;

import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Checkout.dto.*;
import VietFreshHub.Checkout.service.CheckoutService;
import VietFreshHub.Checkout.repository.CheckoutAddressRepository;
import VietFreshHub.Order.dto.OrderFilterRequest;
import VietFreshHub.Order.service.CustomerOrderService;
import VietFreshHub.Order.service.CustomerOrderQueryService;
import VietFreshHub.Product.service.CatalogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit opt-in: real group SQL Server, all changes roll back after each test. */
@EnabledIfEnvironmentVariable(named="VF_SCHEMA_SQLSERVER_TESTS", matches="true")
@SpringBootTest(properties={"app.seed.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=validate", "spring.jpa.properties.hibernate.show_sql=false"})
@Transactional
@AutoConfigureMockMvc
class GroupSchemaSqlServerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired CatalogService catalog;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired CheckoutAddressRepository addresses;
    @Autowired CustomerOrderService orders;
    @Autowired CustomerOrderQueryService details;

    @BeforeEach void requireGroupDatabase() {
        assertEquals("VietFresh_Hub_Final",jdbc.queryForObject("select DB_NAME()",String.class));
        assertEquals(60,jdbc.queryForObject("select count(*) from sys.tables where is_ms_shipped=0",Integer.class));
    }

    @Test void seededRolesCatalogAndAllOrdersAreReadable() {
        for(String email:List.of("admin", "seller", "customer", "customer2", "delivery", "delivery2"))
            assertNotNull(auth.login(email+"@demo.vietfresh.invalid","Demo@12345"));
        long expected=jdbc.queryForObject("select count(distinct product_id) from dbo.v_public_shop_variants",Long.class);
        var page=catalog.listProducts(1,""); assertEquals(expected,page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(p -> p.getImageUrl().startsWith("https://")));
        for(var row:jdbc.queryForList("select order_id,customer_id from dbo.orders")) {
            Long customer=((Number)row.get("customer_id")).longValue(), order=((Number)row.get("order_id")).longValue();
            assertFalse(details.getDetails(customer,order).addressText().contains("null"));
            assertNotNull(orders.tracking(customer,order));
            assertNotNull(orders.history(customer,new OrderFilterRequest()));
        }
    }

    @Test void selectedCheckoutAllocationsCancellationAndAddressSnapshotRollBack() {
        Long buyer=users.findByEmailIgnoreCase("customer@demo.vietfresh.invalid").orElseThrow().getUserId();
        var cart=carts.getActiveCart(buyer);
        var chosen=cart.getItems().stream().filter(i -> i.isPurchasable()).findFirst().orElseThrow();
        var product=em.find(VietFreshHub.Product.entity.ProductVariant.class,chosen.getVariantId()).getProduct();
        product.setRequiresPreparationCheck(true); product.setPreparationNote("Chuẩn bị theo yêu cầu kiểm thử."); em.flush();
        var preview=checkout.preview(buyer,List.of(chosen.getCartItemId()));
        var address=addresses.findByUserUserIdOrderByIsDefaultDescAddressIdAsc(buyer).getFirst();
        assertNull(address.getDistrict());
        var draft=new CheckoutDraft(UUID.randomUUID().toString().replace("-",""),buyer,cart.getCartId(),preview.fingerprint(),
                preview.shippingFeePerShop(),List.of(chosen.getCartItemId()));
        var form=new CheckoutRequest(); form.setCheckoutToken(draft.token()); form.setAddressId(address.getAddressId());
        var result=checkout.confirm(buyer,draft,form);
        assertEquals(result.orderId(),checkout.confirm(buyer,draft,form).orderId());
        assertEquals(address.getFormattedAddress(),jdbc.queryForObject("select formatted_address from dbo.order_addresses where order_id=?",String.class,result.orderId()));
        assertEquals(chosen.getQuantity(),jdbc.queryForObject("select sum(a.quantity) from dbo.stock_allocations a join dbo.order_items i on i.order_item_id=a.order_item_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and a.status='RESERVED'",Integer.class,result.orderId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from dbo.order_items i join dbo.product_variants v on v.variant_id=i.variant_id join dbo.products p on p.product_id=v.product_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and i.requires_preparation_check_snapshot<>p.requires_preparation_check",Integer.class,result.orderId()));
        assertEquals("Chuẩn bị theo yêu cầu kiểm thử.",jdbc.queryForObject("select i.preparation_note_snapshot from dbo.order_items i join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=?",String.class,result.orderId()));
        orders.cancel(buyer,result.orderId(),"Kiểm thử đồng bộ schema, rollback."); em.flush();
        assertEquals(0,jdbc.queryForObject("select count(*) from dbo.stock_allocations a join dbo.order_items i on i.order_item_id=a.order_item_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and a.status<>'RELEASED'",Integer.class,result.orderId()));
        assertEquals(-chosen.getQuantity(),jdbc.queryForObject("select sum(t.quantity) from dbo.inventory_transactions t join dbo.order_items i on i.order_item_id=t.reference_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and t.reference_type='ORDER_ITEM' and t.transaction_type='RELEASE'",Integer.class,result.orderId()));
    }

    @Test void mvcTemplatesRenderWithGroupSeed() throws Exception {
        String buyer="customer@demo.vietfresh.invalid";
        for(String path:List.of("/home","/cart","/customer/checkout","/customer/orders"))
            mvc.perform(get(path).with(user(buyer).roles("CUSTOMER"))).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        Long order=jdbc.queryForObject("select min(o.order_id) from dbo.orders o join dbo.users u on u.user_id=o.customer_id where u.email=?",Long.class,buyer);
        for(String suffix:List.of("","/tracking"))
            mvc.perform(get("/customer/orders/"+order+suffix).with(user(buyer).roles("CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(get("/admin/seller-applications").with(user("admin@demo.vietfresh.invalid").roles("ADMIN"))).andExpect(status().isOk());
        Long application=jdbc.queryForObject("select min(application_id) from dbo.seller_applications",Long.class);
        mvc.perform(get("/admin/seller-applications/"+application).with(user("admin@demo.vietfresh.invalid").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.invalid/seed/giay-phep-kinh-doanh.pdf")));
    }

    @Test void groupSeedReservationCanBeCancelledUsingItsAllocations() {
        boolean exercised=false;
        for(var row:jdbc.queryForList("select order_id,customer_id from dbo.orders where status in ('PENDING','CONFIRMED')")) {
            Long buyer=((Number)row.get("customer_id")).longValue(), order=((Number)row.get("order_id")).longValue();
            if(!orders.actions(buyer,order).cancellable()) continue;
            orders.cancel(buyer,order,"Kiểm thử đơn seed, rollback."); em.flush();
            assertEquals("CANCELLED",jdbc.queryForObject("select status from dbo.orders where order_id=?",String.class,order));
            assertEquals(0,jdbc.queryForObject("select count(*) from dbo.stock_allocations a join dbo.order_items i on i.order_item_id=a.order_item_id join dbo.shop_orders s on s.shop_order_id=i.shop_order_id where s.order_id=? and a.status='RESERVED'",Integer.class,order));
            exercised=true; break;
        }
        assertTrue(exercised,"Group seed must have an eligible COD order for this compatibility test.");
    }
}
