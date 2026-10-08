package VietFreshHub.Shop.repository;

import VietFreshHub.Shop.entity.ShopLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopLocationRepository extends JpaRepository<ShopLocation, Long> {
}
