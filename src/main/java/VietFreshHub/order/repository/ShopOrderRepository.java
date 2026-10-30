package VietFreshHub.order.repository;

import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.dto.CustomerShopOrderStatus;
import VietFreshHub.order.dto.OrderListRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long>, JpaSpecificationExecutor<ShopOrder> {

    String CUSTOMER_HISTORY_FROM = "from ShopOrder s join s.order o left join OrderDeliveryView d on d.shopOrder = s "
            + "where o.customer.userId = :customerId "
            + "and (:status = '' or (" + OrderRepository.CUSTOMER_SHOP_STATUS + ") = :status) "
            + "and (:keyword = '' or s.shopOrderId = :referenceId "
            + "or locate(lower(:keyword),lower(o.orderCode)) > 0) "
            + "and (:fromDate is null or o.placedAt >= :fromDate) "
            + "and (:untilDate is null or o.placedAt < :untilDate) ";

    @Query(value = "select new VietFreshHub.order.dto.OrderListRow(s.shopOrderId,o.orderId,"
            + "s.shop.shopName," + OrderRepository.CUSTOMER_SHOP_STATUS + ",s.paymentStatus,s.totalAmount,o.placedAt) "
            + CUSTOMER_HISTORY_FROM + "order by o.placedAt desc,s.shopOrderId desc",
            countQuery = "select count(s.shopOrderId) " + CUSTOMER_HISTORY_FROM)
    Page<OrderListRow> findCustomerHistory(@Param("customerId") Long customerId, @Param("status") String status,
            @Param("keyword") String keyword, @Param("referenceId") Long referenceId,
            @Param("fromDate") LocalDateTime fromDate, @Param("untilDate") LocalDateTime untilDate, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "shop"})
    Optional<ShopOrder> findByShopOrderIdAndOrderCustomerUserId(Long shopOrderId, Long customerId);

    @Query("select new VietFreshHub.order.dto.CustomerShopOrderStatus(s.order.orderId,s.shopOrderId,s.shop.shopName,"
            + OrderRepository.CUSTOMER_SHOP_STATUS + ") from ShopOrder s left join OrderDeliveryView d on d.shopOrder = s "
            + "where s.order.customer.userId = :customerId and s.shopOrderId in :shopOrderIds order by s.shopOrderId")
    List<CustomerShopOrderStatus> findCustomerStatuses(@Param("customerId") Long customerId,
            @Param("shopOrderIds") List<Long> shopOrderIds);

    @Override
    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findAll(Specification<ShopOrder> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"order", "order.customer"})
    Page<ShopOrder> findAll(Specification<ShopOrder> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"shop", "items.variant.product"})
    List<ShopOrder> findByOrderOrderIdOrderByShopOrderIdAsc(Long orderId);

    @Query("select s from ShopOrder s where s.order.orderId = :orderId order by s.shopOrderId")
    List<ShopOrder> findForOrderNotification(@Param("orderId") Long orderId);

    boolean existsByOrder_OrderIdAndStatusNot(Long orderId, String status);

    long countByShopId(Long shopId);

    long countByShopIdAndStatus(Long shopId, String status);

    long countByShopIdAndStatusIn(Long shopId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopId(Long shopId);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopIdAndStatus(Long shopId, String status);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopIdAndStatusInOrderByOrderPlacedAtDesc(Long shopId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"order", "order.customer", "shop", "items"})
    Optional<ShopOrder> findByShopOrderIdAndShopId(Long shopOrderId, Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShopOrder> findForUpdateByShopOrderIdAndShopId(Long shopOrderId, Long shopId);
}
