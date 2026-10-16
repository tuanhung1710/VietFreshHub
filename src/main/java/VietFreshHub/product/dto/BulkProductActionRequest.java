package VietFreshHub.product.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class BulkProductActionRequest extends ActionRequest {
    @NotEmpty
    @Size(max = 50)
    private List<@NotNull @Positive Long> productIds;
}
