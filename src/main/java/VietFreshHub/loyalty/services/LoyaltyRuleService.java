package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyEarningRuleForm;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionRuleForm;
import VietFreshHub.loyalty.dto.LoyaltyRuleResponse;

public interface LoyaltyRuleService {

    LoyaltyRuleResponse getRules();

    LoyaltyEarningRuleForm getEarningForm();

    LoyaltyRedemptionRuleForm getRedemptionForm();

    LoyaltyRuleResponse updateEarningRules(
            LoyaltyEarningRuleForm form
    );

    LoyaltyRuleResponse updateRedemptionRules(
            LoyaltyRedemptionRuleForm form
    );
}