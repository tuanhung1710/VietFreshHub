package VietFreshHub.checkout.service;
import VietFreshHub.checkout.dto.*;
public interface CheckoutService {
    CheckoutPreview preview(Long customerId);
    CheckoutPreview preview(Long customerId,java.util.List<Long> selectedItemIds);
    CheckoutResult confirm(Long customerId,CheckoutDraft draft,CheckoutRequest request);
}
