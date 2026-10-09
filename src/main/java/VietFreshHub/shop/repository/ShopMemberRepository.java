package VietFreshHub.shop.repository;

import VietFreshHub.shop.entity.ShopMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ShopMemberRepository extends JpaRepository<ShopMember, Long> {

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
