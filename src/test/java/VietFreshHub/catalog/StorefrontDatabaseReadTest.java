package VietFreshHub.catalog;

import VietFreshHub.common.PageSizes;
import VietFreshHub.delivery.scheduler.UnassignedDeliveryRetryScheduler;
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
    @MockitoBean UnassignedDeliveryRetryScheduler deliveryScheduler;
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

    @AfterAll
    static void keepPreviewAvailable() throws InterruptedException {
        if (Boolean.getBoolean("storefront.preview")) new java.util.concurrent.CountDownLatch(1)
                .await(Integer.getInteger("storefront.preview.seconds",55),java.util.concurrent.TimeUnit.SECONDS);
    }
}
