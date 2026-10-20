package VietFreshHub.checkout;

import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.service.CheckoutDraftStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutDraftStoreTest {
    final CheckoutDraftStore store=new CheckoutDraftStore();
    final MockHttpSession session=new MockHttpSession();

    private CheckoutPreview preview(String fingerprint, long address, String fee) {
        var cart=CartDto.builder().cartId(11L).userId(7L).items(List.of(
                CartItemDto.builder().cartItemId(2L).build(),CartItemDto.builder().cartItemId(1L).build())).build();
        var quote=new ShopShippingQuote(10L,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ONE,
                new BigDecimal("2000"),new BigDecimal(fee));
        return new CheckoutPreview(cart,List.of(),address,Map.of(10L,quote),new BigDecimal(fee),null,true,fingerprint,null,false);
    }
    @Test void sameReviewReusesTokenAndSortsSelectedIds() {
        var first=store.issue(session,7L,preview("a",5,"15000"));
        assertSame(first,store.issue(session,7L,preview("a",5,"15000")));
        assertTrue(first.token().matches("[0-9a-f]{32}"));
        assertEquals(List.of(1L,2L),first.selectedItemIds());
    }
    @Test void tokenIsScopedToCustomerAndSession() {
        var draft=store.issue(session,7L,preview("a",5,"15000"));
        assertNull(store.find(session,draft.token(),8L));
        assertNull(store.find(new MockHttpSession(),draft.token(),7L));
        assertNull(store.find(session,null,7L));
        assertSame(draft,store.find(session,draft.token(),7L));
    }
    @Test void changedCartReviewAddressOrQuoteGetsSeparateDraftWithoutInvalidatingOtherTab() {
        var first=store.issue(session,7L,preview("a",5,"15000"));
        for(var next:List.of(preview("b",5,"15000"),preview("a",6,"15000"),preview("a",5,"20000"))) {
            var draft=store.issue(session,7L,next);
            assertNotEquals(first.token(),draft.token());
            assertSame(first,store.find(session,first.token(),7L));
        }
    }
    @Test void ninthDistinctReviewEvictsOnlyOldestDraft() {
        List<CheckoutDraft> drafts=new ArrayList<>();
        for(int i=0;i<9;i++) drafts.add(store.issue(session,7L,preview("review-"+i,5,"15000")));
        assertNull(store.find(session,drafts.getFirst().token(),7L));
        for(int i=1;i<9;i++) assertSame(drafts.get(i),store.find(session,drafts.get(i).token(),7L));
    }
    @Test void draftDefensivelyCopiesSelectionAndQuotes() {
        var selection=new ArrayList<>(List.of(1L));
        var quotes=new HashMap<>(preview("a",5,"15000").shopQuotes());
        var draft=new CheckoutDraft("a",7L,11L,"f",5L,quotes,new BigDecimal("15000"),selection);
        selection.clear(); quotes.clear();
        assertEquals(List.of(1L),draft.selectedItemIds()); assertEquals(Set.of(10L),draft.shopQuotes().keySet());
        assertThrows(UnsupportedOperationException.class,() -> draft.selectedItemIds().clear());
        assertThrows(UnsupportedOperationException.class,() -> draft.shopQuotes().clear());
    }
}
