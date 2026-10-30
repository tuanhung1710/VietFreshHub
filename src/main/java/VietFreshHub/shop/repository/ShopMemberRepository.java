package VietFreshHub.shop.repository;

import org.springframework.data.repository.query.Param;

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

@Query("""
            select sm from ShopMember sm
            join fetch sm.shop s
            where sm.user.userId = :userId
              and sm.status = 'ACTIVE'
              and s.status = 'ACTIVE'
            order by s.shopName
            """)
    List<ShopMember> findActiveMembershipsByUserId(@Param("userId") Long userId);

    @Query("""
            select distinct u.userId from ShopMember sm
            join sm.shop s
            join sm.user u
            join u.userRoles ur
            where s.shopId = :shopId and s.status = 'ACTIVE'
              and sm.status = 'ACTIVE' and sm.memberRole = 'OWNER'
              and u.status = 'ACTIVE' and ur.role.roleName = 'ROLE_STORE_MANAGER'
              and (select count(m) from ShopMember m
                   where m.userId = u.userId and m.status = 'ACTIVE') = 1
              and (select count(m) from ShopMember m
                   where m.shopId = s.shopId and m.status = 'ACTIVE') = 1
            order by u.userId
            """)
    List<Long> findActiveManagerUserIdsByShopId(@Param("shopId") Long shopId);

    @Query("""
            select count(sm) > 0 from ShopMember sm
            join sm.shop s
            where sm.shop.shopId = :shopId
              and sm.user.userId = :userId
              and sm.status = 'ACTIVE'
              and s.status = 'ACTIVE'
            """)
    boolean existsActiveMembership(
            @Param("shopId") Long shopId,
            @Param("userId") Long userId
    );
}
