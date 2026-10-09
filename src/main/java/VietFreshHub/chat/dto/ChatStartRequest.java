package VietFreshHub.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatStartRequest(@NotNull Long shopId) {
}
