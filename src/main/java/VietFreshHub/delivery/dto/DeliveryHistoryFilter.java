package VietFreshHub.delivery.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import VietFreshHub.common.PageSizes;

@Getter
@Setter
public class DeliveryHistoryFilter {
    private String keyword;
    private String status = "ALL";
    private String period = "ALL";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    private Long shopId;
    private String paymentMethod;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String returnStatus = "ALL";
    private String sort = "NEWEST";
    private int size = PageSizes.DEFAULT;
}
