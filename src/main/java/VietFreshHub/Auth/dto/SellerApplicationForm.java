package VietFreshHub.Auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SellerApplicationForm {

    @NotBlank(message = "Vui lòng nhập tên cơ sở kinh doanh")
    @Size(max = 200, message = "Tên cơ sở không được dài quá 200 ký tự")
    private String businessName;

    @Size(max = 50, message = "Mã số thuế không được dài quá 50 ký tự")
    private String taxCode;

    private MultipartFile identityCard;
    private MultipartFile businessLicense;

    @AssertTrue(message = "Bạn cần đồng ý với cam kết pháp lý")
    private boolean legalAgree;

    @AssertTrue(message = "Bạn cần đồng ý với chính sách hoạt động")
    private boolean policyAgree;
}