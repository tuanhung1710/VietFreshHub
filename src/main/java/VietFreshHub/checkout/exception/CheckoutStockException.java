package VietFreshHub.checkout.exception;

import VietFreshHub.exception.CheckoutException;

/** Thiếu hàng là lỗi nghiệp vụ; transaction đặt hàng phải rollback trước khi hiển thị lại. */
public class CheckoutStockException extends CheckoutException {
    private final Long variantId;
    private final int requestedQuantity;
    private final long availableStock;

    public CheckoutStockException(Long variantId, String productName, String variantName,
                                   int requestedQuantity, long availableStock) {
        super(productName + " — " + variantName + ": bạn yêu cầu " + requestedQuantity
                + ", hiện chỉ còn " + Math.max(0, availableStock)
                + ". Vui lòng điều chỉnh trong giỏ và xác nhận lại đơn hàng.");
        this.variantId = variantId;
        this.requestedQuantity = requestedQuantity;
        this.availableStock = Math.max(0, availableStock);
    }

    public Long getVariantId() { return variantId; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public long getAvailableStock() { return availableStock; }
}
