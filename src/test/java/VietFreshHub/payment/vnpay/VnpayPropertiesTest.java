package VietFreshHub.payment.vnpay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class VnpayPropertiesTest {
    @Test
    void missingHashSecretDisablesVnpay() {
        VnpayProperties properties = new VnpayProperties();

        assertNull(properties.getHashSecret());
        assertFalse(properties.isReady());
        assertFalse(properties.isQueryReady());
    }
}
