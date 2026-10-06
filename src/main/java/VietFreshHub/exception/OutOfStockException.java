package VietFreshHub.exception;

public class OutOfStockException extends RuntimeException {
    private final int availableStock;

    public OutOfStockException(String message, int availableStock) {
        super(message);
        this.availableStock = availableStock;
    }

    public OutOfStockException(String message) {
        super(message);
        this.availableStock = 0;
    }

    public int getAvailableStock() {
        return availableStock;
    }
}
