package VietFreshHub.checkout.service;

import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.CheckoutRequest;
import VietFreshHub.checkout.dto.CheckoutResult;

import java.util.List;

public interface CheckoutService {
    CheckoutPreview preview(Long customerId);

    CheckoutPreview preview(Long customerId, List<Long> selectedItemIds);

    CheckoutPreview previewBuyNow(Long customerId, Long variantId, Integer quantity);

    CheckoutResult confirm(Long customerId, CheckoutDraft draft, CheckoutRequest request);
}
