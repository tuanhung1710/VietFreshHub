package VietFreshHub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(
        @NotBlank(message = "Tin nhắn không được để trống")
        @Size(max = 4000, message = "Tin nhắn tối đa 4.000 ký tự")
        String content
) {
}
