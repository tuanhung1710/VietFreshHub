package VietFreshHub.payout.service;

import VietFreshHub.payout.dto.AdminPayoutRow;
import VietFreshHub.payout.dto.PayoutBatchExport;
import VietFreshHub.payout.dto.PayoutBatchView;
import VietFreshHub.payout.dto.ShopPayoutAccountForm;
import VietFreshHub.payout.dto.ShopPayoutAccountView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SellerPayoutService {

    void createForDeliveredOrder(Long shopOrderId, LocalDateTime deliveredAt);

    void recordOnlinePayment(Long orderId, LocalDateTime paidAt);

    boolean createScheduledBatch(LocalDateTime scheduledUtc, String scheduledSlot);

    Long createManualBatch(List<Long> payoutIds);

    List<AdminPayoutRow> getQueue();

    List<ShopPayoutAccountView> getAccounts();

    ShopPayoutAccountForm getShopPayoutAccount(Long shopId);

    List<PayoutBatchView> getBatchesCreatedBetween(LocalDate fromDate, LocalDate toDate);

    BigDecimal getCommissionRate();

    void saveShopAccount(Long shopId, String bankCode, String bankName, String accountNumber,
                         String accountHolder, Long changedBy);

    void confirmCodRemittance(Long payoutId, Long confirmedBy);

    PayoutBatchExport getBatchExport(Long batchId);
}
