package VietFreshHub.product.exception;

public class CatalogException extends RuntimeException {

    /** Tạo lỗi nghiệp vụ để controller có thể trả thông báo dễ hiểu cho người dùng. */
    public CatalogException(String message) {
        super(message);
    }
}
