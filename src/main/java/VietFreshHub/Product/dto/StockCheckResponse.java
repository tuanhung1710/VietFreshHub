package VietFreshHub.Product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckResponse {

    private Long variantId;
    private Integer availableStock;
    private String status;
    private boolean purchasable;
}
