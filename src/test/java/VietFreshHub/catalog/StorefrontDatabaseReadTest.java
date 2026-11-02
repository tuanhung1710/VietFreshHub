package VietFreshHub.catalog;

import VietFreshHub.common.PageSizes;
import VietFreshHub.payment.vnpay.VnpayPaymentTimeoutScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit opt-in: public GETs against the configured SQL Server, with all mutation schedulers disabled. */
@EnabledIfSystemProperty(named="storefront.sqlserver",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={
        "server.port=8808", "spring.jpa.properties.hibernate.hbm2ddl.auto=none", "inventory.alerts.enabled=false"})
@AutoConfigureMockMvc
class StorefrontDatabaseReadTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired VietFreshHub.catalog.service.CatalogService catalog;

    @MockitoBean VnpayPaymentTimeoutScheduler paymentScheduler;
    @MockitoBean VietFreshHub.vietmap.service.VietMapService vietMap;
    @MockitoBean VietFreshHub.payment.vnpay.VnpayQueryClient vnpay;

    @Test
    void publicTemplateAndQueriesWorkWithTheExistingDatabase() throws Exception {
        mvc.perform(get("/home")).andExpect(status().isOk()).andExpect(content().string(containsString("homePagination")));
        mvc.perform(get("/storefront/api/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSizes",is(PageSizes.OPTIONS)))
                .andExpect(jsonPath("$.shops",hasSize(greaterThan(0))));
        mvc.perform(get("/storefront/api/home").param("pageSize","5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.products.items",hasSize(lessThanOrEqualTo(5))))
                .andExpect(jsonPath("$.products.total",greaterThan(0)))
                .andExpect(jsonPath("$.topProducts",hasSize(lessThanOrEqualTo(5))));
        mvc.perform(get("/storefront/api/products").param("places","TP Hồ Chí Minh").param("minRating","4").param("pageSize","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].rating",everyItem(greaterThanOrEqualTo(4.0))));
    }

    @Test
    void shopPagesDisplayTheStoredFormattedAddress() throws Exception {
        var locations = jdbc.queryForList("""
                SELECT s.shop_id,l.formatted_address,l.address_line,l.ward,l.district,l.province
                FROM dbo.shops s LEFT JOIN dbo.shop_locations l ON l.shop_id=s.shop_id
                WHERE s.status='ACTIVE' AND s.availability_status='OPEN'
                """);
        org.junit.jupiter.api.Assertions.assertFalse(locations.isEmpty());
        for (var location : locations) {
            String address = VietFreshHub.shop.util.ShopAddressFormatter.format((String) location.get("formatted_address"),
                    (String) location.get("address_line"),(String) location.get("ward"),
                    (String) location.get("district"),(String) location.get("province"));
            String expected = address == null || address.isBlank() ? "Chưa cập nhật địa chỉ" : address;
            mvc.perform(get("/shops/" + location.get("shop_id"))).andExpect(status().isOk())
                    .andExpect(view().name("shop/detail"))
                    .andExpect(model().attribute("formattedAddress", expected));
        }
    }

    @Test
    void productDetailRendersRealVariantsImagesAndApprovedReviews() throws Exception {
        var product = catalog.listProducts(new VietFreshHub.catalog.dto.CatalogBrowseFilter()).getContent().getFirst();
        mvc.perform(get("/product-detail/" + product.getSelectedVariant().getVariantId()))
                .andExpect(status().isOk()).andExpect(view().name("customer/product-detail"))
                .andExpect(content().string(containsString("/css/product-detail.css")))
                .andExpect(content().string(containsString("name=\"variantId\"")))
                .andExpect(content().string(containsString("name=\"quantity\"")));
    }

    @AfterAll
    static void keepPreviewAvailable() throws InterruptedException {
        if (!Boolean.getBoolean("storefront.preview")) return;
        String stopFile = System.getProperty("storefront.preview.stop-file");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(
                Integer.getInteger("storefront.preview.seconds",55));
        while (System.nanoTime() < deadline) {
            if (stopFile != null && java.nio.file.Files.exists(java.nio.file.Path.of(stopFile))) break;
            Thread.sleep(250);
        }
    }
}
