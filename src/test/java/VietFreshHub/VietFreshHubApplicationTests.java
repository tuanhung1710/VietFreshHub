package VietFreshHub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-context;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=none", "inventory.alerts.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
class VietFreshHubApplicationTests {
    @MockitoBean VietFreshHub.payment.vnpay.VnpayQueryClient vnpayQueryClient;
    @MockitoBean VietFreshHub.payment.vnpay.VnpayPaymentTimeoutScheduler paymentScheduler;
    @MockitoBean VietFreshHub.vietmap.service.VietMapService vietMap;

	@Test
	void contextLoads() {
	}

}
