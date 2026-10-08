package VietFreshHub.Checkout.service.impl;
import VietFreshHub.Auth.entity.Address;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Checkout.dto.CheckoutAddressRequest;
import VietFreshHub.Checkout.exception.CheckoutException;
import VietFreshHub.Checkout.repository.CheckoutAddressRepository;
import VietFreshHub.Checkout.service.CheckoutAddressService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class CheckoutAddressServiceImpl implements CheckoutAddressService {
    private final UserRepository users;
    private final CheckoutAddressRepository addresses;
    private final EntityManager em;
    private final Validator validator;
    @Override @Transactional
    public Long create(Long customerId,CheckoutAddressRequest form) {
        var errors=validator.validate(form);
        if(!errors.isEmpty()) throw new CheckoutException(errors.iterator().next().getMessage());
        var customer=users.findForCartUpdate(customerId).orElseThrow(() -> new CheckoutException("Tài khoản không hợp lệ."));
        em.refresh(customer,LockModeType.PESSIMISTIC_WRITE);
        if(!"ACTIVE".equals(customer.getStatus())) throw new CheckoutException("Tài khoản không còn được phép thêm địa chỉ.");
        Address address=new Address(); address.setUser(customer); address.setRecipientName(form.getRecipientName().trim());
        address.setPhone(form.getPhone().trim()); address.setProvince(form.getProvince().trim()); address.setDistrict(form.getDistrict().trim());
        address.setWard(form.getWard().trim()); address.setAddressLine(form.getAddressLine().trim());
        address.setIsDefault(addresses.findByUserUserIdOrderByIsDefaultDescAddressIdAsc(customerId).isEmpty());
        em.persist(address); return address.getAddressId();
    }
}
