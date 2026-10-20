package VietFreshHub.payment.service;

import VietFreshHub.payment.dto.VnpayPaymentRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface VnpayGateway {
    URI createPaymentUrl(VnpayPaymentRequest request);

    /** Verifies integrity only; does not prove an order is paid or update the database. */
    boolean hasValidSignature(Map<String, List<String>> parameters);
}
