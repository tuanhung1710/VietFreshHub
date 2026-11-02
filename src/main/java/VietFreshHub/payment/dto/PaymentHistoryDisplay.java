package VietFreshHub.payment.dto;

/** Payment History only: no persisted status transitions. */
public final class PaymentHistoryDisplay {
    private PaymentHistoryDisplay() {}
    public static java.util.Map<String,String> options() {
        var labels=new java.util.LinkedHashMap<String,String>();
        labels.put("PENDING","Chưa thanh toán"); labels.put("PAID","Đã thanh toán");
        labels.put("FAILED","Thanh toán thất bại"); labels.put("CANCELLED","Thanh toán bị hủy");
        labels.put("REFUND_PROCESSING","Đang xử lý hoàn tiền"); return labels;
    }
    public static final String ALL_CANCELLED = "exists (select s.shopOrderId from ShopOrder s where s.order=o) and not exists (select s.shopOrderId from ShopOrder s where s.order=o and (s.status <> 'CANCELLED' or s.cancelledAt is null))";
    public static final String REFUND = "p.paymentMethod.methodCode in ('VNPAY','BANK_TRANSFER') and p.status = PAID and p.amount > 0 and p.amount=o.grandTotal and (" + ALL_CANCELLED + ") and not exists (select s.shopOrderId from ShopOrder s where s.order=o and (s.paymentStatus <> 'PAID' or s.confirmedAt is not null))";
    public static final String CASE = "case when " + REFUND + " then 'REFUND_PROCESSING' when p.status in (PENDING,PROCESSING) and (" + ALL_CANCELLED + ") then 'CANCELLED' when p.status in (PENDING,PROCESSING) then 'PENDING' when p.status=FAILED then 'FAILED' when p.status=CANCELLED then 'CANCELLED' when p.status in (REFUNDED,PARTIALLY_REFUNDED) then 'REFUND_PROCESSING' else 'PAID' end";
    public static final String SHOP_CASE = "case when p.paymentMethod.methodCode='COD' then case when s.paymentStatus='PAID' then 'PAID' when s.status='CANCELLED' and s.cancelledAt is not null then 'CANCELLED' else 'PENDING' end when p.status=PAID and p.paymentMethod.methodCode in ('VNPAY','BANK_TRANSFER') and p.amount=o.grandTotal and p.amount>0 and s.totalAmount>0 and s.totalAmount<=p.amount and s.paymentStatus='PAID' and s.status='CANCELLED' and s.cancelledAt is not null and s.confirmedAt is null then 'REFUND_PROCESSING' when p.status in (PENDING,PROCESSING) and s.status='CANCELLED' and s.cancelledAt is not null then 'CANCELLED' when p.status in (PENDING,PROCESSING) then 'PENDING' when p.status=FAILED then 'FAILED' when p.status=CANCELLED then 'CANCELLED' when p.status in (REFUNDED,PARTIALLY_REFUNDED) then 'REFUND_PROCESSING' else 'PAID' end";
}
