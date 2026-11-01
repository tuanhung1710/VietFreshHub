package VietFreshHub.order.repository;

import VietFreshHub.order.entity.ShopOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long>, JpaSpecificationExecutor<ShopOrder> {

    interface SalesTotals {
        java.math.BigDecimal getSalesAmount();
        java.math.BigDecimal getDiscounts();
        java.math.BigDecimal getShippingFees();
        Long getCompletedOrders();
    }

    interface StatusCount {
        String getStatus();
        Long getOrderCount();
    }

    @Query("""
            select coalesce(sum(s.subtotal), 0) as salesAmount,
                   coalesce(sum(s.discountTotal), 0) as discounts,
                   coalesce(sum(s.shippingFee), 0) as shippingFees,
                   count(s) as completedOrders
            from ShopOrder s
            where s.shopId = :shopId and s.status = 'COMPLETED'
              and s.completedAt >= :from and s.completedAt < :to
            """)
    SalesTotals getCompletedSalesTotals(@Param("shopId") Long shopId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("""
            select s.status as status, count(s) as orderCount
            from ShopOrder s join s.order o
            where s.shopId = :shopId and o.placedAt >= :from and o.placedAt < :to
            group by s.status order by s.status
            """)
    List<StatusCount> getOrderCountsByStatus(@Param("shopId") Long shopId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    @Query("""
            select count(distinct o.customer.userId)
            from ShopOrder s join s.order o
            where s.shopId = :shopId and o.placedAt >= :from and o.placedAt < :to
            """)
    long countDistinctCustomers(@Param("shopId") Long shopId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    @Override
    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findAll(Specification<ShopOrder> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"order", "order.customer"})
    Page<ShopOrder> findAll(Specification<ShopOrder> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"shop", "items.variant.product"})
    List<ShopOrder> findByOrderOrderIdOrderByShopOrderIdAsc(Long orderId);

    boolean existsByOrder_OrderIdAndStatusNot(Long orderId, String status);

    long countByShopId(Long shopId);

    long countByShopIdAndStatus(Long shopId, String status);

    long countByShopIdAndStatusIn(Long shopId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopId(Long shopId);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopIdAndStatus(Long shopId, String status);

    List<ShopOrder> findByOrder_Customer_UserIdAndShopIdAndStatus(Long userId, Long shopId, String status);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopIdAndStatusInOrderByOrderPlacedAtDesc(Long shopId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<ShopOrder> findByShopOrderIdAndShopId(Long shopOrderId, Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShopOrder> findForUpdateByShopOrderIdAndShopId(Long shopOrderId, Long shopId);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findTop5ByShopIdAndOrder_PlacedAtGreaterThanEqualAndOrder_PlacedAtLessThanOrderByOrder_PlacedAtDesc(
            Long shopId, LocalDateTime from, LocalDateTime to);
}
