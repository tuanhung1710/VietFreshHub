package VietFreshHub.order.dto;
public record OrderActionsResponse(boolean cancellable,String cancellationHint,String paymentMethodLabel) {}
