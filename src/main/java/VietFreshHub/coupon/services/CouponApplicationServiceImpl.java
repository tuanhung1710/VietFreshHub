package VietFreshHub.coupon.services;

import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.coupon.dto.CouponApplicationResponse;
import VietFreshHub.coupon.dto.CouponUsageResponse;
import VietFreshHub.coupon.entity.Coupon;
import VietFreshHub.coupon.entity.CouponStatus;
import VietFreshHub.coupon.entity.CouponUsage;
import VietFreshHub.coupon.repository.CouponRepository;
import VietFreshHub.coupon.repository.CouponUsageRepository;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.promotion.entity.DiscountType;
import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponApplicationServiceImpl
        implements CouponApplicationService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final EntityManager em;

    @Override
    public CouponApplicationResponse preview(
            Long customerId,
            String code,
            List<Long> cartItemIds
    ) {
        requireId(customerId);

        if (cartItemIds == null || cartItemIds.isEmpty()
                || cartItemIds.stream()
                .anyMatch(id -> id == null || id <= 0)) {
            throw invalid("Vui lòng chọn sản phẩm để áp dụng coupon");
        }

        var selectedIds = new HashSet<>(cartItemIds);

        if (selectedIds.size() != cartItemIds.size()) {
            throw invalid("Danh sách sản phẩm bị trùng");
        }

        List<CartItem> items = em.createQuery("""
                SELECT i
                FROM CartItem i
                JOIN FETCH i.variant v
                JOIN FETCH v.product p
                JOIN FETCH p.shop
                WHERE i.cart.user.userId = :customerId
                  AND i.cart.status = 'ACTIVE'
                  AND i.cartItemId IN :itemIds
                """, CartItem.class)
                .setParameter("customerId", customerId)
                .setParameter("itemIds", selectedIds)
                .getResultList();

        if (items.size() != selectedIds.size()) {
            throw invalid(
                    "Sản phẩm không còn trong giỏ hàng của bạn"
            );
        }

        Coupon coupon = couponRepository
                .findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> invalid("Mã coupon không tồn tại"));

        validateCoupon(coupon, customerId);

        Promotion promotion = coupon.getPromotion();
        BigDecimal eligibleSubtotal = BigDecimal.ZERO;

        for (CartItem item : items) {
            var variant = item.getVariant();
            var product = variant.getProduct();

            if (!promotion.getShopId().equals(
                    product.getShop().getShopId())) {
                continue;
            }

            if (matchesScope(
                    promotion.getPromotionId(),
                    product.getProductId(),
                    variant.getVariantId()
            )) {
                eligibleSubtotal = eligibleSubtotal.add(
                        lineTotal(variant.getPrice(), item.getQuantity())
                );
            }
        }

        return calculate(coupon, eligibleSubtotal);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CouponApplicationResponse applyToOrder(
            Long customerId,
            Long orderId,
            String code
    ) {
        requireId(customerId);
        requireId(orderId);
        String normalizedCode = normalizeCode(code);

        // Đồng bộ các dòng hàng vừa được Checkout tạo.
        em.flush();

        Order order = em.find(Order.class, orderId);

        if (order == null
                || !customerId.equals(
                order.getCustomer().getUserId())) {
            throw invalid("Không tìm thấy đơn hàng của bạn");
        }

        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (!customerId.equals(order.getCustomer().getUserId())) {
            throw invalid("Đơn hàng không thuộc tài khoản của bạn");
        }

        if (order.getStatus() != Order.Status.PENDING
                || !"UNPAID".equals(order.getPaymentStatus())) {
            throw invalid("Chỉ áp dụng coupon khi tạo đơn chưa thanh toán");
        }

        Long paymentCount = em.createQuery("""
                SELECT COUNT(p)
                FROM Payment p
                WHERE p.order.orderId = :orderId
                """, Long.class)
                .setParameter("orderId", orderId)
                .getSingleResult();

        if (paymentCount > 0) {
            throw invalid(
                    "Phải áp dụng coupon trước khi tạo thanh toán"
            );
        }

        if (hasRecordedDiscount(orderId)
                || order.getDiscountTotal().signum() != 0) {
            throw invalid("Đơn hàng đã có ưu đãi được áp dụng");
        }

        Coupon coupon = couponRepository
                .findByCodeForUpdate(normalizedCode)
                .orElseThrow(() -> invalid("Mã coupon không tồn tại"));

        // Tránh đọc trạng thái cũ nếu Entity đã được tải trước đó.
        em.refresh(coupon, LockModeType.PESSIMISTIC_WRITE);

        if (!coupon.getCode().equalsIgnoreCase(normalizedCode)) {
            throw invalid("Mã coupon vừa thay đổi, vui lòng thử lại");
        }

        Promotion promotion = coupon.getPromotion();

        em.refresh(promotion, LockModeType.PESSIMISTIC_WRITE);

        validateCoupon(coupon, customerId);

        List<ShopOrder> shopOrders = em.createQuery("""
                SELECT s
                FROM ShopOrder s
                WHERE s.order.orderId = :orderId
                ORDER BY s.shopOrderId
                """, ShopOrder.class)
                .setParameter("orderId", orderId)
                .getResultList();

        if (shopOrders.isEmpty()
                || shopOrders.stream().anyMatch(s ->
                !"PENDING".equals(s.getStatus())
                        || s.getDiscountTotal().signum() != 0)) {
            throw invalid("Đơn con không đủ điều kiện áp dụng coupon");
        }

        ShopOrder target = shopOrders.stream()
                .filter(s -> promotion.getShopId().equals(s.getShopId()))
                .findFirst()
                .orElseThrow(() -> invalid(
                        "Đơn hàng không có sản phẩm của shop phát hành mã"
                ));

        List<OrderItem> items = em.createQuery("""
                SELECT i
                FROM OrderItem i
                JOIN FETCH i.variant v
                JOIN FETCH v.product
                WHERE i.shopOrder.shopOrderId = :shopOrderId
                """, OrderItem.class)
                .setParameter("shopOrderId", target.getShopOrderId())
                .getResultList();

        BigDecimal eligibleSubtotal = BigDecimal.ZERO;

        for (OrderItem item : items) {
            var variant = item.getVariant();
            var product = variant.getProduct();

            if (!promotion.getShopId().equals(
                    product.getShop().getShopId())) {
                throw invalid("Sản phẩm trong đơn không thuộc đúng shop");
            }

            if (item.getDiscountAmount().signum() != 0) {
                throw invalid("Dòng hàng đã có ưu đãi khác");
            }

            if (matchesScope(
                    promotion.getPromotionId(),
                    product.getProductId(),
                    variant.getVariantId()
            )) {
                // Dùng giá đã chốt trong đơn.
                eligibleSubtotal = eligibleSubtotal.add(
                        lineTotal(item.getUnitPrice(), item.getQuantity())
                );
            }
        }

        CouponApplicationResponse response =
                calculate(coupon, eligibleSubtotal);

        if (response.discountAmount().compareTo(
                target.getSubtotal()) > 0) {
            throw invalid("Mức giảm vượt quá tiền hàng của shop");
        }

        target.setDiscountTotal(response.discountAmount());
        target.setTotalAmount(
                target.getSubtotal()
                        .subtract(target.getDiscountTotal())
                        .add(target.getShippingFee())
        );

        BigDecimal totalDiscount = shopOrders.stream()
                .map(ShopOrder::getDiscountTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setDiscountTotal(totalDiscount);
        order.setGrandTotal(
                order.getSubtotal()
                        .subtract(totalDiscount)
                        .add(order.getShippingFee())
        );

        // Lưu nguồn giảm giá ở cấp đơn con.
        em.createNativeQuery("""
                INSERT INTO dbo.order_discounts (
                    shop_order_id,
                    promotion_id,
                    coupon_id,
                    discount_amount,
                    created_at
                )
                VALUES (
                    :shopOrderId,
                    :promotionId,
                    :couponId,
                    :discountAmount,
                    :createdAt
                )
                """)
                .setParameter("shopOrderId", target.getShopOrderId())
                .setParameter("promotionId", promotion.getPromotionId())
                .setParameter("couponId", coupon.getCouponId())
                .setParameter("discountAmount", response.discountAmount())
                .setParameter("createdAt", utcNow())
                .executeUpdate();

        CouponUsage usage = CouponUsage.builder()
                .coupon(coupon)
                .user(order.getCustomer())
                .order(order)
                .discountAmount(response.discountAmount())
                .build();

        usageRepository.saveAndFlush(usage);

        return response;
    }

    @Override
    public Page<CouponUsageResponse> getUsageHistory(
            Long shopId,
            Long couponId,
            Pageable pageable
    ) {
        requireId(shopId);
        requireId(couponId);

        couponRepository
                .findByCouponIdAndPromotion_ShopId(couponId, shopId)
                .orElseThrow(() -> invalid(
                        "Không tìm thấy coupon của shop"
                ));

        return usageRepository
                .findByCoupon_CouponIdAndCoupon_Promotion_ShopIdOrderByUsedAtDescCouponUsageIdDesc(
                        couponId,
                        shopId,
                        pageable
                )
                .map(usage -> new CouponUsageResponse(
                        usage.getCouponUsageId(),
                        usage.getCoupon().getCouponId(),
                        usage.getCoupon().getCode(),
                        usage.getUser().getUserId(),
                        usage.getUser().getFullName(),
                        usage.getOrder().getOrderId(),
                        usage.getOrder().getOrderCode(),
                        usage.getDiscountAmount(),
                        usage.getUsedAt()
                ));
    }

    private void validateCoupon(Coupon coupon, Long customerId) {
        LocalDateTime now = utcNow();
        Promotion promotion = coupon.getPromotion();

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw invalid("Coupon hiện không hoạt động");
        }

        if (now.isBefore(coupon.getStartAt())
                || !now.isBefore(coupon.getEndAt())) {
            throw invalid("Coupon chưa đến hạn hoặc đã hết hạn");
        }

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw invalid("Chương trình khuyến mãi không hoạt động");
        }

        if (now.isBefore(promotion.getStartAt())
                || !now.isBefore(promotion.getEndAt())) {
            throw invalid("Chương trình chưa đến hạn hoặc đã hết hạn");
        }

        if (promotion.getShopId() == null) {
            throw invalid("Bản hiện tại chỉ hỗ trợ coupon của shop");
        }

        if (coupon.getUsageLimit() != null
                && usageRepository.countByCoupon_CouponId(
                coupon.getCouponId()) >= coupon.getUsageLimit()) {
            throw invalid("Coupon đã hết lượt sử dụng");
        }

        if (coupon.getPerUserLimit() != null
                && usageRepository.countByCoupon_CouponIdAndUser_UserId(
                coupon.getCouponId(),
                customerId
        ) >= coupon.getPerUserLimit()) {
            throw invalid("Bạn đã dùng hết lượt của coupon này");
        }
    }

    private CouponApplicationResponse calculate(
            Coupon coupon,
            BigDecimal eligibleSubtotal
    ) {
        Promotion promotion = coupon.getPromotion();

        if (eligibleSubtotal.signum() <= 0) {
            throw invalid("Không có sản phẩm đủ điều kiện áp dụng coupon");
        }

        BigDecimal minimum = coupon.getMinimumOrderValue()
                .max(promotion.getMinimumOrderValue());

        if (eligibleSubtotal.compareTo(minimum) < 0) {
            throw invalid(
                    "Tiền hàng đủ điều kiện chưa đạt mức tối thiểu "
                            + minimum.toPlainString() + "đ"
            );
        }

        BigDecimal discount;

        if (promotion.getDiscountType() == DiscountType.PERCENT) {
            discount = eligibleSubtotal
                    .multiply(promotion.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        } else {
            discount = promotion.getDiscountValue();
        }

        if (promotion.getMaxDiscount() != null) {
            discount = discount.min(promotion.getMaxDiscount());
        }

        discount = discount.min(eligibleSubtotal)
                .setScale(0, RoundingMode.DOWN);

        if (discount.signum() <= 0) {
            throw invalid("Coupon không tạo ra mức giảm hợp lệ");
        }

        return new CouponApplicationResponse(
                coupon.getCouponId(),
                coupon.getCode(),
                promotion.getPromotionId(),
                promotion.getShopId(),
                eligibleSubtotal,
                discount,
                eligibleSubtotal.subtract(discount)
        );
    }

    private boolean matchesScope(
            Long promotionId,
            Long productId,
            Long variantId
    ) {
        // Không cấu hình phạm vi: áp dụng toàn shop.
        // Có phạm vi: khớp sản phẩm HOẶC SKU HOẶC danh mục trực tiếp.
        Number matched = (Number) em.createNativeQuery("""
                SELECT CASE WHEN
                    (
                        NOT EXISTS (
                            SELECT 1 FROM dbo.promotion_products
                            WHERE promotion_id = :promotionId
                        )
                        AND NOT EXISTS (
                            SELECT 1 FROM dbo.promotion_variants
                            WHERE promotion_id = :promotionId
                        )
                        AND NOT EXISTS (
                            SELECT 1 FROM dbo.promotion_categories
                            WHERE promotion_id = :promotionId
                        )
                    )
                    OR EXISTS (
                        SELECT 1 FROM dbo.promotion_products
                        WHERE promotion_id = :promotionId
                          AND product_id = :productId
                    )
                    OR EXISTS (
                        SELECT 1 FROM dbo.promotion_variants
                        WHERE promotion_id = :promotionId
                          AND variant_id = :variantId
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM dbo.promotion_categories pc
                        JOIN dbo.product_categories c
                          ON c.category_id = pc.category_id
                        WHERE pc.promotion_id = :promotionId
                          AND c.product_id = :productId
                    )
                    THEN 1 ELSE 0 END
                """)
                .setParameter("promotionId", promotionId)
                .setParameter("productId", productId)
                .setParameter("variantId", variantId)
                .getSingleResult();

        return matched.intValue() == 1;
    }

    private boolean hasRecordedDiscount(Long orderId) {
        Number count = (Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM dbo.order_discounts d
                WHERE d.order_id = :orderId
                   OR d.shop_order_id IN (
                        SELECT shop_order_id
                        FROM dbo.shop_orders
                        WHERE order_id = :orderId
                   )
                   OR d.order_item_id IN (
                        SELECT i.order_item_id
                        FROM dbo.order_items i
                        JOIN dbo.shop_orders s
                          ON s.shop_order_id = i.shop_order_id
                        WHERE s.order_id = :orderId
                   )
                """)
                .setParameter("orderId", orderId)
                .getSingleResult();

        return count.longValue() > 0;
    }

    private BigDecimal lineTotal(BigDecimal price, Integer quantity) {
        if (price == null || price.signum() < 0
                || quantity == null || quantity <= 0) {
            throw invalid("Giá hoặc số lượng sản phẩm không hợp lệ");
        }

        return price.multiply(BigDecimal.valueOf(quantity));
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()
                || code.strip().length() > 100) {
            throw invalid("Vui lòng nhập mã coupon hợp lệ");
        }

        return code.strip();
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw invalid("ID không hợp lệ");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}