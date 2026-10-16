package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyEarningRuleForm;
import VietFreshHub.loyalty.dto.LoyaltyRuleResponse;

public interface LoyaltyRuleService {

    LoyaltyRuleResponse getRules();

    LoyaltyEarningRuleForm getEarningForm();

    LoyaltyRuleResponse updateEarningRules(
            LoyaltyEarningRuleForm form
    );
}