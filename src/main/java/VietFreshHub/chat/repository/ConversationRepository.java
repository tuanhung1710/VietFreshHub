package VietFreshHub.chat.repository;

import VietFreshHub.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            join fetch c.customer
            join fetch c.shop
            where c.customer.userId = :customerId
            order by c.createdAt desc
            """)
    List<Conversation> findCustomerConversations(@Param("customerId") Long customerId);

    @Query("""
            select c from Conversation c
            join fetch c.customer
            join fetch c.shop
            where c.shop.shopId in :shopIds
            order by c.createdAt desc
            """)
    List<Conversation> findShopConversations(@Param("shopIds") List<Long> shopIds);

    Optional<Conversation> findFirstByCustomer_UserIdAndShop_ShopIdOrderByCreatedAtDesc(
            Long customerId,
            Long shopId
    );

    @Query("""
            select c from Conversation c
            join fetch c.customer
            join fetch c.shop
            where c.conversationId = :conversationId
            """)
    Optional<Conversation> findConversationWithParticipants(
            @Param("conversationId") Long conversationId
    );
}
