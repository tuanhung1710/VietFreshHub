package VietFreshHub.product.validator;

import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Kiểm tra dữ liệu sản phẩm/SKU và lô ban đầu; không ghi dữ liệu hay thay đổi trạng thái bán. */
@Component
@RequiredArgsConstructor
public class ProductFormValidator {
    private static final List<String> SALE_UNITS = List.of("kg", "g", "quả", "chùm", "nải", "buồng", "hộp", "túi", "khay", "thùng", "sọt", "others");

    private final CategoryValidator categoryValidator;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    /** Dùng cùng danh sách đơn vị cho kiểm tra dữ liệu và biểu mẫu MVC. */
    public List<String> units() {
        return SALE_UNITS;
    }

    /** Kiểm tra thông tin sản phẩm, danh mục, ảnh và hướng dẫn chuẩn bị trước khi lưu. */
    public void validateProductFields(ProductRequest form) {
        String name = form.getName();
        require(
                name != null && !name.isBlank() && name.trim().length() <= 250,
                "Nhập tên sản phẩm, tối đa 250 ký tự.");
        require(
                form.getOrigin() != null
                        && !form.getOrigin().isBlank()
                        && form.getOrigin().length() <= 200,
                "Nhập xuất xứ sản phẩm, tối đa 200 ký tự.");
        require(
                form.getCategoryId() != null
                        && categoryValidator.isActiveCategoryChain(form.getCategoryId()),
                "Chọn danh mục còn hoạt động. Các danh mục cấp trên cũng phải còn hoạt động.");
        validateImageUrl(form.getImageUrl());
        require(
                form.getExpiryWarningDays() != null && form.getExpiryWarningDays() >= 0,
                "Số ngày nhắc trước hạn sử dụng phải là số nguyên không âm.");
        if (form.isRequiresPreparationCheck()) {
            require(
                    form.getPreparationNote() != null && !form.getPreparationNote().isBlank(),
                    "Nhập hướng dẫn kiểm tra và đóng gói.");
        }
    }

    /** Chỉ chấp nhận liên kết HTTP, HTTPS hoặc ảnh nội bộ trong giới hạn độ dài cho phép. */
    private void validateImageUrl(String url) {
        boolean valid = url != null && !url.isBlank() && url.length() <= 1000;
        if (valid) {
            valid =
                    url.startsWith("https://")
                            || url.startsWith("http://")
                            || url.startsWith("/images/");
        }
        require(valid, "Dán liên kết ảnh bắt đầu bằng http:// hoặc https://, hoặc tải ảnh lên.");
    }

    /**
     * Kiểm tra SKU, giá, ảnh, đơn vị, khối lượng và ngưỡng cảnh báo của phân loại.
     * Không cho đổi đơn vị khi đã có tồn kho, giao dịch kho hoặc tham chiếu đơn hàng.
     */
    public void validateVariantFields(VariantRequest form, ProductVariant existing) {
        if (form.getSku() != null && !form.getSku().isBlank()) {
            require(
                    form.getSku().matches("[A-Za-z0-9_-]{1,100}"),
                    "SKU chỉ được chứa chữ cái không dấu, chữ số, dấu gạch nối (-) và dấu gạch dưới (_).");
            if (existing == null) {
                require(
                        !form.getSku().matches("(?i)(SKU-[0-9]+|TMP-.*)"),
                        "Để trống SKU để cấp tự động; mã SKU- và TMP- được dành cho hệ thống.");
            }
            if (existing == null || !existing.getSku().equalsIgnoreCase(form.getSku())) {
                require(
                        !productVariantRepository.existsBySkuIgnoreCase(form.getSku()),
                        "SKU đã tồn tại.");
            }
        }
        require(
                form.getVariantName() != null
                        && !form.getVariantName().isBlank()
                        && form.getVariantName().length() <= 200,
                "Nhập tên phân loại.");
        InventoryRules.price(form.getPrice());
        validateImageUrl(form.getThumbnailUrl());
        // Cho phép giữ đơn vị cũ ngoài danh sách hiện hành để vẫn sửa được dữ liệu đã có.
        boolean supportedUnit = SALE_UNITS.contains(form.getUnit());
        boolean unchangedUnit =
                existing != null && Objects.equals(existing.getUnit(), form.getUnit());
        require(supportedUnit || unchangedUnit, "Chọn đơn vị bán hợp lệ.");
        require(
                form.getLowStockThreshold() != null && form.getLowStockThreshold() >= 0,
                "Mức tồn kho cảnh báo phải lớn hơn hoặc bằng 0.");
        require(
                form.getCompareAtPrice() == null
                        || form.getCompareAtPrice().compareTo(form.getPrice()) >= 0,
                "Giá tham chiếu phải lớn hơn hoặc bằng giá bán.");
        require(
                form.getWeight() == null
                        || (form.getWeight().compareTo(new BigDecimal("0.001")) >= 0
                                && form.getWeight().stripTrailingZeros().scale() <= 3),
                "Giá trị phải từ 0,001 kg, tối đa 3 chữ số thập phân.");
        if (existing != null && !Objects.equals(existing.getUnit(), form.getUnit())) {
            // Khi đã phát sinh tồn hoặc lịch sử, đổi đơn vị sẽ làm sai ý nghĩa các số lượng đã lưu.
            require(
                    !hasStockOrHistory(existing.getVariantId()),
                    "Không thể đổi đơn vị bán của phân loại đã có tồn kho hoặc lịch sử. Hãy tạo phân loại mới.");
        }
    }

