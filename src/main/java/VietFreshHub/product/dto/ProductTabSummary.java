package VietFreshHub.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Shop-wide counts, independent of the current search and page. */
@Getter
@AllArgsConstructor
public class ProductTabSummary {
    private long all;
    private long active;
    private long outOfStock;
    private long violation;
    private long hidden;
    private long unavailable;
}
