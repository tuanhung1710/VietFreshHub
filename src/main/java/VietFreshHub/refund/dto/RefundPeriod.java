package VietFreshHub.refund.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RefundPeriod(LocalDateTime start, LocalDateTime end, LocalDateTime currentTime,
                           List<Row> requests, BigDecimal totalAmount) {

    public RefundPeriod {
        requests = List.copyOf(requests);
    }

    public boolean isCompleted() {
        return !end.isAfter(currentTime);
    }

    public LocalDateTime getLatestSelectableTime() {
        return currentTime.toLocalDate().atTime(23, 59, 59);
    }

    public record Row(Long refundRequestId, String orderCode, String bankName, String bankAccountNumber,
                      String accountHolderName, BigDecimal refundAmount, LocalDateTime submittedAt) {

        public String getMaskedAccountNumber() {
            if (bankAccountNumber == null || bankAccountNumber.length() <= 4) {
                return "****";
            }
            return "****" + bankAccountNumber.substring(bankAccountNumber.length() - 4);
        }

        public String getTransferDescription() {
            return "Hoan tien RR" + refundRequestId;
        }
    }
}
