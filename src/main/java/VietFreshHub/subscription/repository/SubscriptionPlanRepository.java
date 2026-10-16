package VietFreshHub.subscription.repository;

import VietFreshHub.subscription.entity.PlanStatus;
import VietFreshHub.subscription.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findAllByOrderByCreatedAtDesc();

    List<SubscriptionPlan> findByStatusOrderByPriceAsc(PlanStatus status);

    Optional<SubscriptionPlan> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndPlanIdNot(String name, Long planId);
}