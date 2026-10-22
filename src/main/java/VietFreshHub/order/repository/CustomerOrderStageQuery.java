package VietFreshHub.order.repository;

/**
 * Read-only display stage for order alias o, shared by history filtering and details.
 * Child states take precedence over a stale parent. Ignore terminal shops when choosing
 * the earliest outstanding stage: confirmation, preparation, handover, transportation.
 * All cancelled => cancelled; all terminal with at least one completed => completed.
 * A failed delivery remains unresolved fulfillment, never evidence of a refund.
 * Parent-only records use conservative fallback stages; no persistence is changed.
 */
public final class CustomerOrderStageQuery {
    private CustomerOrderStageQuery() {}

    private static final String TRANSPORT_STARTED = """
            exists (select d.deliveryId from OrderDeliveryView d where d.shopOrder = s
                and d.status in ('PICKED_UP','OUT_FOR_DELIVERY','DELIVERED','FAILED','REDELIVERY_SCHEDULED'))
            """;
    public static final String EXPRESSION = """
            case
              when exists (select s.shopOrderId from ShopOrder s where s.order = o)
                and not exists (select s.shopOrderId from ShopOrder s where s.order = o and s.status <> 'CANCELLED')
                then 'CANCELLED'
              when exists (select s.shopOrderId from ShopOrder s where s.order = o and s.status = 'COMPLETED')
                and not exists (select s.shopOrderId from ShopOrder s where s.order = o and s.status not in ('COMPLETED','CANCELLED'))
                then 'COMPLETED'
              when not exists (select s.shopOrderId from ShopOrder s where s.order = o)
                and o.status = 'CANCELLED' then 'CANCELLED'
              when not exists (select s.shopOrderId from ShopOrder s where s.order = o)
                and o.status = 'COMPLETED' then 'COMPLETED'
              when o.paymentStatus not in ('PAID','REFUNDED','PARTIALLY_REFUNDED')
                and not exists (select p.paymentId from Payment p where p.order = o and p.status in ('PAID','REFUNDED','PARTIALLY_REFUNDED'))
                and not exists (select s.shopOrderId from ShopOrder s where s.order = o
                    and (s.status not in ('PENDING','CONFIRMED','CANCELLED') or
            """ + TRANSPORT_STARTED + """
                    ))
                and exists (select p.paymentId from Payment p where p.order = o
                    and p.paymentMethod.methodCode <> 'COD' and p.status in ('PENDING','PROCESSING','FAILED')
                    and not exists (select newer.paymentId from Payment newer where newer.order = o and newer.paymentId > p.paymentId))
                then 'WAITING_PAYMENT'
              when exists (select s.shopOrderId from ShopOrder s where s.order = o
                    and s.status = 'PENDING' and not
            """ + TRANSPORT_STARTED + """
                    ) then 'WAITING_CONFIRMATION'
              when exists (select s.shopOrderId from ShopOrder s where s.order = o
                    and s.status in ('CONFIRMED','PREPARING') and not
            """ + TRANSPORT_STARTED + """
                    ) then 'PREPARING'
              when exists (select s.shopOrderId from ShopOrder s where s.order = o
                    and s.status = 'READY_FOR_DELIVERY' and not
            """ + TRANSPORT_STARTED + """
                    ) then 'AWAITING_DELIVERY'
              when exists (select s.shopOrderId from ShopOrder s where s.order = o
                    and s.status not in ('COMPLETED','CANCELLED')
                    and (s.status = 'OUT_FOR_DELIVERY' or
            """ + TRANSPORT_STARTED + """
                    )) then 'SHIPPING'
              when o.status = 'PENDING' then 'WAITING_CONFIRMATION'
              else 'PREPARING'
            end
            """;
}
