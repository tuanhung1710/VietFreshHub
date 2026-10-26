package VietFreshHub.product.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.require;

import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.service.ProductDraftService;

import org.springframework.stereotype.Service;

@Service
public class ProductDraftServiceImpl implements ProductDraftService {
    @Override
    public void addVariant(ProductRequest form) {
        require(form.getVariants() != null && form.getVariants().size() < 20,
                "Mỗi sản phẩm có tối đa 20 phân loại.");
        form.getVariants().add(new VariantRequest());
    }

    @Override
    public void removeVariant(ProductRequest form, int index) {
        variantAt(form, index);
        require(form.getVariants().size() > 1, "Sản phẩm cần ít nhất một phân loại.");
        form.getVariants().remove(index);
    }

    @Override
    public void useProductImage(ProductRequest form, int index) {
        VariantRequest variant = variantAt(form, index);
        require(form.getImageUrl() != null && !form.getImageUrl().isBlank(),
                "Chọn ảnh sản phẩm trước khi dùng cho phân loại.");
        variant.setThumbnailUrl(form.getImageUrl());
    }

    private VariantRequest variantAt(ProductRequest form, int index) {
        require(form.getVariants() != null && index >= 0 && index < form.getVariants().size(),
                "Phân loại không hợp lệ.");
        return form.getVariants().get(index);
    }
}
