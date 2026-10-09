package VietFreshHub.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record AdminAccountRow(
        Long userId,
        String fullName,
        String email,
        String phone,
        String status,
        LocalDateTime createdAt,
        LocalDateTime emailVerifiedAt,
        List<Integer> roleIds,
        List<String> roleNames,
        List<String> providers
) {
    public String roleIdsCsv() {
        return roleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public String roleNamesCsv() {
        return String.join(", ", roleNames);
    }

    public String providersLabel() {
        return providers.isEmpty() ? "Email và mật khẩu" : String.join(", ", providers);
    }
}
