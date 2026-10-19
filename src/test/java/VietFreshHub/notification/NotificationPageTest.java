package VietFreshHub.notification;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.notification.controller.NotificationController;
import VietFreshHub.notification.entity.Notification;
import VietFreshHub.notification.repository.NotificationRepository;
import VietFreshHub.notification.services.NotificationServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationPageTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(repository, mock(EntityManager.class));
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken user = UsernamePasswordAuthenticationToken.authenticated(
            "owner@example.com", "", AuthorityUtils.createAuthorityList("ROLE_SELLER"));

    @BeforeEach
    void setUp() {
        when(auth.getCurrentUserId(user)).thenReturn(7L);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service, auth))
                .setViewResolvers(views).build();
    }

    private Notification notification(Long id, boolean read) {
        var item = new Notification();
        item.setNotificationId(id);
        item.setTitle("Gói dịch vụ <Pro>");
        item.setContent("<script>alert(1)</script>\nNội dung");
        item.setCreatedAt(LocalDateTime.of(2026, 10, 19, 10, 30));
        if (read) item.setReadAt(item.getCreatedAt().plusMinutes(1));
        return item;
    }

    @Test
    void rendersOnlyCurrentUserPageEscapesContentAndShowsUnreadAction() throws Exception {
        when(repository.findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notification(1L, false), notification(2L, true)), PageRequest.of(0, 20), 41));
        mvc.perform(get("/notifications").principal(user).param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gói dịch vụ &lt;Pro&gt;")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("19/10/2026 10:30")))
                .andExpect(content().string(containsString("/notifications/1/read")))
                .andExpect(content().string(not(containsString("/notifications/2/read"))))
                .andExpect(content().string(containsString("/notifications?page=1")));
        verify(repository).findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(7L, PageRequest.of(0, 20));
    }

    @Test
    void negativePageShowsEmptyFirstPage() throws Exception {
        when(repository.findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/notifications").principal(user).param("page", "-3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có thông báo nào.")))
                .andExpect(content().string(not(containsString("aria-label=\"Phân trang thông báo\""))));
    }

    @Test
    void readUsesAuthenticatedUserAndPreservesTimestampOnRepeat() throws Exception {
        var item = notification(1L, false);
        when(repository.findByNotificationIdAndUser_UserId(1L, 7L)).thenReturn(Optional.of(item));
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        mvc.perform(post("/notifications/1/read").principal(user).param("userId", "999"))
                .andExpect(redirectedUrl("/notifications"));
        assertNotNull(item.getReadAt());
        assertFalse(item.getReadAt().isBefore(before));
        assertEquals(0, item.getReadAt().getNano());
        var first = item.getReadAt();
        service.markAsRead(7L, 1L);
        assertEquals(first, item.getReadAt());
        verify(repository, never()).save(any());
        verify(repository, never()).findByNotificationIdAndUser_UserId(1L, 999L);
    }

    @Test
    void cannotReadAnotherUsersOrMissingNotification() {
        assertThrows(AccessDeniedException.class, () -> service.markAsRead(7L, 99L));
        verify(repository).findByNotificationIdAndUser_UserId(99L, 7L);
        verify(repository, never()).save(any());
    }

    @Test
    void invalidIdsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.getMyNotifications(null, 0));
        assertThrows(IllegalArgumentException.class, () -> service.getMyNotifications(0L, 0));
        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(-1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(7L, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(7L, null));
        verifyNoInteractions(repository);
    }
}
