package VietFreshHub.payout.service;

import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import VietFreshHub.payout.dto.AdminPayoutRow;
import VietFreshHub.payout.dto.PayoutBatchExport;
import VietFreshHub.payout.dto.PayoutBatchExportItem;
import VietFreshHub.payout.dto.PayoutBatchShopTransfer;
import VietFreshHub.payout.dto.PayoutBatchView;
import VietFreshHub.payout.dto.ShopPayoutAccountForm;
import VietFreshHub.payout.dto.ShopPayoutAccountView;
import VietFreshHub.payout.entity.SellerPayout;
import VietFreshHub.payout.entity.SellerPayoutBatch;
import VietFreshHub.payout.entity.SellerPayoutBatchItem;
import VietFreshHub.payout.entity.ShopPayoutAccount;
import VietFreshHub.payout.repository.SellerPayoutBatchItemRepository;
import VietFreshHub.payout.repository.SellerPayoutBatchRepository;
import VietFreshHub.payout.repository.SellerPayoutRepository;
import VietFreshHub.payout.repository.ShopPayoutAccountRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerPayoutServiceImpl implements SellerPayoutService {

    private static final List<SellerPayout.Status> BATCHABLE_STATUSES =
            List.of(SellerPayout.Status.PENDING, SellerPayout.Status.FAILED);

    private final SellerPayoutRepository payouts;
    private final SellerPayoutBatchRepository batches;
    private final SellerPayoutBatchItemRepository batchItems;
    private final ShopPayoutAccountRepository accounts;
    private final ShopRepository shops;
    private final ShopOrderRepository shopOrders;
    private final PaymentRepository payments;
    private final EntityManager entityManager;

    @Value("${app.payout.commission-rate:0.10}")
    private BigDecimal commissionRate;

    @Override
    @Transactional
    public void createForDeliveredOrder(Long shopOrderId, LocalDateTime deliveredAt) {
        if (shopOrderId == null || deliveredAt == null) return;
        ShopOrder shopOrder = shopOrders.findById(shopOrderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng của cửa hàng."));
        if (!"COMPLETED".equals(shopOrder.getStatus())) return;

        SellerPayout existing = payouts.findByShopOrderIdForUpdate(shopOrderId).orElse(null);
        if (existing != null) {
            if (existing.getEligibleAt() == null) existing.setEligibleAt(deliveredAt.plusHours(24));
            return;
        }

        PaymentState paymentState = paymentState(shopOrder.getOrder().getOrderId());
        payouts.save(newPayout(shopOrder, deliveredAt.plusHours(24), paymentState.source(), paymentState.confirmedAt()));
    }

    @Override
    @Transactional
    public void recordOnlinePayment(Long orderId, LocalDateTime paidAt) {
        if (orderId == null) return;
        PaymentState state = paymentState(orderId);
        if (state.source() != SellerPayout.FundsSource.ONLINE) {
            throw new IllegalStateException("Không thể ghi nhận tiền online khi payment chưa được xác nhận PAID.");
        }
        LocalDateTime confirmedAt = paidAt == null ? state.confirmedAt() : paidAt;
        validateCommissionRate();
        for (ShopOrder shopOrder : shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(orderId)) {
            SellerPayout payout = payouts.findByShopOrderIdForUpdate(shopOrder.getShopOrderId()).orElse(null);
            if (payout == null) {
                LocalDateTime eligibleAt = shopOrder.getCompletedAt() == null
                        ? null : shopOrder.getCompletedAt().plusHours(24);
                payouts.save(newPayout(shopOrder, eligibleAt, SellerPayout.FundsSource.ONLINE, confirmedAt));
            } else if (payout.getStatus() != SellerPayout.Status.PAID
                    && payout.getStatus() != SellerPayout.Status.CANCELLED) {
                payout.setFundsSource(SellerPayout.FundsSource.ONLINE);
                payout.setFundsConfirmedAt(confirmedAt);
            }
        }
    }

    @Override
    @Transactional
    public boolean createScheduledBatch(LocalDateTime scheduledUtc, String scheduledSlot) {
        if (batches.existsByScheduledSlot(scheduledSlot)) return false;
        LocalDateTime now = utcNow();
        List<SellerPayout> candidates = payouts.findByStatusInOrderByEligibleAtAscPayoutIdAsc(BATCHABLE_STATUSES);
        Set<Long> candidateShopIds = candidates.stream()
                .filter(payout -> payout.getEligibleAt() != null && !payout.getEligibleAt().isAfter(now))
                .filter(payout -> payout.getFundsConfirmedAt() != null)
                .map(SellerPayout::getShopId).collect(Collectors.toSet());
        if (candidateShopIds.isEmpty()) return false;

        Map<Long, ShopPayoutAccount> verifiedAccounts = accounts.findByShopIdInAndVerifiedTrue(candidateShopIds)
                .stream().collect(Collectors.toMap(ShopPayoutAccount::getShopId, Function.identity()));
        if (verifiedAccounts.isEmpty()) return false;

        List<SellerPayout> eligible = payouts.lockEligibleForBatch(BATCHABLE_STATUSES, now, verifiedAccounts.keySet());
        if (eligible.isEmpty()) return false;

        createBatch(eligible, verifiedAccounts, scheduledUtc, scheduledSlot);
        return true;
    }

    @Override
    @Transactional
    public Long createManualBatch(List<Long> payoutIds) {
        if (payoutIds == null || payoutIds.isEmpty() || payoutIds.stream().anyMatch(Objects::isNull)) {
            throw badRequest("Chọn ít nhất một khoản payout đủ điều kiện.");
        }

        List<Long> selectedIds = payoutIds.stream().distinct().sorted().toList();
        LocalDateTime now = utcNow();
        List<SellerPayout> selected = new ArrayList<>(selectedIds.size());
        for (Long payoutId : selectedIds) {
            SellerPayout payout = payouts.findByPayoutIdForUpdate(payoutId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Không tìm thấy khoản payout đã chọn."));
            if (!BATCHABLE_STATUSES.contains(payout.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Một khoản đã chọn không còn ở trạng thái chờ payout.");
            }
            if (payout.getEligibleAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Một khoản đã chọn chưa được xác nhận giao thành công.");
            }
            if (payout.getFundsConfirmedAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Một khoản đã chọn chưa được xác nhận tiền về sàn.");
            }
            selected.add(payout);
        }

        Set<Long> selectedShopIds = selected.stream().map(SellerPayout::getShopId).collect(Collectors.toSet());
        Map<Long, ShopPayoutAccount> verifiedAccounts = accounts.findByShopIdInAndVerifiedTrue(selectedShopIds)
                .stream().collect(Collectors.toMap(ShopPayoutAccount::getShopId, Function.identity()));
        if (selectedShopIds.stream().anyMatch(shopId -> !verifiedAccounts.containsKey(shopId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mỗi shop được chọn phải có tài khoản payout đã xác minh.");
        }

        String manualSlot = "MANUAL-" + UUID.randomUUID().toString().replace("-", "");
        SellerPayoutBatch batch = createBatch(selected, verifiedAccounts, now, manualSlot);
        return batch.getBatchId();
    }

    private SellerPayoutBatch createBatch(List<SellerPayout> eligible,
                                          Map<Long, ShopPayoutAccount> verifiedAccounts,
                                          LocalDateTime scheduledAt, String scheduledSlot) {
        SellerPayoutBatch batch = new SellerPayoutBatch();
        batch.setScheduledSlot(scheduledSlot);
        batch.setScheduledAt(scheduledAt);
        batch.setStatus(SellerPayoutBatch.Status.READY);
        batch.setPayoutCount(eligible.size());
        batch.setShopCount((int) eligible.stream().map(SellerPayout::getShopId).distinct().count());
        batch.setTotalAmount(eligible.stream().map(SellerPayout::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        batches.saveAndFlush(batch);

        List<SellerPayoutBatchItem> newItems = new ArrayList<>(eligible.size());
        for (SellerPayout payout : eligible) {
            ShopPayoutAccount account = verifiedAccounts.get(payout.getShopId());
            SellerPayoutBatchItem item = new SellerPayoutBatchItem();
            item.setBatch(batch);
            item.setPayout(payout);
            item.setShopId(payout.getShopId());
            item.setShopNameSnapshot(account.getShop().getShopName());
            item.setBankNameSnapshot(account.getBankName());
            item.setBankCodeSnapshot(account.getBankCode());
            item.setAccountNumberSnapshot(account.getAccountNumber());
            item.setAccountHolderSnapshot(account.getAccountHolder());
            item.setTransferAmount(payout.getNetAmount());
            item.setStatus(SellerPayoutBatchItem.Status.PENDING);
            newItems.add(item);
            payout.setStatus(SellerPayout.Status.PROCESSING);
        }
        batchItems.saveAll(newItems);
        return batch;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminPayoutRow> getQueue() {
        Map<Long, ShopPayoutAccount> verifiedByShop = accounts.findAll().stream()
                .filter(ShopPayoutAccount::isVerified)
                .collect(Collectors.toMap(ShopPayoutAccount::getShopId, Function.identity()));
        return payouts.findByStatusInOrderByEligibleAtAscPayoutIdAsc(
                        List.of(SellerPayout.Status.PENDING, SellerPayout.Status.PROCESSING, SellerPayout.Status.FAILED))
                .stream().map(payout -> {
                    boolean hasVerifiedAccount = verifiedByShop.containsKey(payout.getShopId());
                    boolean canManualBatch = BATCHABLE_STATUSES.contains(payout.getStatus())
                            && payout.getEligibleAt() != null
                            && payout.getFundsConfirmedAt() != null && hasVerifiedAccount;
                    return new AdminPayoutRow(
                            payout.getPayoutId(), payout.getShopId(), payout.getShop().getShopName(),
                            payout.getShopOrder().getShopOrderId(), payout.getShopOrder().getOrder().getOrderCode(),
                            payout.getFundsSource().name(), payout.getShopOrder().getSubtotal(),
                            payout.getShopOrder().getDiscountTotal(), payout.getGrossAmount(),
                            payout.getCommissionAmount(), payout.getNetAmount(), payout.getStatus().name(),
                            payout.getEligibleAt(), payout.getFundsConfirmedAt(), hasVerifiedAccount, canManualBatch);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShopPayoutAccountView> getAccounts() {
        Map<Long, ShopPayoutAccount> byShop = accounts.findAllByOrderByShopShopNameAsc().stream()
                .collect(Collectors.toMap(ShopPayoutAccount::getShopId, Function.identity()));
        return shops.findAllByStatusOrderByShopNameAsc("ACTIVE").stream().map(shop -> {
            ShopPayoutAccount account = byShop.get(shop.getShopId());
            return new ShopPayoutAccountView(shop.getShopId(), shop.getShopName(),
                    account == null ? null : account.getBankName(),
                    account == null ? null : account.getAccountNumber(),
                    account == null ? null : account.getAccountHolder(), account != null && account.isVerified());
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShopPayoutAccountForm getShopPayoutAccount(Long shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng."));
        ShopPayoutAccount account = accounts.findByShopId(shopId).orElse(null);
        return new ShopPayoutAccountForm(shopId, shop.getShopName(),
                account == null ? "" : account.getBankCode(),
                account == null ? "" : account.getBankName(),
                account == null ? "" : account.getAccountNumber(),
                account == null ? "" : account.getAccountHolder(),
                account != null && account.isVerified(), account != null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutBatchView> getRecentBatches() {
        return batches.findAllByOrderByScheduledAtDesc(PageRequest.of(0, 30)).stream().map(batch -> {
            Map<Long, List<SellerPayoutBatchItem>> byShop = batchItems.findForBatch(batch.getBatchId()).stream()
                    .collect(Collectors.groupingBy(SellerPayoutBatchItem::getShopId, LinkedHashMap::new, Collectors.toList()));
            List<PayoutBatchShopTransfer> transfers = byShop.entrySet().stream().map(entry -> {
                List<SellerPayoutBatchItem> items = entry.getValue();
                SellerPayoutBatchItem first = items.getFirst();
                String status = aggregateItemStatus(items);
                String reference = items.stream().map(SellerPayoutBatchItem::getTransferReference)
                        .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
                BigDecimal amount = items.stream().map(SellerPayoutBatchItem::getTransferAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new PayoutBatchShopTransfer(entry.getKey(), first.getShopNameSnapshot(), amount,
                        items.size(), status, reference);
            }).toList();
            return new PayoutBatchView(batch.getBatchId(), batch.getScheduledSlot(), batch.getScheduledAt(),
                    batch.getTotalAmount(), batch.getPayoutCount(), batch.getShopCount(), batch.getStatus().name(), transfers);
        }).toList();
    }

    @Override
    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    @Override
    @Transactional
    public void saveShopAccount(Long shopId, String bankCode, String bankName, String accountNumber,
                                String accountHolder, Long changedBy) {
        if (shopId == null) throw badRequest("Vui lòng chọn cửa hàng.");
        String normalizedCode = required(bankCode, 30, "Mã ngân hàng").toUpperCase(Locale.ROOT);
        String normalizedBankName = required(bankName, 100, "Tên ngân hàng");
        String normalizedNumber = required(accountNumber, 50, "Số tài khoản").replaceAll("\\s+", "");
        String normalizedHolder = required(accountHolder, 150, "Tên chủ tài khoản").toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9._-]+") || !normalizedNumber.matches("[A-Za-z0-9-]+")) {
            throw badRequest("Mã ngân hàng hoặc số tài khoản không hợp lệ.");
        }
        shops.findByShopIdForUpdate(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng."));
        var existingAccount = accounts.findByShopId(shopId);
        boolean isNewAccount = existingAccount.isEmpty();
        ShopPayoutAccount account = existingAccount.orElseGet(() -> {
            ShopPayoutAccount created = new ShopPayoutAccount();
            created.setShopId(shopId);
            return created;
        });
        boolean detailsChanged = !normalizedCode.equals(account.getBankCode())
                || !normalizedBankName.equals(account.getBankName())
                || !normalizedNumber.equals(account.getAccountNumber())
                || !normalizedHolder.equals(account.getAccountHolder());
        account.setShop(shops.getReferenceById(shopId));
        account.setBankCode(normalizedCode);
        account.setBankName(normalizedBankName);
        account.setAccountNumber(normalizedNumber);
        account.setAccountHolder(normalizedHolder);
        if (detailsChanged) account.setVerified(false);
        account.setUpdatedBy(changedBy);
        if (isNewAccount) entityManager.persist(account);
    }

    @Override
    @Transactional
    public void setAccountVerified(Long shopId, boolean verified, Long changedBy) {
        ShopPayoutAccount account = accounts.findByShopId(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Shop chưa khai báo tài khoản payout."));
        account.setVerified(verified);
        account.setUpdatedBy(changedBy);
    }

    @Override
    @Transactional
    public void confirmCodRemittance(Long payoutId, Long confirmedBy) {
        SellerPayout payout = payouts.findByPayoutIdForUpdate(payoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản payout."));
        if (payout.getFundsSource() != SellerPayout.FundsSource.COD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ xác nhận COD sau khi sàn thực nhận tiền từ shipper.");
        }
        if (payout.getStatus() == SellerPayout.Status.PROCESSING || payout.getStatus() == SellerPayout.Status.PAID
                || payout.getStatus() == SellerPayout.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khoản payout đã được đưa vào lô hoặc đã hoàn tất.");
        }
        if (payout.getFundsConfirmedAt() == null) {
            payout.setFundsConfirmedAt(utcNow());
            payout.setFundsConfirmedBy(confirmedBy);
        }
    }

    @Override
    @Transactional
    public void recordTransferResult(Long batchId, Long shopId, boolean successful,
                                    String reference, String failureReason, Long changedBy) {
        SellerPayoutBatch batch = batches.findByBatchId(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lô payout."));
        List<SellerPayoutBatchItem> items = batchItems.findByBatchAndShop(batchId, shopId);
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng trong lô payout.");
        if (items.stream().anyMatch(item -> item.getStatus() != SellerPayoutBatchItem.Status.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kết quả chuyển khoản cho cửa hàng này đã được ghi nhận.");
        }
        String normalizedReference = reference == null ? "" : reference.strip();
        String normalizedReason = failureReason == null ? "" : failureReason.strip();
        if (successful && normalizedReference.isBlank()) throw badRequest("Vui lòng nhập mã tham chiếu giao dịch.");
        if (!successful && normalizedReason.isBlank()) throw badRequest("Vui lòng nhập lý do chuyển khoản thất bại.");
        if (normalizedReference.length() > 255 || normalizedReason.length() > 1000) throw badRequest("Thông tin kết quả vượt quá độ dài cho phép.");

        LocalDateTime now = utcNow();
        for (SellerPayoutBatchItem item : items) {
            item.setStatus(successful ? SellerPayoutBatchItem.Status.PAID : SellerPayoutBatchItem.Status.FAILED);
            item.setTransferReference(successful ? normalizedReference : null);
            item.setFailureReason(successful ? null : normalizedReason);
            item.setPaidAt(successful ? now : null);
            item.setResultBy(changedBy);
            SellerPayout payout = item.getPayout();
            payout.setStatus(successful ? SellerPayout.Status.PAID : SellerPayout.Status.FAILED);
            payout.setProcessedAt(successful ? now : null);
            payout.setTransactionReference(successful ? normalizedReference : null);
        }
        refreshBatchStatus(batch, batchItems.findForBatch(batchId));
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutBatchExport getBatchExport(Long batchId) {
        SellerPayoutBatch batch = batches.findByBatchId(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lô payout."));
        List<PayoutBatchExportItem> items = batchItems.findForBatch(batchId).stream().map(item -> {
            SellerPayout payout = item.getPayout();
            ShopOrder shopOrder = payout.getShopOrder();
            return new PayoutBatchExportItem(item.getShopId(), item.getShopNameSnapshot(), item.getBankNameSnapshot(),
                    item.getBankCodeSnapshot(), item.getAccountNumberSnapshot(), item.getAccountHolderSnapshot(),
                    shopOrder.getShopOrderId(), shopOrder.getOrder().getOrderCode(), shopOrder.getSubtotal(),
                    payout.getGrossAmount(), shopOrder.getDiscountTotal(), payout.getCommissionAmount(),
                    item.getTransferAmount(), item.getStatus().name(), item.getTransferReference());
        }).toList();
        return new PayoutBatchExport(batch.getBatchId(), batch.getScheduledSlot(), batch.getScheduledAt(),
                batch.getTotalAmount(), items);
    }

    private PaymentState paymentState(Long orderId) {
        List<Payment> orderPayments = payments.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        if (!orderPayments.isEmpty() && orderPayments.stream()
                .allMatch(payment -> "COD".equals(payment.getPaymentMethod().getMethodCode()))) {
            return new PaymentState(SellerPayout.FundsSource.COD, null);
        }
        LocalDateTime confirmedAt = orderPayments.stream()
                .filter(payment -> payment.getStatus() == Payment.Status.PAID)
                .filter(payment -> !"COD".equals(payment.getPaymentMethod().getMethodCode()))
                .map(payment -> payment.getPaidAt() == null ? utcNow() : payment.getPaidAt())
                .max(Comparator.naturalOrder()).orElse(null);
        return confirmedAt == null
                ? new PaymentState(SellerPayout.FundsSource.UNCONFIRMED, null)
                : new PaymentState(SellerPayout.FundsSource.ONLINE, confirmedAt);
    }

    private SellerPayout newPayout(ShopOrder shopOrder, LocalDateTime eligibleAt,
                                   SellerPayout.FundsSource source, LocalDateTime fundsConfirmedAt) {
        validateCommissionRate();
        BigDecimal subtotal = money(shopOrder.getSubtotal());
        BigDecimal discount = money(shopOrder.getDiscountTotal());
        BigDecimal eligibleAmount = subtotal.subtract(discount);
        if (eligibleAmount.signum() < 0) {
            throw new IllegalStateException("Tổng giảm giá không được lớn hơn tiền hàng của đơn.");
        }
        SellerPayout payout = new SellerPayout();
        payout.setShopId(shopOrder.getShopId());
        payout.setShop(shopReference(shopOrder.getShopId()));
        payout.setShopOrder(shopOrder);
        payout.setGrossAmount(eligibleAmount);
        BigDecimal commission = eligibleAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        payout.setCommissionAmount(commission);
        payout.setNetAmount(eligibleAmount.subtract(commission));
        payout.setEligibleAt(eligibleAt);
        payout.setFundsSource(source);
        payout.setFundsConfirmedAt(fundsConfirmedAt);
        payout.setStatus(SellerPayout.Status.PENDING);
        return payout;
    }

    private Shop shopReference(Long shopId) {
        if (shopId == null) throw new IllegalStateException("Đơn hàng không có cửa hàng hợp lệ.");
        return shops.getReferenceById(shopId);
    }

    private void validateCommissionRate() {
        if (commissionRate == null || commissionRate.signum() < 0
                || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("app.payout.commission-rate phải nằm trong khoảng từ 0 đến 1.");
        }
    }

    private void refreshBatchStatus(SellerPayoutBatch batch, List<SellerPayoutBatchItem> items) {
        long paid = items.stream().filter(item -> item.getStatus() == SellerPayoutBatchItem.Status.PAID).count();
        long failed = items.stream().filter(item -> item.getStatus() == SellerPayoutBatchItem.Status.FAILED).count();
        if (paid == items.size()) batch.setStatus(SellerPayoutBatch.Status.PAID);
        else if (failed == items.size()) batch.setStatus(SellerPayoutBatch.Status.FAILED);
        else if (paid + failed == items.size()) batch.setStatus(SellerPayoutBatch.Status.PARTIAL);
        if (paid + failed == items.size()) batch.setCompletedAt(utcNow());
    }

    private String aggregateItemStatus(Collection<SellerPayoutBatchItem> items) {
        Set<SellerPayoutBatchItem.Status> statuses = items.stream().map(SellerPayoutBatchItem::getStatus).collect(Collectors.toSet());
        return statuses.size() == 1 ? statuses.iterator().next().name() : "PARTIAL";
    }

    private String required(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw badRequest(field + " là bắt buộc và tối đa " + maxLength + " ký tự.");
        }
        return normalized;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private record PaymentState(SellerPayout.FundsSource source, LocalDateTime confirmedAt) { }
}
