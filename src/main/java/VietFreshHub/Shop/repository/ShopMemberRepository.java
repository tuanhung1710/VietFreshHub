package VietFreshHub.Shop.repository;

import VietFreshHub.Shop.entity.ShopMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ShopMemberRepository extends JpaRepository<ShopMember, Long> {

    List<ShopMember> findByUserIdAndStatusAndMemberRoleIn(
            Long userId, String status, Collection<String> memberRoles);
}
