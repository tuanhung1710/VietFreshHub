package VietFreshHub.admin.dto;

public record AdminAccountStats(
        long total,
        long active,
        long inactive,
        long blocked,
        long deleted
) {
}
