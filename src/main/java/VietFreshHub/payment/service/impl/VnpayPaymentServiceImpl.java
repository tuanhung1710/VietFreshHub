package VietFreshHub.payment.service.impl;

import VietFreshHub.exception.CheckoutException;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.order.service.OrderCancelledEvent;
import VietFreshHub.order.service.OrderInventoryReleaseService;
import VietFreshHub.payment.config.VnpayProperties;
import VietFreshHub.payment.dto.VnpayPaymentPage;
import VietFreshHub.payment.dto.VnpayPaymentRequest;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.entity.PaymentTransaction;
import VietFreshHub.payment.repository.*;
import VietFreshHub.payment.service.VnpayGateway;
import VietFreshHub.payment.service.VnpayPaymentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.*;
import java.util.*;

@Service
public class VnpayPaymentServiceImpl implements VnpayPaymentService {
    private final VnpayProperties settings;
    private final VnpayGateway gateway;
    private final PaymentRepository payments;
    private final PaymentMethodRepository methods;
    private final PaymentTransactionRepository audit;
    private final OrderRepository orders;
    private final ShopOrderRepository shops;
    private final OrderInventoryReleaseService inventory;
    private final EntityManager em;
    private final ApplicationEventPublisher events;
    private final int timeoutMinutes;

    public VnpayPaymentServiceImpl(VnpayProperties settings, VnpayGateway gateway, PaymentRepository payments,
            PaymentMethodRepository methods, PaymentTransactionRepository audit, OrderRepository orders,
            ShopOrderRepository shops, OrderInventoryReleaseService inventory, EntityManager em,
            ApplicationEventPublisher events,
            @Value("${app.payment.vnpay.timeout-minutes:15}") int timeoutMinutes) {
        this.settings=settings; this.gateway=gateway; this.payments=payments; this.methods=methods;
        this.audit=audit; this.orders=orders; this.shops=shops; this.inventory=inventory;
        this.em=em; this.events=events; this.timeoutMinutes=timeoutMinutes;
    }

    @Override @Transactional(readOnly=true)
    public boolean available() {
        if (!settings.enabled || settings.tmnCode == null || !settings.tmnCode.matches("[A-Za-z0-9]{8}")
                || settings.hashSecret() == null || settings.hashSecret().isBlank()
                || timeoutMinutes<1 || timeoutMinutes>60) return false;
        if (methods.findByMethodCodeAndStatus("VNPAY","ACTIVE").isEmpty()) return false;
        if(!"https://sandbox.vnpayment.vn/paymentv2/vpcpay.html".equals(settings.paymentUrl)) return false;
        try { gateway.hasValidSignature(Map.of()); }
        catch(IllegalArgumentException | IllegalStateException ex) { return false; }
        // Fail closed if the integration script has not installed the fulfillment guard.
        Number gate=(Number)em.createNativeQuery("select count(*) from sys.triggers where object_id=object_id('dbo.TR_TV3_VnpayPaymentGate') and is_disabled=0", Integer.class)
                .getSingleResult();
        if(gate.intValue()!=1) return false;
        Number indexes=(Number)em.createNativeQuery("select count(*) from sys.indexes where is_unique=1 and is_disabled=0 and ((object_id=object_id('dbo.payments') and name='UX_TV3_VnpayReference') or (object_id=object_id('dbo.payment_transactions') and name='UX_TV3_VnpayReceipt'))", Integer.class)
                .getSingleResult();
        return indexes.intValue()==2;
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void prepare(Payment payment) {
        if (!available()) throw new CheckoutException("Thanh toán VNPAY hiện chưa khả dụng.");
        Instant created=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        payment.setProvider("VNPAY");
        payment.setTransactionReference(UUID.randomUUID().toString().replace("-",""));
        payment.setCreatedAt(LocalDateTime.ofInstant(created,ZoneOffset.UTC));
        gateway.createPaymentUrl(new VnpayPaymentRequest(payment.getTransactionReference(),payment.getAmount(),
                "127.0.0.1",created,created.plusSeconds(timeoutMinutes*60L)));
        // INIT stores the immutable expiration snapshot, not a secret or redirect URL.
        record(payment,"VNPAY_INIT",null,"PENDING",created.plusSeconds(timeoutMinutes*60L).toString());
    }

    @Override @Transactional(readOnly=true)
    public boolean isOnlineOrder(Long customerId, Long orderId) {
        owned(customerId,orderId);
        return payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId).stream().anyMatch(this::online);
    }

