package VietFreshHub.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import VietFreshHub.common.PageSizes;

@Getter
@Setter
@NoArgsConstructor
public class OrderFilterRequest {

    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private List<String> priceRange = List.of();
    private List<String> status = List.of();
    private List<String> paymentStatus = List.of();
    private String sort;
    private int size = PageSizes.DEFAULT;
}
