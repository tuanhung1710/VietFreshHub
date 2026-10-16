package VietFreshHub.product.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class CatalogSummary {

    private long productCount;
    private long variantCount;
}
