package VietFreshHub.payout.dto;

public record ShopPayoutAccountForm(
        Long shopId,
        String shopName,
        String bankCode,
        String bankName,
        String accountNumber,
        String accountHolder,
        boolean verified,
        boolean hasAccount
) { }
