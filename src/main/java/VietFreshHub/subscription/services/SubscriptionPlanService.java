package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.SubscriptionPlanForm;
import VietFreshHub.subscription.dto.SubscriptionPlanResponse;

import java.util.List;

public interface SubscriptionPlanService {

    List<SubscriptionPlanResponse> getAll();

    List<SubscriptionPlanResponse> getActivePlans();

    SubscriptionPlanResponse getById(Long planId);

    SubscriptionPlanForm getFormById(Long planId);

    SubscriptionPlanResponse getFreePlan();

    SubscriptionPlanResponse create(SubscriptionPlanForm form);

    SubscriptionPlanResponse update(
            Long planId,
            SubscriptionPlanForm form
    );

    void activate(Long planId);

    void deactivate(Long planId);
}