    @Override @Transactional(readOnly=true)
    public VnpayPaymentPage page(Long customerId,Long orderId) {
        Order order=owned(customerId,orderId);
        Payment payment=paymentFor(orderId);
        Instant expires=expiration(payment);
        String status; String message;
        if(needsReview(orderId)) {
            status="REVIEW"; message="Giao dịch cần đối soát. Không thanh toán thêm; vui lòng liên hệ hỗ trợ.";
        } else if (payment.getStatus()==Payment.Status.PAID && "PAID".equals(order.getPaymentStatus())) {
            status="PAID"; message="Thanh toán thành công. Đơn hàng đã được ghi nhận thanh toán.";
        } else if(order.getStatus()==Order.Status.CANCELLED) {
            status="CANCELLED"; message="Đơn hàng đã hủy. Không tiếp tục thanh toán cho đơn này.";
        } else if(!Instant.now().isBefore(expires)) {
            status="EXPIRED"; message="Đã hết thời hạn thanh toán. Đơn sẽ được hủy và hoàn lại lượng hàng đang giữ.";
        } else if(payment.getStatus()==Payment.Status.FAILED) {
            status="FAILED"; message="Thanh toán không thành công. Bạn có thể thử lại trong thời hạn của đơn.";
        } else {
            status="PENDING"; message="Đang chờ thanh toán hoặc chờ VNPAY xác nhận. Bạn có thể tải lại để kiểm tra.";
        }
        boolean canPay=Set.of("PENDING","FAILED").contains(status) && available();
        return new VnpayPaymentPage(orderId,order.getOrderCode(),payment.getAmount(),status,message,
                LocalDateTime.ofInstant(expires,ZoneId.of("Asia/Ho_Chi_Minh")),canPay);
    }

    @Override @Transactional
    public URI start(Long customerId,Long orderId,String clientIp) {
        Order order=owned(customerId,orderId); lock(order);
        Payment payment=paymentFor(orderId); em.refresh(payment,LockModeType.PESSIMISTIC_WRITE);
        Instant expires=expiration(payment);
        if(!available() || order.getStatus()!=Order.Status.PENDING || !"UNPAID".equals(order.getPaymentStatus())
                || needsReview(orderId)
                || !Set.of(Payment.Status.PENDING,Payment.Status.FAILED).contains(payment.getStatus()) || !Instant.now().isBefore(expires)) {
            throw new CheckoutException("Không thể mở thanh toán. Vui lòng kiểm tra lại trạng thái đơn.");
        }
        if(payment.getStatus()==Payment.Status.FAILED) {
            Payment retry=new Payment(); retry.setOrder(order); retry.setPaymentMethod(payment.getPaymentMethod());
            retry.setAmount(payment.getAmount()); retry.setProvider("VNPAY");
            retry.setTransactionReference(UUID.randomUUID().toString().replace("-",""));
            retry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
            payments.saveAndFlush(retry);
            record(retry,"VNPAY_INIT",null,"PENDING",expires.toString());
            payment=retry;
        }
        return gateway.createPaymentUrl(new VnpayPaymentRequest(payment.getTransactionReference(),payment.getAmount(),
                clientIp,payment.getCreatedAt().toInstant(ZoneOffset.UTC),expires));
    }

    @Override @Transactional
    public Map<String,String> receiveIpn(Map<String,List<String>> data) {
        if(!validReturn(data)) return reply("97","Invalid signature or merchant");
        String reference=one(data,"vnp_TxnRef");
        if(!reference.matches("[A-Za-z0-9]{1,100}")) return reply("01","Order not found");
        Payment payment=payments.findByProviderAndTransactionReference("VNPAY",reference).orElse(null);
        if(payment==null) return reply("01","Order not found");
        Order order=payment.getOrder(); lock(order); em.refresh(payment,LockModeType.PESSIMISTIC_WRITE);
        String amount=one(data,"vnp_Amount");
        if(!amount.matches("[0-9]{1,12}") || !payment.getAmount().movePointRight(2).toBigIntegerExact().toString().equals(amount)
                || payment.getAmount().compareTo(order.getGrandTotal())!=0)
            return reply("04","Invalid amount");
        String response=one(data,"vnp_ResponseCode"), transactionStatus=one(data,"vnp_TransactionStatus");
        String transaction=one(data,"vnp_TransactionNo");
        if(!response.matches("[0-9]{2}") || !transactionStatus.matches("[0-9]{2}")
                || !transaction.matches("[0-9]{1,255}")) return reply("99","Invalid result");
        if(audit.findFirstByPaymentPaymentIdAndEventTypeOrderByTransactionIdAsc(payment.getPaymentId(),"VNPAY_RECEIPT").isPresent()
                || payment.getStatus()==Payment.Status.PAID) return reply("02","Order already confirmed");
        boolean success="00".equals(response) && "00".equals(transactionStatus);
        if(!success) {
            if(payment.getStatus()==Payment.Status.PENDING) {
                payment.setStatus(Payment.Status.FAILED);
                record(payment,"VNPAY_FAILURE",transaction,"FAILED",response+":"+transactionStatus);
            }
            return reply("00","Confirm success");
        }
        if(audit.existsByEventTypeAndProviderTransactionId("VNPAY_RECEIPT",transaction)) {
            if(payment.getStatus()!=Payment.Status.PROCESSING) {
                payment.setStatus(Payment.Status.PROCESSING);
                record(payment,"VNPAY_CONFLICT",transaction,"REVIEW",null);
            }
            return reply("99","Transaction requires reconciliation");
        }
        // A valid late success is money received, but must never resurrect a cancelled order.
        boolean fulfill=order.getStatus()==Order.Status.PENDING && "UNPAID".equals(order.getPaymentStatus())
                && payment.getStatus()==Payment.Status.PENDING && Instant.now().isBefore(expiration(payment))
                && !needsReview(order.getOrderId())
                && payment.getPaymentId().equals(paymentFor(order.getOrderId()).getPaymentId());
        payment.setStatus(fulfill ? Payment.Status.PAID : Payment.Status.PROCESSING);
        payment.setPaidAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        if(fulfill) order.setPaymentStatus("PAID");
        record(payment,"VNPAY_RECEIPT",transaction,fulfill ? "PAID" : "REVIEW",response+":"+transactionStatus);
        em.flush();
        return reply("00","Confirm success");
    }

