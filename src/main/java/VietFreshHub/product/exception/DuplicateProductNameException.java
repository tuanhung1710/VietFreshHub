package VietFreshHub.product.exception;

public class DuplicateProductNameException extends CatalogException {
    public static final String MESSAGE =
            "Tên sản phẩm đã tồn tại trong cửa hàng. Vui lòng chọn tên khác.";

    public DuplicateProductNameException() {
        super(MESSAGE);
    }
}
