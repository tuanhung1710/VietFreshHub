package VietFreshHub.Auth.dto;

import VietFreshHub.Auth.entity.SellerApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerApplicationAdminRow {
    private Long applicationId;
    private String businessName;
    private String taxCode;
    private String applicantName;
    private String applicantEmail;
    private String applicantPhone;
    private SellerApplicationStatus status;
    private String statusLabel;
    private String submittedAtLabel;
}
