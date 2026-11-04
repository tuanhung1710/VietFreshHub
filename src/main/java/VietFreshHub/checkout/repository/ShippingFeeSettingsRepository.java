package VietFreshHub.checkout.repository;

import VietFreshHub.checkout.entity.ShippingFeeSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingFeeSettingsRepository extends JpaRepository<ShippingFeeSettings, Long> {
}
