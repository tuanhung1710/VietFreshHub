package VietFreshHub.product.service;

import VietFreshHub.product.dto.ProductRequest;

/** Xử lý dữ liệu biểu mẫu chưa lưu; ProductService phụ trách lưu và mở bán sản phẩm. */
public interface ProductDraftService {
    /** Kiểm tra một thao tác duy nhất rồi cập nhật bản nháp, không lưu vào cơ sở dữ liệu. */
    void update(
            ProductRequest form,
            boolean addVariant,
            Integer removeVariant,
            Integer useProductImage);
}
