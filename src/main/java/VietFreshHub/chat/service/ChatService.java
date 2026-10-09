package VietFreshHub.chat.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.chat.dto.ChatConversationView;
import VietFreshHub.chat.dto.ChatMessageView;
import VietFreshHub.chat.dto.ChatShopOption;
import VietFreshHub.chat.entity.ChatMessage;
import VietFreshHub.chat.entity.Conversation;
import VietFreshHub.chat.repository.ChatMessageRepository;
import VietFreshHub.chat.repository.ConversationRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.entity.ShopMember;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String ACTIVE = "ACTIVE";
    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";
    private static final String STORE_MANAGER_ROLE = "ROLE_STORE_MANAGER";
    private static final int MESSAGE_HISTORY_LIMIT = 50;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ShopRepository shopRepository;
    private final ShopMemberRepository shopMemberRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public Long getCurrentUserId(String email) {
        return requireActiveUser(email).getUserId();
    }

    @Transactional(readOnly = true)
    public List<ChatShopOption> listCustomerShops(String email) {
        User customer = requireUserWithRole(email, CUSTOMER_ROLE);
        return shopRepository.findAllByStatusOrderByShopNameAsc(ACTIVE).stream()
                .filter(shop -> !shopMemberRepository.existsActiveMembership(
                        shop.getShopId(), customer.getUserId()))
                .map(shop -> new ChatShopOption(shop.getShopId(), shop.getShopName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatConversationView> listCustomerConversations(String email) {
        User customer = requireUserWithRole(email, CUSTOMER_ROLE);
        return conversationRepository.findCustomerConversations(customer.getUserId()).stream()
                .map(conversation -> toConversationView(conversation, customer.getUserId(), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatConversationView> listShopConversations(String email) {
        User manager = requireUserWithRole(email, STORE_MANAGER_ROLE);
        List<Long> shopIds = shopMemberRepository.findActiveMembershipsByUserId(manager.getUserId())
                .stream()
                .map(ShopMember::getShop)
                .map(Shop::getShopId)
                .toList();
        if (shopIds.isEmpty()) return List.of();

        return conversationRepository.findShopConversations(shopIds).stream()
                .map(conversation -> toConversationView(conversation, manager.getUserId(), false))
                .toList();
    }

    @Transactional
    public ChatConversationView startCustomerConversation(String email, Long shopId) {
        User customer = requireUserWithRole(email, CUSTOMER_ROLE);
        Shop shop = shopRepository.findByShopIdForUpdate(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cửa hàng."));
        if (!ACTIVE.equalsIgnoreCase(shop.getStatus())) {
            throw new IllegalStateException("Cửa hàng hiện không nhận tin nhắn.");
        }
        if (shopMemberRepository.existsActiveMembership(shopId, customer.getUserId())) {
            throw new IllegalArgumentException("Bạn không thể bắt đầu chat với cửa hàng của mình.");
        }

        Conversation conversation = conversationRepository
                .findFirstByCustomer_UserIdAndShop_ShopIdOrderByCreatedAtDesc(
                        customer.getUserId(), shopId)
                .orElseGet(() -> conversationRepository.save(Conversation.open(customer, shop)));
        if (conversation.getClosedAt() != null) {
            conversation.reopen();
            conversation = conversationRepository.save(conversation);
        }
        return toConversationView(conversation, customer.getUserId(), true);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> listMessages(String email, Long conversationId) {
        User user = requireActiveUser(email);
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(user, conversation);

        List<ChatMessage> newestFirst = chatMessageRepository
                .findByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(
                        conversationId, Limit.of(MESSAGE_HISTORY_LIMIT));
        List<ChatMessageView> chronological = new ArrayList<>(newestFirst.size());
        for (int index = newestFirst.size() - 1; index >= 0; index--) {
            chronological.add(toMessageView(newestFirst.get(index), user.getUserId()));
        }
        return chronological;
    }

    @Transactional
    public void markMessagesRead(String email, Long conversationId) {
        User user = requireActiveUser(email);
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(user, conversation);
        chatMessageRepository.markUnreadMessagesRead(
                conversationId,
                user.getUserId(),
                LocalDateTime.now(ZoneOffset.UTC).withNano(0)
        );
    }

    @Transactional
    public ChatMessageView sendMessage(String email, Long conversationId, String content) {
        User sender = requireActiveUser(email);
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(sender, conversation);
        if (conversation.getClosedAt() != null) {
            throw new IllegalStateException("Cuộc trò chuyện đã đóng.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Tin nhắn không được để trống.");
        }
        String trimmedContent = content.trim();
        if (trimmedContent.length() > 4000) {
            throw new IllegalArgumentException("Tin nhắn tối đa 4.000 ký tự.");
        }

        ChatMessage saved = chatMessageRepository.saveAndFlush(
                ChatMessage.text(conversation, sender, trimmedContent));
        return toMessageView(saved, sender.getUserId());
    }

    @Transactional(readOnly = true)
    public void assertCanAccessConversation(String email, Long conversationId) {
        User user = requireActiveUser(email);
        assertParticipant(user, requireConversation(conversationId));
    }

    private ChatConversationView toConversationView(
            Conversation conversation,
            Long viewerId,
            boolean customerSide
    ) {
        ChatMessage lastMessage = chatMessageRepository
                .findFirstByConversation_ConversationIdOrderByCreatedAtDescMessageIdDesc(
                        conversation.getConversationId())
                .orElse(null);
        long unreadCount = chatMessageRepository
                .countByConversation_ConversationIdAndSender_UserIdNotAndReadAtIsNull(
                        conversation.getConversationId(), viewerId);
        return new ChatConversationView(
                conversation.getConversationId(),
                conversation.getShop().getShopId(),
                conversation.getShop().getShopName(),
                conversation.getCustomer().getUserId(),
                conversation.getCustomer().getFullName(),
                customerSide ? conversation.getShop().getShopName()
                        : conversation.getCustomer().getFullName(),
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? conversation.getCreatedAt() : lastMessage.getCreatedAt(),
                unreadCount
        );
    }

    private ChatMessageView toMessageView(ChatMessage message, Long viewerId) {
        return new ChatMessageView(
                message.getMessageId(),
                message.getSender().getUserId(),
                message.getSender().getFullName(),
                message.getContent(),
                message.getCreatedAt(),
                viewerId.equals(message.getSender().getUserId())
        );
    }

    private User requireUserWithRole(String email, String requiredRole) {
        User user = requireActiveUser(email);
        if (!userRoleRepository.findRoleNamesByUserId(user.getUserId()).contains(requiredRole)) {
            throw new AccessDeniedException("Tài khoản không có quyền thực hiện thao tác này.");
        }
        return user;
    }

    private User requireActiveUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Phiên đăng nhập không hợp lệ."));
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Tài khoản hiện không hoạt động.");
        }
        return user;
    }

    private Conversation requireConversation(Long conversationId) {
        return conversationRepository.findConversationWithParticipants(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc trò chuyện."));
    }

    private void assertParticipant(User user, Conversation conversation) {
        if (user.getUserId().equals(conversation.getCustomer().getUserId())) return;
        if (shopMemberRepository.existsActiveMembership(
                conversation.getShop().getShopId(), user.getUserId())) return;
        throw new AccessDeniedException("Bạn không thuộc cuộc trò chuyện này.");
    }
}