    @Override
    public boolean validReturn(Map<String,List<String>> data) {
        return gateway.hasValidSignature(data) && settings.tmnCode.equals(one(data,"vnp_TmnCode"));
    }

    @Override @Transactional
    public void expire(Long orderId) {
        Order order=orders.findById(orderId).orElse(null);
        if(order==null) return;
        lock(order);
        if(order.getStatus()!=Order.Status.PENDING || !"UNPAID".equals(order.getPaymentStatus())) return;
        Payment payment=paymentFor(orderId); em.refresh(payment,LockModeType.PESSIMISTIC_WRITE);
        if(Instant.now().isBefore(expiration(payment))) return;
        var children=shops.findByOrderOrderIdOrderByShopOrderIdAsc(orderId);
        children.forEach(child -> em.refresh(child,LockModeType.PESSIMISTIC_WRITE));
        if(children.stream().anyMatch(child -> !"PENDING".equals(child.getStatus()))) return;
        var lines=children.stream().flatMap(child -> child.getItems().stream()).map(item ->
                new OrderInventoryReleaseService.Line(item.getOrderItemId(),item.getVariant().getVariantId(),item.getQuantity())).toList();
        inventory.release(lines,order.getCustomer().getUserId());
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        for(var child:children) {
            child.setStatus("CANCELLED"); child.setCancelledAt(now);
            OrderStatusHistory history=new OrderStatusHistory(); history.setShopOrder(child); history.setStatus("CANCELLED");
            history.setChangedBy(order.getCustomer()); history.setNote("Hết hạn thanh toán VNPAY sandbox."); em.persist(history);
        }
        order.setStatus(Order.Status.CANCELLED); order.setCancelledAt(now);
        if(payment.getStatus()!=Payment.Status.PROCESSING) payment.setStatus(Payment.Status.CANCELLED);
        record(payment,"VNPAY_EXPIRED",null,"CANCELLED",null);
        em.flush(); events.publishEvent(new OrderCancelledEvent(orderId,order.getCustomer().getUserId()));
    }

    private Order owned(Long customerId,Long orderId) {
        return orders.findByOrderIdAndCustomerUserId(orderId,customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private void lock(Order order) { em.refresh(order,LockModeType.PESSIMISTIC_WRITE); }
    private boolean online(Payment p) { return "VNPAY".equals(p.getProvider()) && "VNPAY".equals(p.getPaymentMethod().getMethodCode()); }
    private Payment paymentFor(Long orderId) {
        return payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId).stream().filter(this::online).reduce((first,last) -> last)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private boolean needsReview(Long orderId) {
        return payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId).stream()
                .anyMatch(p -> online(p) && p.getStatus()==Payment.Status.PROCESSING);
    }
    private Instant expiration(Payment payment) {
        return Instant.parse(audit.findFirstByPaymentPaymentIdAndEventTypeOrderByTransactionIdAsc(payment.getPaymentId(),"VNPAY_INIT")
                .orElseThrow(() -> new IllegalStateException("Missing VNPAY initialization audit")).getProviderPayload());
    }
    private void record(Payment payment,String event,String transaction,String status,String payload) {
        PaymentTransaction entry=new PaymentTransaction(); entry.setPayment(payment); entry.setEventType(event);
        entry.setProviderTransactionId(transaction); entry.setAmount(payment.getAmount()); entry.setStatus(status);
        entry.setProviderPayload(payload); audit.save(entry);
    }
    private String one(Map<String,List<String>> data,String key) {
        List<String> values=data==null ? null : data.get(key);
        return values!=null && values.size()==1 && values.getFirst()!=null ? values.getFirst() : "";
    }
    private Map<String,String> reply(String code,String message) { return Map.of("RspCode",code,"Message",message); }
}
