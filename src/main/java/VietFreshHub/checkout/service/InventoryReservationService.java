package VietFreshHub.checkout.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.checkout.entity.CheckoutInventoryBatch;
import VietFreshHub.checkout.entity.CheckoutStockAllocation;
import VietFreshHub.checkout.entity.CheckoutStockMovement;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.checkout.exception.CheckoutStockException;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.order.entity.OrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Giữ hàng theo lô, ưu tiên lô hết hạn trước. */
@Service
@RequiredArgsConstructor
public class InventoryReservationService {
    private final EntityManager entityManager;

    public record Reservation(Long orderItemId, Long variantId, int quantity) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<Reservation> reservations, Long customerId) {
        User actor = entityManager.getReference(User.class, customerId);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        // Mọi yêu cầu đều khóa theo cùng thứ tự để giảm nguy cơ deadlock.
        List<Reservation> sortedReservations = new ArrayList<>(reservations);
        sortedReservations.sort(Comparator.comparing(Reservation::variantId)
                .thenComparing(Reservation::orderItemId));

        for (Reservation reservation : sortedReservations) {
            validateReservation(reservation);
            List<CheckoutInventoryBatch> batches = lockSellableBatches(reservation.variantId(), today);
            checkAvailableQuantity(batches, reservation);
            reserveFromBatches(batches, reservation, actor);
        }
    }

    private void validateReservation(Reservation reservation) {
        if (reservation.orderItemId() == null || reservation.variantId() == null || reservation.quantity() < 1) {
            throw new CheckoutException("Yêu cầu giữ kho không hợp lệ.");
        }
    }

    private List<CheckoutInventoryBatch> lockSellableBatches(Long variantId, LocalDate today) {
        // Khóa theo mã lô trước, rồi mới sắp xếp theo hạn sử dụng để phân bổ hàng.
        String query = "select b from CheckoutInventoryBatch b where b.variant.variantId=:variantId "
                + "and b.status='ACTIVE' and (b.expiryDate is null or b.expiryDate>=:today) order by b.batchId";
        List<CheckoutInventoryBatch> batches = entityManager.createQuery(query, CheckoutInventoryBatch.class)
                .setParameter("variantId", variantId)
                .setParameter("today", today)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        // Nếu entity đã được đọc trước khi chờ khóa, lấy lại số lượng sau khi có khóa.
        for (CheckoutInventoryBatch batch : batches) {
            entityManager.refresh(batch, LockModeType.PESSIMISTIC_WRITE);
        }
        return batches;
    }

    private void checkAvailableQuantity(List<CheckoutInventoryBatch> batches, Reservation reservation) {
        long availableQuantity = 0;
        for (CheckoutInventoryBatch batch : batches) {
            availableQuantity += (long) batch.getQuantityOnHand() - batch.getReservedQuantity();
        }
        if (availableQuantity < reservation.quantity()) {
            CatalogVariant variant = entityManager.getReference(CatalogVariant.class, reservation.variantId());
            throw new CheckoutStockException(reservation.variantId(), variant.getProduct().getName(),
                    variant.getVariantName(), reservation.quantity(), availableQuantity);
        }
    }

    private void reserveFromBatches(List<CheckoutInventoryBatch> batches, Reservation reservation, User actor) {
        List<CheckoutInventoryBatch> sortedBatches = new ArrayList<>(batches);
        sortedBatches.sort(Comparator.comparing(CheckoutInventoryBatch::getExpiryDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CheckoutInventoryBatch::getBatchId));

        int remainingQuantity = reservation.quantity();
        for (CheckoutInventoryBatch batch : sortedBatches) {
            int availableQuantity = batch.getQuantityOnHand() - batch.getReservedQuantity();
            int reservedQuantity = Math.min(remainingQuantity, availableQuantity);
            if (reservedQuantity <= 0) {
                continue;
            }

            batch.setReservedQuantity(batch.getReservedQuantity() + reservedQuantity);
            createAllocation(batch, reservation.orderItemId(), reservedQuantity);
            recordStockMovement(batch, reservation.orderItemId(), reservedQuantity, actor);

            remainingQuantity -= reservedQuantity;
            if (remainingQuantity == 0) {
                break;
            }
        }
    }

    private void createAllocation(CheckoutInventoryBatch batch, Long orderItemId, int quantity) {
        CheckoutStockAllocation allocation = new CheckoutStockAllocation();
        allocation.setOrderItem(entityManager.getReference(OrderItem.class, orderItemId));
        allocation.setBatch(batch);
        allocation.setQuantity(quantity);
        entityManager.persist(allocation);
    }

    private void recordStockMovement(CheckoutInventoryBatch batch, Long orderItemId, int quantity, User actor) {
        CheckoutStockMovement movement = new CheckoutStockMovement();
        movement.setVariant(batch.getVariant());
        movement.setBatch(batch);
        movement.setTransactionType("RESERVE");
        movement.setQuantity(quantity);
        movement.setReferenceType("ORDER_ITEM");
        movement.setReferenceId(orderItemId);
        movement.setCreatedBy(actor);
        movement.setNote("Giữ kho khi đặt đơn COD.");
        entityManager.persist(movement);
    }
}
