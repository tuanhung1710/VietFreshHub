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

/** Active alert episodes for this application instance; reset when the process restarts. */
@Component
public class InventoryAlertStateStore {
    private final ConcurrentMap<Long, ShopState> shops = new ConcurrentHashMap<>();

    public void reconcile(Long shopId,
                          UnaryOperator<Map<InventoryAlertType, Set<Long>>> scan) {
        ShopState state = shops.computeIfAbsent(shopId, ignored -> new ShopState());
        state.permit.acquireUninterruptibly();
        boolean deferred = false;
        try {
            var next = new EnumMap<InventoryAlertType, Set<Long>>(InventoryAlertType.class);
            scan.apply(state.active).forEach((type, ids) -> {
                if (!ids.isEmpty()) next.put(type, Set.copyOf(ids));
            });
            Map<InventoryAlertType, Set<Long>> snapshot = Map.copyOf(next);
            if (TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            if (status == STATUS_COMMITTED) state.active = snapshot;
                        } finally {
                            state.permit.release();
                        }
                    }
                });
                // Keep same-shop scans waiting until both the database and memory state are complete.
                deferred = true;
            } else {
                state.active = snapshot;
            }
        } finally {
            if (!deferred) state.permit.release();
        }
    }

    private static final class ShopState {
        private final Semaphore permit = new Semaphore(1, true);
        private Map<InventoryAlertType, Set<Long>> active = Map.of();
    }
}
