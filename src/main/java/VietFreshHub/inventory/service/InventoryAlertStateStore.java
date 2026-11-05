package VietFreshHub.inventory.service;

import VietFreshHub.inventory.enums.InventoryAlertType;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;

/**
 * Theo dõi đợt cảnh báo trong tiến trình hiện tại; khởi động lại ứng dụng sẽ bắt đầu theo dõi lại.
 * Chỉ cập nhật bộ nhớ sau khi giao dịch thành công để lần quét sau không bỏ sót cảnh báo bị rollback.
 */
@Component
public class InventoryAlertStateStore {
    private final ConcurrentMap<Long, ShopState> shops = new ConcurrentHashMap<>();

    /** Mỗi cửa hàng chỉ có một lượt quét; giữ khóa đến khi giao dịch và trạng thái bộ nhớ cùng hoàn tất. */
    public void reconcile(Long shopId, UnaryOperator<Map<InventoryAlertType, Set<Long>>> scan) {
        ShopState state = shops.computeIfAbsent(shopId, ignored -> new ShopState());
        state.permit.acquireUninterruptibly();
        boolean deferred = false;
        try {
            var next = new EnumMap<InventoryAlertType, Set<Long>>(InventoryAlertType.class);
            scan.apply(state.active)
                    .forEach(
                            (type, ids) -> {
                                if (!ids.isEmpty()) {
                                    next.put(type, Set.copyOf(ids));
                                }
                            });
            Map<InventoryAlertType, Set<Long>> snapshot = Map.copyOf(next);
            if (TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                try {
                                    if (status == STATUS_COMMITTED) {
                                        state.active = snapshot;
                                    }
                                } finally {
                                    state.permit.release();
                                }
                            }
                        });
                // Lượt quét kế tiếp phải chờ commit/rollback rồi mới được đọc trạng thái cảnh báo.
                deferred = true;
            } else {
                state.active = snapshot;
            }
        } finally {
            if (!deferred) {
                state.permit.release();
            }
        }
    }

    private static final class ShopState {
        private final Semaphore permit = new Semaphore(1, true);
        private Map<InventoryAlertType, Set<Long>> active = Map.of();
    }
}