    /** Kiểm tra phân loại đã có giao dịch kho, đơn hàng, tồn thực tế hoặc hàng giữ cho đơn. */
    private boolean hasStockOrHistory(Long variantId) {
        // Lịch sử kho hoặc đơn hàng vẫn ràng buộc đơn vị bán ngay cả khi tồn hiện tại bằng 0.
        if (inventoryTransactionRepository.existsByVariantId(variantId)
                || productVariantRepository.orderReferenceCount(variantId) > 0) {
            return true;
        }
        for (InventoryBatch batch :
                inventoryBatchRepository.findByVariantIdOrderByExpiryDateAscBatchIdAsc(variantId)) {
            if (batch.getQuantityOnHand() > 0 || batch.getReservedQuantity() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Kiểm tra từ 1 đến 20 phân loại mới và tồn ban đầu; máy chủ cấp SKU sau khi có ID. */
    public void validateNewVariants(ProductRequest form) {
        require(
                form.getVariants() != null
                        && !form.getVariants().isEmpty()
                        && form.getVariants().size() <= 20,
                "Mỗi sản phẩm cần từ 1 đến 20 phân loại.");
        for (VariantRequest variant : form.getVariants()) {
            // Máy chủ cấp SKU sau khi cơ sở dữ liệu sinh ID, không dùng mã gửi từ biểu mẫu.
            variant.setSku(null);
            validateVariantFields(variant, null);
            initialQuantity(variant);
        }
    }

    /** Kiểm tra số lượng, ngày và giá vốn của lô đầu tiên; trả về 0 khi chọn chưa nhập hàng. */
    public int initialQuantity(VariantRequest form) {
        // Tạo SKU chưa nhập hàng không yêu cầu dữ liệu lô, nhưng số lượng phải trống hoặc bằng 0.
        if (form.isNoInitialStock()) {
            require(
                    form.getInitialQuantity() == null || form.getInitialQuantity() == 0,
                    "Khi chọn Chưa nhập hàng, số lượng nhập ban đầu phải bằng 0.");
            return 0;
        }
        require(
                form.getInitialQuantity() != null && form.getInitialQuantity() > 0,
                "Nhập số lượng lớn hơn 0 hoặc chọn Chưa nhập hàng.");
        // Nếu nhập hàng ngay, kiểm tra ngày nhập, hạn sử dụng được nhập trực tiếp và giá vốn.
        InventoryRules.dates(form.getReceivedDate(), form.getExpiryDate(), today());
        require(
                form.getCostPrice() == null || form.getCostPrice().signum() >= 0,
                "Giá vốn không được âm.");
        return form.getInitialQuantity();
    }
}
