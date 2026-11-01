package VietFreshHub.payout.dto;

public record ShopPayoutAccountView(
        Long shopId,
        String shopName,
        String bankName,
        String accountNumber,
        String accountHolder,
        boolean verified
) {}
