package VietFreshHub.Order.dto;
public record OrderActionsResponse(boolean cancellable,String cancellationHint,String paymentMethodLabel) {}
