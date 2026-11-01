package VietFreshHub.checkout;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.cart.dto.*;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.service.*;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.vietmap.service.VietMapService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real quote/fee calculations with mocked address, shop and VietMap boundaries. */
class CheckoutShippingQuoteServiceTest {
    final CustomerAddressService addresses=mock(CustomerAddressService.class);
    final ShopService shops=mock(ShopService.class);
    final VietMapService vietmap=mock(VietMapService.class);
    final CheckoutShippingQuoteService service=new CheckoutShippingQuoteService(addresses,shops,vietmap,new ShippingFeeService());
    final Address address=new Address();
    final ShopLocation pickup=new ShopLocation(10L);

    private CheckoutPreview preview(boolean secondShop) {
        address.setAddressId(5L); address.setLatitude(new BigDecimal("10.77")); address.setLongitude(new BigDecimal("106.70"));
        pickup.updateLocation(1,"TP.HCM",2,"Quận 1",3,"Phường 1","25 Nguyễn Huệ","25 Nguyễn Huệ, TP.HCM",BigDecimal.TEN,new BigDecimal("106"));
        List<CartItemDto> items=new ArrayList<>();
        items.add(CartItemDto.builder().cartItemId(1L).shopId(10L).shopName("Shop 10").build());
        // Multiple lines in one shop must not multiply the shop's delivery fee.
        items.add(CartItemDto.builder().cartItemId(2L).shopId(10L).shopName("Shop 10").build());
        if(secondShop) items.add(CartItemDto.builder().cartItemId(3L).shopId(20L).shopName("Shop 20").build());
        var cart=CartDto.builder().cartId(11L).userId(7L).items(items).totalAmount(new BigDecimal("235000")).build();
        when(addresses.getDeliveryAddress(7L,5L)).thenReturn(address);
        when(shops.findLocationByShopId(10L)).thenReturn(Optional.of(pickup));
        when(shops.findLocationByShopIdForConfirmation(10L)).thenReturn(Optional.of(pickup));
        when(vietmap.motorcycleRoadDistanceMeters(any(),any(),any(),any())).thenReturn(new BigDecimal("2000"));
        if(secondShop) {
            var other=new ShopLocation(20L);
            other.updateLocation(1,"TP.HCM",2,"Quận 2",3,"Phường 2","10 ABC","10 ABC",new BigDecimal("10.5"),new BigDecimal("106.5"));
            when(shops.findLocationByShopId(20L)).thenReturn(Optional.of(other));
            when(vietmap.motorcycleRoadDistanceMeters(other.getLatitude(),other.getLongitude(),address.getLatitude(),address.getLongitude()))
                    .thenReturn(new BigDecimal("3000"));
        }
        return new CheckoutPreview(cart,List.of(new CheckoutPreview.AddressOption(5L,"An","0901234567","Address",true,true)),
                null,Map.of(),null,null,true,"fingerprint",null,false);
    }
    private CheckoutDraft draft(CheckoutPreview quote) {
        return new CheckoutDraft("a".repeat(32),7L,11L,quote.fingerprint(),quote.addressId(),quote.shopQuotes(),quote.shippingFee(),List.of(1L,2L));
    }
    @Test void sumsFeesPerDistinctShopAndUsesRoadDistance() {
        var quote=service.quote(7L,preview(true),5L);
        assertTrue(quote.canConfirm()); assertEquals(Set.of(10L,20L),quote.shopQuotes().keySet());
        assertEquals(new BigDecimal("35000.00"),quote.shippingFee()); assertEquals(new BigDecimal("270000.00"),quote.grandTotal());
        assertEquals(new BigDecimal("3000"),quote.shopQuotes().get(20L).distanceMeters());
        verify(vietmap,times(2)).motorcycleRoadDistanceMeters(any(),any(),any(),any());
    }
    @Test void boundaryFifteenKilometersIsAllowedButBeyondItIsBlocked() {
        var preview=preview(false);
        when(vietmap.motorcycleRoadDistanceMeters(any(),any(),any(),any())).thenReturn(new BigDecimal("15000"));
        assertTrue(service.quote(7L,preview,5L).canConfirm());
        when(vietmap.motorcycleRoadDistanceMeters(any(),any(),any(),any())).thenReturn(new BigDecimal("15000.1"));
        var rejected=service.quote(7L,preview,5L);
        assertTrue(rejected.deliveryOutOfRange()); assertFalse(rejected.canConfirm());
        assertNull(rejected.shippingFee()); assertTrue(rejected.shopQuotes().isEmpty());
        assertTrue(rejected.shippingError().contains("15 km"));
    }
    @Test void providerFailureProducesUnconfirmablePreviewWithoutInventingFee() {
        var preview=preview(false);
        when(vietmap.motorcycleRoadDistanceMeters(any(),any(),any(),any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        var quote=service.quote(7L,preview,5L);
        assertFalse(quote.canConfirm()); assertNull(quote.grandTotal()); assertNotNull(quote.shippingError());
        assertFalse(quote.deliveryOutOfRange());
    }
    @Test void cartOwnershipIsCheckedBeforeCallingProvider() {
        var preview=preview(false); var quote=service.quote(8L,preview,5L);
        assertFalse(quote.canConfirm()); assertNotNull(quote.shippingError());
        verifyNoInteractions(vietmap); verify(addresses,never()).getDeliveryAddress(anyLong(),anyLong());
    }
    @Test void confirmationRechecksPickupAndDestinationCoordinates() {
        var quote=service.quote(7L,preview(false),5L); var draft=draft(quote);
        assertEquals(quote.shippingFee(),service.validateForConfirmation(draft,address,Set.of(10L)));
        address.setLatitude(new BigDecimal("10.78"));
        assertThrows(CheckoutException.class,() -> service.validateForConfirmation(draft,address,Set.of(10L)));
        address.setLatitude(new BigDecimal("10.77"));
        pickup.updateLocation(1,"TP.HCM",2,"Quận 1",3,"Phường 1","25 Nguyễn Huệ","25 Nguyễn Huệ",new BigDecimal("10.1"),new BigDecimal("106"));
        assertThrows(CheckoutException.class,() -> service.validateForConfirmation(draft,address,Set.of(10L)));
    }
    @Test void confirmationRejectsChangedShopSetOrTamperedTotal() {
        var quote=service.quote(7L,preview(false),5L); var draft=draft(quote);
        assertThrows(CheckoutException.class,() -> service.validateForConfirmation(draft,address,Set.of(10L,20L)));
        var changed=new CheckoutDraft(draft.token(),7L,11L,draft.fingerprint(),5L,draft.shopQuotes(),BigDecimal.ONE,draft.selectedItemIds());
        assertThrows(CheckoutException.class,() -> service.validateForConfirmation(changed,address,Set.of(10L)));
    }
    @Test void confirmationRecomputesFeeInsteadOfTrustingStoredQuoteAmount() {
        var quote=service.quote(7L,preview(false),5L); var original=quote.shopQuotes().get(10L);
        var changed=new ShopShippingQuote(10L,original.pickupLatitude(),original.pickupLongitude(),original.destinationLatitude(),original.destinationLongitude(),original.distanceMeters(),BigDecimal.ONE);
        var draft=new CheckoutDraft("a".repeat(32),7L,11L,"fingerprint",5L,Map.of(10L,changed),BigDecimal.ONE,List.of(1L));
        assertThrows(CheckoutException.class,() -> service.validateForConfirmation(draft,address,Set.of(10L)));
    }
    @Test void distanceSnapshotRejectsNegativeAndUnstorableValues() {
        assertEquals(new BigDecimal("1234.6"),service.distanceSnapshot(new BigDecimal("1234.56")));
        assertThrows(CheckoutException.class,() -> service.distanceSnapshot(new BigDecimal("-1")));
        assertThrows(CheckoutException.class,() -> service.distanceSnapshot(new BigDecimal("1000000000000")));
    }
}
