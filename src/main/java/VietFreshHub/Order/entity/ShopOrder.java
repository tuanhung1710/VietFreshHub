package VietFreshHub.Order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "shop_orders")
public class ShopOrder {

    @Id
    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "status")
    private String status;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    public void confirm() {
        this.status = "CONFIRMED";
    }

    public void startPreparing() {
        this.status = "PREPARING";
    }

    public void markReady() {
        this.status = "READY_FOR_DELIVERY";
    }

    public void markOutForDelivery() {
        if (!"READY_FOR_DELIVERY".equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể bắt đầu giao hàng cho đơn hàng sẵn sàng giao hàng.");
        }
        this.status = "OUT_FOR_DELIVERY";
    }

    public void markCompleted() {
        if (!"OUT_FOR_DELIVERY".equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể hoàn tất đơn hàng đang giao hàng.");
        }
        this.status = "COMPLETED";
    }
}
