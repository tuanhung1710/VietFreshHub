package VietFreshHub.loyalty.services;

import VietFreshHub.loyalty.dto.LoyaltyEarningRuleForm;
import VietFreshHub.loyalty.dto.LoyaltyRedemptionRuleForm;
import VietFreshHub.loyalty.dto.LoyaltyRuleResponse;
import VietFreshHub.loyalty.entity.LoyaltyRule;
import VietFreshHub.loyalty.repository.LoyaltyRuleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyRuleServiceImpl implements LoyaltyRuleService {

    private static final Long RULE_ID = 1L;

    private final LoyaltyRuleRepository loyaltyRuleRepository;
    private final Validator validator;

    @Override
    public LoyaltyRuleResponse getRules() {
        return toResponse(findRule());
    }

    @Override
    public LoyaltyEarningRuleForm getEarningForm() {
        LoyaltyRule rule = findRule();

        LoyaltyEarningRuleForm form = new LoyaltyEarningRuleForm();
        form.setEarningPercent(rule.getEarningPercent());
        form.setEarningEnabled(rule.isEarningEnabled());

        return form;
    }

    @Override
    public LoyaltyRedemptionRuleForm getRedemptionForm() {
        LoyaltyRule rule = findRule();

        LoyaltyRedemptionRuleForm form =
                new LoyaltyRedemptionRuleForm();

        form.setMinRedemptionAmount(rule.getMinRedemptionAmount().setScale(0, java.math.RoundingMode.UNNECESSARY));
        form.setMaxRedemptionPercent(rule.getMaxRedemptionPercent());
        form.setRedemptionEnabled(rule.isRedemptionEnabled());

        return form;
    }

    @Override
    @Transactional
    public LoyaltyRuleResponse updateEarningRules(
            LoyaltyEarningRuleForm form
    ) {
        validateForm(form);

        LoyaltyRule rule = findRuleForUpdate();

        rule.setEarningPercent(form.getEarningPercent());
        rule.setEarningEnabled(form.isEarningEnabled());

        LoyaltyRule savedRule =
                loyaltyRuleRepository.saveAndFlush(rule);

        return toResponse(savedRule);
    }

    @Override
    @Transactional
    public LoyaltyRuleResponse updateRedemptionRules(
            LoyaltyRedemptionRuleForm form
    ) {
        validateForm(form);

        LoyaltyRule rule = findRuleForUpdate();

        rule.setMinRedemptionAmount(form.getMinRedemptionAmount());
        rule.setMaxRedemptionPercent(form.getMaxRedemptionPercent());
        rule.setRedemptionEnabled(form.isRedemptionEnabled());

        LoyaltyRule savedRule =
                loyaltyRuleRepository.saveAndFlush(rule);

        return toResponse(savedRule);
    }

    private LoyaltyRule findRule() {
        return loyaltyRuleRepository.findById(RULE_ID)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Chưa cấu hình Loyalty. "
                                        + "Cần khởi tạo bản ghi loyalty_rules có ID = 1."
                        )
                );
    }

    private LoyaltyRule findRuleForUpdate() {
        return loyaltyRuleRepository.findForUpdate(RULE_ID)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Chưa cấu hình Loyalty. "
                                        + "Cần khởi tạo bản ghi loyalty_rules có ID = 1."
                        )
                );
    }

    private LoyaltyRuleResponse toResponse(LoyaltyRule rule) {
        return new LoyaltyRuleResponse(
                rule.getRuleId(),
                rule.getEarningPercent(),
                rule.isEarningEnabled(),
                rule.getMinRedemptionAmount(),
                rule.getMaxRedemptionPercent(),
                rule.isRedemptionEnabled(),
                rule.getUpdatedAt()
        );
    }

    private <T> void validateForm(T form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu cấu hình Loyalty không hợp lệ"
            );
        }

        Set<ConstraintViolation<T>> violations =
                validator.validate(form);

        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .findFirst()
                    .orElse("Dữ liệu cấu hình Loyalty không hợp lệ");

            throw new IllegalArgumentException(message);
        }
    }
}
