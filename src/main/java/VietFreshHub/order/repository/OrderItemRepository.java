package VietFreshHub.order.repository;

import VietFreshHub.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByShopOrderId(Long shopOrderId);

    List<OrderItem> findByShopOrder_Order_Customer_UserIdAndShopOrder_StatusAndVariant_Product_ProductId(
            Long userId, String status, Long productId);

    interface AdminBestSellingProduct {
        String getProductName();
        String getUnit();
        Long getUnitsSold();
    }

    @Query("""
            select i.productNameSnapshot as productName, v.unit as unit, sum(i.quantity) as unitsSold
            from OrderItem i join i.shopOrder s join s.order o join i.variant v
            join VietFreshHub.delivery.entity.Delivery d on d.shopOrder = s
            where d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED
              and s.status = 'COMPLETED' and o.status = VietFreshHub.order.entity.Order.Status.COMPLETED
              and d.deliveredAt >= :from and d.deliveredAt < :to
            group by v.product.productId, i.productNameSnapshot, v.unit
            order by sum(i.quantity) desc, i.productNameSnapshot, v.unit
            """)
    List<AdminBestSellingProduct> findAdminBestSellers(@Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to,
                                                       org.springframework.data.domain.Pageable pageable);
}
