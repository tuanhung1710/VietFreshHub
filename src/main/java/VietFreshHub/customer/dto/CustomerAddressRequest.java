package VietFreshHub.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CustomerAddressRequest {

    private Long addressId;

    @NotBlank(message = "Vui lòng nhập tên người nhận.")
    @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự.")
    private String recipientName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự.")
    private String phone;

    @NotBlank(message = "Vui lòng nhập tỉnh/thành phố.")
    @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự.")
    private String province;

    @NotBlank(message = "Vui lòng nhập quận/huyện.")
    @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự.")
    private String district;

    @NotBlank(message = "Vui lòng nhập phường/xã.")
    @Size(max = 100, message = "Phường/xã tối đa 100 ký tự.")
    private String ward;

    @NotBlank(message = "Vui lòng nhập địa chỉ cụ thể.")
    @Size(max = 500, message = "Địa chỉ cụ thể tối đa 500 ký tự.")
    private String addressLine;

    private boolean isDefault;

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
