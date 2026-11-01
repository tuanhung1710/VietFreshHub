package VietFreshHub.sellerapplication.event;

public record SellerApplicationApprovedEvent(
        Long applicationId,
        String recipientEmail,
        String recipientName,
        String businessName
) {
}
