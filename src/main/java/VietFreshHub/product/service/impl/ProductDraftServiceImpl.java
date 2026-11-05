package VietFreshHub.product.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.require;

import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.service.ProductDraftService;

import org.springframework.stereotype.Service;

@Service
public class ProductDraftServiceImpl implements ProductDraftService {
    @Override
    public void update(
            ProductRequest form,
            boolean addVariant,
            Integer removeVariant,
            Integer useProductImage) {
        require(
                (addVariant ? 1 : 0)
                                + (removeVariant != null ? 1 : 0)
                                + (useProductImage != null ? 1 : 0)
                        == 1,
                "Chọn một thao tác với phân loại.");
        if (addVariant) {
            addVariant(form);
        } else if (removeVariant != null) {
            removeVariant(form, removeVariant);
        } else {
            useProductImage(form, useProductImage);
        }
    }

    /** Thêm dòng phân loại vào bản nháp, tối đa 20 dòng; chưa lưu vào cơ sở dữ liệu. */
    private void addVariant(ProductRequest form) {
        require(
                form.getVariants() != null && form.getVariants().size() < 20,
                "Mỗi sản phẩm có tối đa 20 phân loại.");
        form.getVariants().add(new VariantRequest());
    }

    /** Xóa dòng phân loại hợp lệ trong bản nháp nhưng luôn giữ lại ít nhất một dòng. */
    private void removeVariant(ProductRequest form, int index) {
        variantAt(form, index);
        require(form.getVariants().size() > 1, "Sản phẩm cần ít nhất một phân loại.");
        form.getVariants().remove(index);
    }

    /** Gán ảnh sản phẩm hiện có làm ảnh của dòng phân loại được chọn trong bản nháp. */
    private void useProductImage(ProductRequest form, int index) {
        VariantRequest variant = variantAt(form, index);
        require(
                form.getImageUrl() != null && !form.getImageUrl().isBlank(),
                "Chọn ảnh sản phẩm trước khi dùng cho phân loại.");
        variant.setThumbnailUrl(form.getImageUrl());
    }

    /** Lấy dòng phân loại trong bản nháp sau khi kiểm tra danh sách và chỉ số hợp lệ. */
    private VariantRequest variantAt(ProductRequest form, int index) {
        require(
                form.getVariants() != null && index >= 0 && index < form.getVariants().size(),
                "Phân loại không hợp lệ.");
        VariantRequest variant = form.getVariants().get(index);
        require(variant != null, "Phân loại không hợp lệ.");
        return variant;
    }
}
