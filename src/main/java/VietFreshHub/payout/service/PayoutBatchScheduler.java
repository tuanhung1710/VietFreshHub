package VietFreshHub.payout.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutBatchScheduler {

    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private final SellerPayoutService sellerPayoutService;

    @Value("${app.payout.time-zone:Asia/Ho_Chi_Minh}")
    private String timeZone;

    @Scheduled(cron = "${app.payout.morning-cron:0 0 10 * * *}", zone = "${app.payout.time-zone:Asia/Ho_Chi_Minh}")
    public void createMorningBatch() {
        createBatch(LocalTime.of(10, 0));
    }

    @Scheduled(cron = "${app.payout.afternoon-cron:0 0 16 * * *}", zone = "${app.payout.time-zone:Asia/Ho_Chi_Minh}")
    public void createAfternoonBatch() {
        createBatch(LocalTime.of(16, 0));
    }

    private void createBatch(LocalTime slotTime) {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDateTime localSlot = LocalDate.now(zone).atTime(slotTime);
        LocalDateTime utcSlot = localSlot.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        String slotKey = localSlot.format(SLOT_FORMAT);
        try {
            sellerPayoutService.createScheduledBatch(utcSlot, slotKey);
        } catch (DataIntegrityViolationException exception) {
            log.info("A payout batch already exists for scheduled slot {}", slotKey);
        }
    }
}
