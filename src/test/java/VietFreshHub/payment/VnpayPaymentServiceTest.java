package VietFreshHub.payment;

import VietFreshHub.auth.entity.User;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.payment.config.VnpayProperties;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.*;
import VietFreshHub.payment.service.VnpayGateway;
import VietFreshHub.payment.service.impl.VnpayPaymentServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VnpayPaymentServiceTest {
    final PaymentRepository payments=mock(PaymentRepository.class);
    final PaymentTransactionRepository audit=mock(PaymentTransactionRepository.class);
    final OrderRepository orders=mock(OrderRepository.class);
    final VnpayGateway gateway=mock(VnpayGateway.class);
    final EntityManager em=mock(EntityManager.class);
    final VnpayPaymentServiceImpl service=new VnpayPaymentServiceImpl(
            new VnpayProperties(true,"DEMOV210","fake-secret","https://sandbox.vnpayment.vn/paymentv2/vpcpay.html","http://localhost:8080/payments/vnpay/return"),
            gateway,payments,mock(PaymentMethodRepository.class),audit,orders,mock(ShopOrderRepository.class),
            mock(OrderInventoryReleaseService.class),em,mock(ApplicationEventPublisher.class),15);
    final Order order=new Order(); final Payment payment=new Payment(); final PaymentTransaction init=new PaymentTransaction();

    @BeforeEach void fixture() {
        User buyer=new User(); buyer.setUserId(7L);
        order.setOrderId(1L); order.setCustomer(buyer); order.setOrderCode("VF-test"); order.setGrandTotal(new BigDecimal("10000.00"));
        PaymentMethod method=new PaymentMethod(); method.setMethodCode("VNPAY");
        payment.setPaymentId(10L); payment.setOrder(order); payment.setProvider("VNPAY"); payment.setPaymentMethod(method);
        payment.setAmount(order.getGrandTotal()); payment.setTransactionReference("TEST123"); payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        init.setProviderPayload(Instant.now().plusSeconds(900).toString());
        when(gateway.hasValidSignature(anyMap())).thenReturn(true);
        when(payments.findByProviderAndTransactionReference("VNPAY","TEST123")).thenReturn(Optional.of(payment));
        when(payments.findByOrderOrderIdOrderByPaymentIdAsc(1L)).thenReturn(List.of(payment));
        when(audit.findFirstByPaymentPaymentIdAndEventTypeOrderByTransactionIdAsc(10L,"VNPAY_INIT")).thenReturn(Optional.of(init));
        when(orders.findByOrderIdAndCustomerUserId(1L,7L)).thenReturn(Optional.of(order));
    }
    Map<String,List<String>> callback(String response,String status) {
        return new HashMap<>(Map.of("vnp_TmnCode",List.of("DEMOV210"),"vnp_TxnRef",List.of("TEST123"),
                "vnp_Amount",List.of("1000000"),"vnp_ResponseCode",List.of(response),
                "vnp_TransactionStatus",List.of(status),"vnp_TransactionNo",List.of("123456")));
    }
    String accept(Map<String,List<String>> data) { return service.receiveIpn(data).get("RspCode"); }

    @Test void successfulIpnUpdatesBothPaymentAndOrderAndAppendsReceipt() {
        assertEquals("00",accept(callback("00","00")));
        assertEquals(Payment.Status.PAID,payment.getStatus()); assertEquals("PAID",order.getPaymentStatus());
        assertNotNull(payment.getPaidAt());
        verify(audit).save(argThat(entry -> "VNPAY_RECEIPT".equals(entry.getEventType()) && "PAID".equals(entry.getStatus())));
    }
    @Test void invalidSignatureCannotReachDatabase() {
        when(gateway.hasValidSignature(anyMap())).thenReturn(false);
        assertEquals("97",accept(callback("00","00"))); verifyNoInteractions(payments,audit);
    }
    @Test void wrongMerchantCannotReachDatabase() {
        var data=callback("00","00"); data.put("vnp_TmnCode",List.of("OTHER123"));
        assertEquals("97",accept(data)); verifyNoInteractions(payments,audit);
    }
    @Test void unknownReferenceCannotChangeState() {
        when(payments.findByProviderAndTransactionReference("VNPAY","TEST123")).thenReturn(Optional.empty());
        assertEquals("01",accept(callback("00","00"))); verifyNoInteractions(audit);
    }
    @Test void mismatchingAmountCannotBePaid() {
        var data=callback("00","00"); data.put("vnp_Amount",List.of("1"));
        assertEquals("04",accept(data)); assertEquals("UNPAID",order.getPaymentStatus()); verifyNoInteractions(audit);
    }
    @Test void duplicateReceiptIsIdempotent() {
        when(audit.findFirstByPaymentPaymentIdAndEventTypeOrderByTransactionIdAsc(10L,"VNPAY_RECEIPT"))
                .thenReturn(Optional.of(new PaymentTransaction()));
        assertEquals("02",accept(callback("00","00"))); verify(audit,never()).save(any());
    }
    @Test void successRequiresBothProviderStatuses() {
        assertEquals("00",accept(callback("00","02")));
        assertEquals(Payment.Status.FAILED,payment.getStatus()); assertEquals("UNPAID",order.getPaymentStatus());
    }
    @Test void failedIpnDoesNotOverwritePaidState() {
        payment.setStatus(Payment.Status.PAID); order.setPaymentStatus("PAID");
        assertEquals("02",accept(callback("24","02"))); assertEquals("PAID",order.getPaymentStatus());
    }
    @Test void lateSuccessIsAuditedForReviewWithoutResurrectingCancelledOrder() {
        order.setStatus(Order.Status.CANCELLED); payment.setStatus(Payment.Status.CANCELLED);
        assertEquals("00",accept(callback("00","00")));
        assertEquals(Order.Status.CANCELLED,order.getStatus()); assertEquals("UNPAID",order.getPaymentStatus());
        assertEquals(Payment.Status.PROCESSING,payment.getStatus());
        verify(audit).save(argThat(entry -> "REVIEW".equals(entry.getStatus())));
    }
    @Test void expiredSuccessRequiresReviewEvenBeforeExpiryJobRuns() {
        init.setProviderPayload(Instant.now().minusSeconds(1).toString());
        assertEquals("00",accept(callback("00","00"))); assertEquals("UNPAID",order.getPaymentStatus());
        assertEquals(Payment.Status.PROCESSING,payment.getStatus());
    }
    @Test void reusedProviderReceiptCannotPayAnotherOrder() {
        when(audit.existsByEventTypeAndProviderTransactionId("VNPAY_RECEIPT","123456")).thenReturn(true);
        assertEquals("99",accept(callback("00","00"))); assertEquals("UNPAID",order.getPaymentStatus());
    }
    @Test void malformedResultCannotUpdatePayment() {
        var data=callback("00","00"); data.remove("vnp_TransactionNo");
        assertEquals("99",accept(data)); assertEquals(Payment.Status.PENDING,payment.getStatus());
    }
    @Test void returnUrlIsReadOnly() {
        assertTrue(service.validReturn(callback("00","00"))); verifyNoInteractions(payments,audit,em,orders);
    }
    @Test void foreignCustomerCannotReadOrStartPayment() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> service.page(8L,1L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> service.start(8L,1L,"127.0.0.1"));
        verifyNoInteractions(payments,audit);
    }
}
