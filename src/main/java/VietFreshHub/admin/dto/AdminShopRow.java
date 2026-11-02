package VietFreshHub.admin.dto;

import VietFreshHub.shop.entity.Shop;

import java.time.LocalDateTime;

public record AdminShopRow(
        Long shopId,
        String shopCode,
        String shopName,
        String description,
        String phone,
        String email,
        String status,
        String availabilityStatus,
        LocalDateTime createdAt
) {
    public static AdminShopRow from(Shop shop) {
        return new AdminShopRow(
                shop.getShopId(),
                shop.getShopCode(),
                shop.getShopName(),
                shop.getDescription(),
                shop.getPhone(),
                shop.getEmail(),
                shop.getStatus(),
                shop.getAvailabilityStatus(),
                shop.getCreatedAt()
        );
    }
}
