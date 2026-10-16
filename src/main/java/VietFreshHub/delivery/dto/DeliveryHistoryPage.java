package VietFreshHub.delivery.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record DeliveryHistoryPage(Page<DeliveryHistoryRow> records, List<Option> shops,
                                  List<Option> paymentMethods, long allTimeCount, long unresolvedCount) {
    public record Option(String value, String label) {
    }
}
