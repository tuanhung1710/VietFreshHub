package VietFreshHub.product.service;

import VietFreshHub.product.dto.ProductRequest;

/** Xử lý dữ liệu biểu mẫu chưa lưu; ProductService phụ trách lưu và mở bán sản phẩm. */
public interface ProductDraftService {
    /** Thêm một dòng phân loại vào bản nháp chưa lưu. */
    void addVariant(ProductRequest form);

    /** Xóa dòng phân loại trong bản nháp nhưng giữ ít nhất một dòng. */
    void removeVariant(ProductRequest form, int index);

    /** Dùng ảnh sản phẩm làm ảnh của phân loại được chọn trong bản nháp. */
    void useProductImage(ProductRequest form, int index);
}
