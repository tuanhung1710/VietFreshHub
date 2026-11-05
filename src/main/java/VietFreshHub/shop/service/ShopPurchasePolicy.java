package VietFreshHub.shop.service;

import VietFreshHub.shop.repository.ShopMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopPurchasePolicy {
    public static final String OWN_SHOP_MESSAGE = "Bạn không thể mua sản phẩm tại cửa hàng của chính mình.";

    private final ShopMemberRepository members;

    public Set<Long> ownedShopIds(Long customerId) {
        return members.findAllByUserIdAndStatus(customerId, "ACTIVE").stream()
                .filter(member -> "OWNER".equals(member.getMemberRole()))
                .map(member -> member.getShopId())
                .collect(Collectors.toSet());
    }

    public boolean ownsShop(Long customerId, Long shopId) {
        return ownedShopIds(customerId).contains(shopId);
    }
}
