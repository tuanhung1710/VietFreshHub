package VietFreshHub.shop.repository;

import VietFreshHub.shop.entity.ShopMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShopMemberRepository extends JpaRepository<ShopMember, Long> {

    @Query("""
            select m from ShopMember m
            where m.userId=:userId and m.status='ACTIVE' and m.memberRole='OWNER'
              and (select count(u) from ShopMember u where u.userId=:userId and u.status='ACTIVE')=1
              and (select count(s) from ShopMember s where s.shopId=m.shopId and s.status='ACTIVE')=1
            """)
    Optional<ShopMember> findSoleActiveOwnerByUserId(Long userId);

    List<ShopMember> findByShopIdAndUserIdIn(Long shopId, Collection<Long> userIds);
}
