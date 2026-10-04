package VietFreshHub.Order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCountsResponse {

    private long totalOrders;
    private long incomingOrders;
    private long confirmedOrders;
    private long preparingOrders;
    private long readyOrders;
    private long processedOrders;
}
