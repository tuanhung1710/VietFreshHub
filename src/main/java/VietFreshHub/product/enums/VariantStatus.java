package VietFreshHub.product.enums;

/** Cấu hình bán của SKU; OUT_OF_STOCK cũ vẫn thuộc nhóm bật bán, lượng khả dụng được tính riêng. */
public enum VariantStatus {
    ACTIVE,
    INACTIVE,
    OUT_OF_STOCK,
    DELETED
}
