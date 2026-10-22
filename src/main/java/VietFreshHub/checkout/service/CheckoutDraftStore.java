package VietFreshHub.checkout.service;

import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-side review data; prices, cart IDs and shipping quotes are never trusted from a form. */
@Component
public class CheckoutDraftStore {
    private static final String ATTRIBUTE=CheckoutDraftStore.class.getName()+".drafts";
    private static final int MAX_DRAFTS=8;
    public CheckoutDraft issue(HttpSession session,Long customerId,CheckoutPreview preview) {
        Map<String,CheckoutDraft> drafts=store(session);
        synchronized(drafts) {
            for(CheckoutDraft existing:drafts.values()) {
                if(customerId.equals(existing.customerId()) && Objects.equals(preview.cart().getCartId(),existing.cartId())
                        && preview.fingerprint().equals(existing.fingerprint())
                        && Objects.equals(preview.addressId(),existing.addressId())
                        && preview.shopQuotes().equals(existing.shopQuotes())
                        && Objects.equals(preview.shippingFee(),existing.shippingFee())
                        && preview.cart().getItems().stream().map(VietFreshHub.cart.dto.CartItemDto::getCartItemId).sorted().toList()
                                .equals(existing.selectedItemIds())) return existing;
            }
            String token=UUID.randomUUID().toString().replace("-","");
            CheckoutDraft draft=new CheckoutDraft(token,customerId,preview.cart().getCartId(),preview.fingerprint(),preview.addressId(),
                    preview.shopQuotes(),preview.shippingFee(),
                    preview.cart().getItems().stream().map(VietFreshHub.cart.dto.CartItemDto::getCartItemId).sorted().toList());
            drafts.put(token,draft);
            while(drafts.size()>MAX_DRAFTS) drafts.remove(drafts.keySet().iterator().next());
            return draft;
        }
    }
    public CheckoutDraft find(HttpSession session,String token,Long customerId) {
        if(token==null) return null;
        Map<String,CheckoutDraft> drafts=store(session);
        synchronized(drafts) {
            CheckoutDraft draft=drafts.get(token);
            return draft!=null && customerId.equals(draft.customerId()) ? draft : null;
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String,CheckoutDraft> store(HttpSession session) {
        synchronized(session) {
            Object existing=session.getAttribute(ATTRIBUTE);
            if(existing instanceof Map<?,?>) return (Map<String,CheckoutDraft>)existing;
            Map<String,CheckoutDraft> created=new LinkedHashMap<>(); session.setAttribute(ATTRIBUTE,created); return created;
        }
    }
}
