package VietFreshHub.admin.repository;

import VietFreshHub.admin.dto.AdminProductModerationRow;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminProductModerationRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> lockByProductId(@Param("productId") Long productId);

    @Query(value = """
            select p.product_id as productId,
                   p.name as name,
                   p.slug as slug,
                   p.description as description,
                   s.shop_name as shopName,
                   p.status as status,
                   p.approval_status as approvalStatus,
                   p.moderation_blocked as moderationBlocked,
                   p.moderation_reason as moderationReason,
                   p.created_at as createdAt,
                   p.updated_at as updatedAt,
                   coalesce(
                       (select top (1) i.image_url from dbo.product_images i
                        where i.product_id = p.product_id and i.variant_id is null
                        order by i.is_primary desc, i.sort_order asc, i.image_id asc),
                       (select top (1) v.thumbnail_url from dbo.product_variants v
                        where v.product_id = p.product_id and v.thumbnail_url is not null
                        order by v.variant_id asc)
                   ) as imageUrl,
                   (select count_big(*) from dbo.product_variants v
                    where v.product_id = p.product_id and v.status <> 'DELETED') as variantCount
            from dbo.products p
            join dbo.shops s on s.shop_id = p.shop_id
            where p.status <> 'DELETED'
              and ((:tab = 'PENDING' and p.approval_status = 'PENDING' and p.moderation_blocked = 0)
                or (:tab = 'LOCKED' and p.moderation_blocked = 1)
                or (:tab = 'REVIEWED' and p.approval_status <> 'PENDING' and p.moderation_blocked = 0))
              and (:fromDate is null or p.created_at >= :fromDate)
              and (:untilDate is null or p.created_at < :untilDate)
              and (:keyword = ''
                or lower(p.name) like '%' + lower(:keyword) + '%'
                or lower(p.slug) like '%' + lower(:keyword) + '%'
                or lower(s.shop_name) like '%' + lower(:keyword) + '%'
                or exists (select 1 from dbo.product_variants v where v.product_id = p.product_id
                    and (lower(v.sku) like '%' + lower(:keyword) + '%'
                      or lower(v.variant_name) like '%' + lower(:keyword) + '%')))
            order by case when :tab = 'PENDING' then p.created_at end asc,
                     case when :tab <> 'PENDING' then p.updated_at end desc,
                     p.product_id desc
            """, countQuery = """
            select count_big(*)
            from dbo.products p
            join dbo.shops s on s.shop_id = p.shop_id
            where p.status <> 'DELETED'
              and ((:tab = 'PENDING' and p.approval_status = 'PENDING' and p.moderation_blocked = 0)
                or (:tab = 'LOCKED' and p.moderation_blocked = 1)
                or (:tab = 'REVIEWED' and p.approval_status <> 'PENDING' and p.moderation_blocked = 0))
              and (:fromDate is null or p.created_at >= :fromDate)
              and (:untilDate is null or p.created_at < :untilDate)
              and (:keyword = ''
                or lower(p.name) like '%' + lower(:keyword) + '%'
                or lower(p.slug) like '%' + lower(:keyword) + '%'
                or lower(s.shop_name) like '%' + lower(:keyword) + '%'
                or exists (select 1 from dbo.product_variants v where v.product_id = p.product_id
                    and (lower(v.sku) like '%' + lower(:keyword) + '%'
                      or lower(v.variant_name) like '%' + lower(:keyword) + '%')))
            """, nativeQuery = true)
    Page<AdminProductModerationRow> search(
            @Param("tab") String tab,
            @Param("keyword") String keyword,
            @Param("fromDate") java.time.LocalDateTime fromDate,
            @Param("untilDate") java.time.LocalDateTime untilDate,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update p
            set approval_status = 'APPROVED',
                approved_by = :adminId,
                approved_at = :reviewedAt,
                updated_at = :reviewedAt
            output inserted.approved_by, 'PRODUCT_APPROVED', 'PRODUCT', inserted.product_id,
                   N'Admin đã hậu kiểm và duyệt sản phẩm theo lô.', inserted.approved_at
            into dbo.audit_events (actor_id, action, entity_type, entity_id, reason, created_at)
            from dbo.products p
            join dbo.shops s on s.shop_id = p.shop_id
            where p.status <> 'DELETED'
              and p.approval_status = 'PENDING'
              and p.moderation_blocked = 0
              and (:fromDate is null or p.created_at >= :fromDate)
              and (:untilDate is null or p.created_at < :untilDate)
              and (:keyword = ''
                or lower(p.name) like '%' + lower(:keyword) + '%'
                or lower(p.slug) like '%' + lower(:keyword) + '%'
                or lower(s.shop_name) like '%' + lower(:keyword) + '%'
                or exists (select 1 from dbo.product_variants v where v.product_id = p.product_id
                    and (lower(v.sku) like '%' + lower(:keyword) + '%'
                      or lower(v.variant_name) like '%' + lower(:keyword) + '%')))
            """, nativeQuery = true)
    int bulkApprovePending(
            @Param("keyword") String keyword,
            @Param("fromDate") java.time.LocalDateTime fromDate,
            @Param("untilDate") java.time.LocalDateTime untilDate,
            @Param("adminId") Long adminId,
            @Param("reviewedAt") java.time.LocalDateTime reviewedAt);

    long countByStatusNotAndApprovalStatusAndModerationBlockedFalse(
            ProductStatus excludedStatus, ApprovalStatus approvalStatus);

    long countByStatusNotAndModerationBlockedTrue(ProductStatus excludedStatus);

    long countByStatusNotAndApprovalStatusNotAndModerationBlockedFalse(
            ProductStatus excludedStatus, ApprovalStatus approvalStatus);
}
