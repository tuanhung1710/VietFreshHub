package VietFreshHub.checkout.service;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.checkout.dto.CheckoutDraft;
import VietFreshHub.checkout.dto.CheckoutPreview;
import VietFreshHub.checkout.dto.ShopShippingQuote;
import VietFreshHub.checkout.exception.DeliveryRangeException;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import VietFreshHub.vietmap.service.VietMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class CheckoutShippingQuoteService {
    private static final BigDecimal MAX_DELIVERY_DISTANCE_METERS = new BigDecimal("15000");

    private final CustomerAddressService customerAddresses;
    private final ShopService shops;
    private final VietMapService vietMap;
    private final ShippingFeeService shippingFees;

    @Transactional(propagation = Propagation.NEVER)
    public CheckoutPreview quote(Long customerId, CheckoutPreview preview, Long addressId) {
        Long selectedAddressId = addressId;
        if (selectedAddressId == null && !preview.addresses().isEmpty()) {
            selectedAddressId = preview.addresses().getFirst().addressId();
        }
        try {
            if (!Objects.equals(customerId, preview.cart().getUserId())) {
                throw new CheckoutException("Giỏ hàng không thuộc tài khoản của bạn.");
            }
            if (preview.cart().getItems().isEmpty() || preview.cart().isHasUnavailableItems()) {
                throw new CheckoutException("Vui lòng kiểm tra lại sản phẩm trong giỏ trước khi tính phí giao hàng.");
            }
            if (selectedAddressId == null) {
                throw new CheckoutException("Vui lòng thêm hoặc chọn địa chỉ nhận hàng để tính phí giao hàng.");
            }
            Address address;
            try {
                address = customerAddresses.getDeliveryAddress(customerId, selectedAddressId);
            } catch (IllegalArgumentException exception) {
                throw new CheckoutException(exception.getMessage());
            }
            BigDecimal destinationLatitude = address.getLatitude();
            BigDecimal destinationLongitude = address.getLongitude();
            Map<Long, String> shopNames = new TreeMap<>();
            for (CartItemDto item : preview.cart().getItems()) {
                shopNames.put(item.getShopId(), item.getShopName());
            }
            Map<Long, ShopLocation> locations = new TreeMap<>();
            for (Long shopId : shopNames.keySet()) {
                ShopLocation location = shops.findLocationByShopId(shopId)
                        .orElseThrow(() -> new CheckoutException("Cửa hàng " + shopNames.get(shopId)
                                + " chưa có địa chỉ lấy hàng hợp lệ. Vui lòng chọn lại sản phẩm hoặc thử lại sau."));
                validatePickupLocation(shopId, location, shopNames.get(shopId));
                locations.put(shopId, location);
            }
            Map<Long, ShopShippingQuote> quotes = new TreeMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Long shopId : locations.keySet()) {
                ShopLocation location = locations.get(shopId);
                BigDecimal distanceMeters;
                try {
                    distanceMeters = vietMap.motorcycleRoadDistanceMeters(location.getLatitude(), location.getLongitude(),
                            destinationLatitude, destinationLongitude);
                } catch (ResponseStatusException exception) {
                    throw new CheckoutException("Chưa thể tính phí vận chuyển cho cửa hàng " + shopNames.get(shopId)
                            + ". Vui lòng thử lại sau.");
                }
                validateDeliveryDistance(distanceMeters, shopNames.get(shopId));
                BigDecimal fee = shippingFees.calculateShippingFee(distanceMeters);
                quotes.put(shopId, new ShopShippingQuote(shopId, location.getLatitude(), location.getLongitude(),
                        destinationLatitude, destinationLongitude, distanceMeters, fee));
                total = total.add(fee);
            }
            return new CheckoutPreview(preview.cart(), preview.addresses(), selectedAddressId, quotes, money(total),
                    money(preview.cart().getTotalAmount().add(total)), preview.codAvailable(), preview.fingerprint(), null, false);
        } catch (CheckoutException exception) {
            return new CheckoutPreview(preview.cart(), preview.addresses(), selectedAddressId, Map.of(), null, null,
                    preview.codAvailable(), preview.fingerprint(), exception.getMessage(), exception instanceof DeliveryRangeException);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal validateForConfirmation(CheckoutDraft draft, Address address, Set<Long> shopIds) {
        if (!Objects.equals(draft.addressId(), address.getAddressId()) || draft.shippingFee() == null
                || shopIds.isEmpty() || !draft.shopQuotes().keySet().equals(shopIds)) {
            throw staleQuote();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Long shopId : new TreeSet<>(shopIds)) {
            ShopShippingQuote quote = draft.shopQuotes().get(shopId);
            if (quote == null || !shopId.equals(quote.shopId())
                    || !sameCoordinate(quote.destinationLatitude(), address.getLatitude())
                    || !sameCoordinate(quote.destinationLongitude(), address.getLongitude())) {
                throw staleQuote();
            }
            ShopLocation location = shops.findLocationByShopIdForConfirmation(shopId).orElseThrow(this::staleQuote);
            validatePickupLocation(shopId, location, String.valueOf(shopId));
            if (!sameCoordinate(quote.pickupLatitude(), location.getLatitude())
                    || !sameCoordinate(quote.pickupLongitude(), location.getLongitude())) {
                throw staleQuote();
            }
            validateDeliveryDistance(quote.distanceMeters(), null);
            if (quote.shippingFee() == null
                    || shippingFees.calculateShippingFee(quote.distanceMeters()).compareTo(quote.shippingFee()) != 0) {
                throw staleQuote();
            }
            total = total.add(money(quote.shippingFee()));
        }
        if (total.compareTo(draft.shippingFee()) != 0) {
            throw staleQuote();
        }
        return money(total);
    }

    private void validateDeliveryDistance(BigDecimal distanceMeters, String shopName) {
        if (distanceMeters == null || distanceMeters.signum() < 0) {
            throw new CheckoutException("Khoảng cách giao hàng không hợp lệ. Vui lòng tính lại phí giao hàng.");
        }
        if (distanceMeters.compareTo(MAX_DELIVERY_DISTANCE_METERS) > 0) {
            String shopLabel = shopName == null ? "" : "Cửa hàng " + shopName + ": ";
            throw new DeliveryRangeException(shopLabel
                    + "Địa chỉ nhận hàng nằm ngoài phạm vi giao hàng tối đa 15 km của cửa hàng. "
                    + "Vui lòng thay đổi địa chỉ nhận hàng hoặc chọn sản phẩm từ cửa hàng gần hơn.");
        }
    }

    public BigDecimal distanceSnapshot(BigDecimal distanceMeters) {
        if (distanceMeters == null || distanceMeters.signum() < 0) {
            throw new CheckoutException("Khoảng cách giao hàng không hợp lệ. Vui lòng tính lại phí giao hàng.");
        }
        BigDecimal snapshot = distanceMeters.setScale(1, RoundingMode.HALF_UP);
        if (snapshot.precision() > 12) {
            throw new CheckoutException("Khoảng cách giao hàng vượt giới hạn lưu trữ DECIMAL(12,1). Hiện chưa thể đặt đơn.");
        }
        return snapshot;
    }

    private void validatePickupLocation(Long shopId, ShopLocation location, String shopName) {
        if (!shopId.equals(location.getShopId())) {
            throw staleQuote();
        }
        try {
            shops.validatePickupLocation(new VietMapPlaceResponse(location.getProvinceId(), location.getProvince(),
                    location.getDistrictId(), location.getDistrict(), location.getWardId(), location.getWard(),
                    location.getAddressLine(), location.getFormattedAddress(), location.getLatitude(), location.getLongitude()));
        } catch (IllegalArgumentException exception) {
            throw new CheckoutException("Cửa hàng " + shopName + " chưa có địa chỉ lấy hàng hợp lệ. Vui lòng thử lại sau.");
        }
    }

    private boolean sameCoordinate(BigDecimal quoted, BigDecimal current) {
        return quoted != null && current != null && quoted.compareTo(current) == 0;
    }

    private BigDecimal money(BigDecimal amount) {
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() < 0 || normalized.precision() > 18) {
            throw new CheckoutException("Giá trị đơn vượt giới hạn lưu trữ DECIMAL(18,2).");
        }
        return normalized;
    }

    private CheckoutException staleQuote() {
        return new CheckoutException("Phí giao hàng hoặc thông tin giao hàng đã thay đổi. Vui lòng xem lại đơn và tính lại phí giao hàng trước khi xác nhận.");
    }
}
