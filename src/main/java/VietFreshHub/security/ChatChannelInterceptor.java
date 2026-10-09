package VietFreshHub.security;

import VietFreshHub.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SEND_DESTINATION = Pattern.compile("^/app/chat/(\\d+)/send$");
    private static final Pattern TOPIC_DESTINATION = Pattern.compile("^/topic/conversations/(\\d+)$");

    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) return message;

        String destination = accessor.getDestination();
        Pattern expected = command == StompCommand.SEND ? SEND_DESTINATION : TOPIC_DESTINATION;
        Matcher matcher = destination == null ? null : expected.matcher(destination);
        Principal principal = accessor.getUser();
        if (matcher == null || !matcher.matches() || principal == null) {
            throw new AccessDeniedException("Đích chat không hợp lệ hoặc chưa đăng nhập.");
        }
        try {
            chatService.assertCanAccessConversation(principal.getName(), Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Mã cuộc trò chuyện không hợp lệ.");
        }
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception exception) {
        if (exception == null) return;

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StringBuilder exceptionTypes = new StringBuilder();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (!exceptionTypes.isEmpty()) exceptionTypes.append(" -> ");
            exceptionTypes.append(cause.getClass().getSimpleName());
        }

        log.error("STOMP inbound processing failed: command={}, destination={}, exceptionTypes={}",
                accessor.getCommand(), accessor.getDestination(), exceptionTypes);
    }
}
