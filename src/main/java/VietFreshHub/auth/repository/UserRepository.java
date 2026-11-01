package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    interface NewCustomersByHour {
        Integer getCustomerYear();
        Integer getCustomerMonth();
        Integer getCustomerDay();
        Integer getCustomerHour();
        Long getCustomerCount();
    }

    @Query("""
            select count(distinct u.userId) from User u
            join u.userRoles ur join ur.role r where r.roleName = 'CUSTOMER'
            """)
    long countCustomers();

    @Query("""
            select count(distinct u.userId) from User u
            join u.userRoles ur join ur.role r
            where r.roleName = 'CUSTOMER' and u.createdAt >= :from and u.createdAt < :to
            """)
    long countNewCustomers(@Param("from") java.time.LocalDateTime from,
                           @Param("to") java.time.LocalDateTime to);

    @Query("""
            select year(u.createdAt) as customerYear, month(u.createdAt) as customerMonth,
                   day(u.createdAt) as customerDay, hour(u.createdAt) as customerHour,
                   count(distinct u.userId) as customerCount
            from User u join u.userRoles ur join ur.role r
            where r.roleName = 'CUSTOMER' and u.createdAt >= :from and u.createdAt < :to
            group by year(u.createdAt), month(u.createdAt), day(u.createdAt), hour(u.createdAt)
            order by year(u.createdAt), month(u.createdAt), day(u.createdAt), hour(u.createdAt)
            """)
    List<NewCustomersByHour> countNewCustomersByHour(@Param("from") java.time.LocalDateTime from,
                                                      @Param("to") java.time.LocalDateTime to);

    @Query("""
            select count(distinct u.userId) from User u join u.userRoles ur join ur.role r
            where r.roleName = 'CUSTOMER'
              and exists (select o.orderId from Order o
                  where o.customer = u and o.status = VietFreshHub.order.entity.Order.Status.COMPLETED
                    and exists (select s.shopOrderId from ShopOrder s
                        where s.order = o and s.status = 'COMPLETED')
                    and not exists (select s.shopOrderId from ShopOrder s
                        where s.order = o and s.status <> 'COMPLETED'))
            """)
    long countCustomersWithSuccessfulPurchase();

    @Query("""
            select count(distinct u.userId) from User u join u.userRoles ur join ur.role r
            where r.roleName = 'CUSTOMER'
              and (select count(o.orderId) from Order o
                  where o.customer = u and o.status = VietFreshHub.order.entity.Order.Status.COMPLETED
                    and exists (select s.shopOrderId from ShopOrder s
                        where s.order = o and s.status = 'COMPLETED')
                    and not exists (select s.shopOrderId from ShopOrder s
                        where s.order = o and s.status <> 'COMPLETED')) >= 2
            """)
    long countReturningCustomers();

    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndUserIdNot(String phone, Long userId);
    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("""
            select count(distinct u.userId)
            from User u
            join u.userRoles ur
            join ur.role r
            where u.status = :status
              and r.roleName = :roleName
              and u.userId <> :excludedUserId
            """)
    long countOtherUsersWithRoleAndStatus(
            @Param("status") String status,
            @Param("roleName") String roleName,
            @Param("excludedUserId") Long excludedUserId
    );

    List<User> findByStatusIgnoreCaseAndUserRoles_Role_RoleNameOrderByUserIdAsc(String status, String roleName);
}
