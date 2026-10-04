package VietFreshHub.Order.repository;

import VietFreshHub.Order.entity.ShopOrder;
import jakarta.persistence.LockModeType;
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

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long>, JpaSpecificationExecutor<ShopOrder> {

    @Override
    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findAll(Specification<ShopOrder> specification, Sort sort);

    long countByShopId(Long shopId);

    long countByShopIdAndStatus(Long shopId, String status);

    long countByShopIdAndStatusIn(Long shopId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopId(Long shopId);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopIdAndStatus(Long shopId, String status);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<ShopOrder> findByShopIdAndStatusInOrderByOrderPlacedAtDesc(Long shopId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<ShopOrder> findByShopOrderIdAndShopId(Long shopOrderId, Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShopOrder> findForUpdateByShopOrderIdAndShopId(Long shopOrderId, Long shopId);
}
