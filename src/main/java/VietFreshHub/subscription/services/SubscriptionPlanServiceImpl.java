package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.SubscriptionPlanForm;
import VietFreshHub.subscription.dto.SubscriptionPlanResponse;
import VietFreshHub.subscription.entity.PlanStatus;
import VietFreshHub.subscription.entity.SubscriptionPlan;
import VietFreshHub.subscription.repository.SubscriptionPlanRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionPlanServiceImpl
        implements SubscriptionPlanService {

    private static final String FREE_PLAN_NAME = "Free";

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final Validator validator;

    @Override
    public List<SubscriptionPlanResponse> getAll() {
        return subscriptionPlanRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<SubscriptionPlanResponse> getActivePlans() {
        return subscriptionPlanRepository
                .findByStatusOrderByPriceAsc(PlanStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SubscriptionPlanResponse getById(Long planId) {
        return toResponse(findPlan(planId));
    }

    @Override
    public SubscriptionPlanForm getFormById(Long planId) {
        SubscriptionPlan plan = findPlan(planId);

        SubscriptionPlanForm form = new SubscriptionPlanForm();
        form.setName(plan.getName());
        form.setDescription(plan.getDescription());
        form.setPrice(plan.getPrice());
        form.setBillingPeriod(plan.getBillingPeriod());
        form.setBenefits(plan.getBenefits());
        form.setMaxProducts(plan.getMaxProducts());
        form.setStatus(plan.getStatus());

        return form;
    }

    @Override
    public SubscriptionPlanResponse getFreePlan() {
        SubscriptionPlan plan = subscriptionPlanRepository
                .findByNameIgnoreCase(FREE_PLAN_NAME)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Chưa cấu hình gói Free"
                        )
                );

        if (plan.getStatus() != PlanStatus.ACTIVE
                || plan.getPrice() == null
                || plan.getPrice().compareTo(BigDecimal.ZERO) != 0
                || plan.getMaxProducts() == null
                || plan.getMaxProducts() <= 0) {

            throw new IllegalStateException(
                    "Gói Free phải hoạt động, có giá bằng 0 "
                            + "và giới hạn sản phẩm lớn hơn 0"
            );
        }

        return toResponse(plan);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse create(
            SubscriptionPlanForm form
    ) {
        validateForm(form);

        String name = form.getName().strip();

        if (FREE_PLAN_NAME.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException(
                    "Gói Free là gói mặc định, không được tạo thêm"
            );
        }

        if (subscriptionPlanRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Tên gói đã tồn tại");
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(name)
                .description(trimToNull(form.getDescription()))
                .price(form.getPrice())
                .billingPeriod(form.getBillingPeriod())
                .benefits(trimToNull(form.getBenefits()))
                .maxProducts(form.getMaxProducts())
                .status(form.getStatus())
                .build();

        SubscriptionPlan savedPlan =
                subscriptionPlanRepository.save(plan);

        return toResponse(savedPlan);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse update(
            Long planId,
            SubscriptionPlanForm form
    ) {
        validateForm(form);

        SubscriptionPlan plan = findPlan(planId);
        String name = form.getName().strip();

        if (isFreePlan(plan)) {
            validateFreePlanUpdate(name, form);
            name = FREE_PLAN_NAME;
        } else if (FREE_PLAN_NAME.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException(
                    "Không được đổi gói khác thành gói Free"
            );
        }

        if (subscriptionPlanRepository
                .existsByNameIgnoreCaseAndPlanIdNot(name, planId)) {
            throw new IllegalArgumentException("Tên gói đã tồn tại");
        }

        plan.setName(name);
        plan.setDescription(trimToNull(form.getDescription()));
        plan.setPrice(form.getPrice());
        plan.setBillingPeriod(form.getBillingPeriod());
        plan.setBenefits(trimToNull(form.getBenefits()));
        plan.setMaxProducts(form.getMaxProducts());
        plan.setStatus(form.getStatus());

        // Flush để @PreUpdate cập nhật updatedAt trước khi tạo Response.
        SubscriptionPlan savedPlan =
                subscriptionPlanRepository.saveAndFlush(plan);

        return toResponse(savedPlan);
    }

    @Override
    @Transactional
    public void activate(Long planId) {
        SubscriptionPlan plan = findPlan(planId);
        plan.setStatus(PlanStatus.ACTIVE);

        subscriptionPlanRepository.save(plan);
    }

    @Override
    @Transactional
    public void deactivate(Long planId) {
        SubscriptionPlan plan = findPlan(planId);

        if (isFreePlan(plan)) {
            throw new IllegalArgumentException(
                    "Không được tắt gói Free mặc định"
            );
        }

        plan.setStatus(PlanStatus.INACTIVE);

        subscriptionPlanRepository.save(plan);
    }

    private SubscriptionPlan findPlan(Long planId) {
        if (planId == null || planId <= 0) {
            throw new IllegalArgumentException("ID gói không hợp lệ");
        }

        return subscriptionPlanRepository.findById(planId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy gói dịch vụ"
                        )
                );
    }

    private SubscriptionPlanResponse toResponse(
            SubscriptionPlan plan
    ) {
        return new SubscriptionPlanResponse(
                plan.getPlanId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getBillingPeriod(),
                plan.getBenefits(),
                plan.getMaxProducts(),
                plan.getStatus(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private void validateForm(SubscriptionPlanForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu gói không hợp lệ"
            );
        }

        Set<ConstraintViolation<SubscriptionPlanForm>> violations =
                validator.validate(form);

        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .findFirst()
                    .orElse("Dữ liệu gói không hợp lệ");

            throw new IllegalArgumentException(message);
        }
    }

    private void validateFreePlanUpdate(
            String name,
            SubscriptionPlanForm form
    ) {
        if (!FREE_PLAN_NAME.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException(
                    "Không được đổi tên gói Free mặc định"
            );
        }

        if (form.getPrice().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "Giá gói Free phải bằng 0"
            );
        }

        if (form.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Gói Free phải luôn hoạt động"
            );
        }
    }

    private boolean isFreePlan(SubscriptionPlan plan) {
        return FREE_PLAN_NAME.equalsIgnoreCase(plan.getName());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }
}