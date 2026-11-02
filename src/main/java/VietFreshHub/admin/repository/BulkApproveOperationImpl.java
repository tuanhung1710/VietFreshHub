package VietFreshHub.admin.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;

public class BulkApproveOperationImpl implements BulkApproveOperation {

    private static final String BULK_APPROVE_SQL = """
            SET NOCOUNT ON;

            DECLARE @approvedRows TABLE (
                actor_id BIGINT NOT NULL,
                action VARCHAR(100) NOT NULL,
                entity_type VARCHAR(100) NOT NULL,
                entity_id BIGINT NOT NULL,
                reason NVARCHAR(1000) NOT NULL,
                created_at DATETIME2 NOT NULL
            );

            BEGIN TRY
                update p
                set approval_status = 'APPROVED',
                    approved_by = :adminId,
                    approved_at = :reviewedAt,
                    updated_at = :reviewedAt
                output inserted.approved_by,
                       'PRODUCT_APPROVED',
                       'PRODUCT',
                       inserted.product_id,
                       N'Admin đã hậu kiểm và duyệt sản phẩm theo lô.',
                       inserted.approved_at
                into @approvedRows (actor_id, action, entity_type, entity_id, reason, created_at)
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
                          or lower(v.variant_name) like '%' + lower(:keyword) + '%')));

                insert into dbo.audit_events (actor_id, action, entity_type, entity_id, reason, created_at)
                select actor_id, action, entity_type, entity_id, reason, created_at
                from @approvedRows;

                SET NOCOUNT OFF;
                select count(*) as approvedCount from @approvedRows;
            END TRY
            BEGIN CATCH
                SET NOCOUNT OFF;
                THROW;
            END CATCH;
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BulkApproveOperationImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int bulkApprovePending(
            String keyword,
            LocalDateTime fromDate,
            LocalDateTime untilDate,
            Long adminId,
            LocalDateTime reviewedAt) {
        MapSqlParameterSource queryParameters = new MapSqlParameterSource()
                .addValue("keyword", keyword)
                .addValue("fromDate", fromDate)
                .addValue("untilDate", untilDate)
                .addValue("adminId", adminId)
                .addValue("reviewedAt", reviewedAt);

        return jdbcTemplate.queryForObject(BULK_APPROVE_SQL, queryParameters, Integer.class);
    }
}
