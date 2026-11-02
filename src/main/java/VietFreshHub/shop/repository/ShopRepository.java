package VietFreshHub.shop.repository;

import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    @Query("select s from Shop s where s.status='ACTIVE' and s.availabilityStatus='OPEN' and (:keyword='' or locate(lower(:keyword),lower(s.shopName))>0)")
    org.springframework.data.domain.Page<Shop> findPublicShops(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

    Optional<Shop> findByShopIdAndStatus(Long shopId, String status);

    org.springframework.data.domain.Page<Shop> findByStatus(String status, org.springframework.data.domain.Pageable pageable);

    List<Shop> findAllByStatusOrderByShopNameAsc(String status);

    Optional<Shop> findByShopName(String shopName);

    @Query(value = """
            select s from Shop s
            where (:status = 'ALL' or s.status = :status)
              and (:keyword = ''
                   or lower(s.shopName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.email, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.phone, '')) like lower(concat('%', :keyword, '%')))
            """,
            countQuery = """
            select count(s) from Shop s
            where (:status = 'ALL' or s.status = :status)
              and (:keyword = ''
                   or lower(s.shopName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.email, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.phone, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<Shop> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.shopId = :shopId")
    Optional<Shop> findByShopIdForUpdate(@Param("shopId") Long shopId);
}
