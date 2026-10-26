package VietFreshHub.checkout.service;

import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lưu báo giá phía server để không phải tin giá và mã giỏ do form gửi lên. */
@Component
public class CheckoutDraftStore {
    private static final String ATTRIBUTE = CheckoutDraftStore.class.getName() + ".drafts";
    private static final int MAX_DRAFTS = 8;

    public CheckoutDraft issue(HttpSession session, Long customerId, CheckoutPreview preview) {
        Map<String, CheckoutDraft> drafts = store(session);
        List<Long> selectedItemIds = selectedItemIds(preview);

        synchronized (drafts) {
            // Cùng giỏ, địa chỉ và báo giá thì dùng lại token để tránh đặt trùng đơn.
            for (CheckoutDraft existing : drafts.values()) {
                if (matchesPreview(existing, customerId, preview, selectedItemIds)) {
                    return existing;
                }
            }

            String token = UUID.randomUUID().toString().replace("-", "");
            CheckoutDraft draft = new CheckoutDraft(
                    token, customerId, preview.cart().getCartId(), preview.fingerprint(),
                    preview.addressId(), preview.shopQuotes(), preview.shippingFee(), selectedItemIds);
            drafts.put(token, draft);

            while (drafts.size() > MAX_DRAFTS) {
                String oldestToken = drafts.keySet().iterator().next();
                drafts.remove(oldestToken);
            }
            return draft;
        }
    }

    public CheckoutDraft find(HttpSession session, String token, Long customerId) {
        if (token == null) {
            return null;
        }
        Map<String, CheckoutDraft> drafts = store(session);
        synchronized (drafts) {
            CheckoutDraft draft = drafts.get(token);
            if (draft == null || !customerId.equals(draft.customerId())) {
                return null;
            }
            return draft;
        }
    }

    private List<Long> selectedItemIds(CheckoutPreview preview) {
        List<Long> itemIds = new ArrayList<>();
        for (CartItemDto item : preview.cart().getItems()) {
            itemIds.add(item.getCartItemId());
        }
        Collections.sort(itemIds);
        return List.copyOf(itemIds);
    }

    private boolean matchesPreview(CheckoutDraft draft, Long customerId,
                                   CheckoutPreview preview, List<Long> selectedItemIds) {
        return customerId.equals(draft.customerId())
                && Objects.equals(preview.cart().getCartId(), draft.cartId())
                && preview.fingerprint().equals(draft.fingerprint())
                && Objects.equals(preview.addressId(), draft.addressId())
                && preview.shopQuotes().equals(draft.shopQuotes())
                && Objects.equals(preview.shippingFee(), draft.shippingFee())
                && selectedItemIds.equals(draft.selectedItemIds());
    }

    @SuppressWarnings("unchecked")
    private Map<String, CheckoutDraft> store(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(ATTRIBUTE);
            if (existing instanceof Map<?, ?>) {
                return (Map<String, CheckoutDraft>) existing;
            }
            Map<String, CheckoutDraft> created = new LinkedHashMap<>();
            session.setAttribute(ATTRIBUTE, created);
            return created;
        }
    }
}
