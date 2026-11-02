package VietFreshHub.order.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.order.entity.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** Shared prefix for order mutations: all Customers, then all Orders, in ID order. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderLifecycleLock {
    private final EntityManager em;

    public Order lock(Long orderId) {
        return lockAll(List.of(orderId)).getFirst();
    }

    public List<Order> lockAll(Collection<Long> orderIds) {
        var owners = new TreeMap<Long, Long>();
        for (Long orderId : new TreeSet<>(orderIds)) {
            Long customerId = em.createQuery("select o.customer.userId from Order o where o.orderId = :id", Long.class)
                    .setParameter("id", orderId).getResultStream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
            owners.put(orderId, customerId);
        }
        for (Long customerId : new TreeSet<>(owners.values())) {
            refreshOnce(em.getReference(User.class, customerId));
        }
        return owners.entrySet().stream().map(entry -> {
            Order order = em.find(Order.class, entry.getKey());
            if (order == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
            refreshOnce(order);
            if (!entry.getValue().equals(order.getCustomer().getUserId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Khách hàng của đơn vừa thay đổi.");
            }
            return order;
        }).toList();
    }

    public void lockForDelivery(Long deliveryId) {
        Long orderId = em.createQuery("select d.shopOrder.order.orderId from Delivery d where d.deliveryId = :id", Long.class)
                .setParameter("id", deliveryId).getResultStream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin giao hàng."));
        lock(orderId);
    }

    private void refreshOnce(Object entity) {
        // Reentrant calls must not discard changes made earlier in this transaction.
        LockModeType mode = em.getLockMode(entity);
        if (mode != LockModeType.PESSIMISTIC_WRITE && mode != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
            em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        }
    }
}
