package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.dto.DailyCodSummary;
import VietFreshHub.delivery.dto.DeliveryHistoryFilter;
import VietFreshHub.delivery.dto.DeliveryHistoryPage.Option;
import VietFreshHub.delivery.dto.DeliveryHistoryRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeliveryStaffHistoryRepository {
    private final EntityManager entityManager;

    private static final String HISTORY = """
            with assignment_data as (
                select a.assignment_id, a.delivery_id, a.delivery_staff_id, a.status as assignment_status, a.accepted_at,
                       d.status as current_status, d.delivered_at, d.picked_up_at, d.failure_reason,
                       so.total_amount, so.shop_id,
                       o.order_id, o.order_code, s.shop_name, oa.recipient_name, oa.phone,
                       coalesce(nullif(oa.formatted_address, ''), oa.address_line) as delivery_address,
                       failure.created_at as failed_at, failure.history_id as failed_history_id,
                       case when not exists (
                           select 1 from dbo.delivery_status_history h
                           where h.delivery_id = d.delivery_id and h.status = 'FAILED'
                             and (h.note is null or charindex('SHOP_GOODS_RETURN_CONFIRMED assignmentId=', h.note) <> 1)
                       ) then 1 else 0 end as missing_failure_history,
                       ownership.only_attempt, payment.method_count, payment.has_cod, payment.method_name,
                       case when exists (
                           select 1 from dbo.delivery_status_history h
                           where h.delivery_id = d.delivery_id and h.status = 'DELIVERED'
                             and h.changed_by = a.delivery_staff_id and h.created_at >= d.delivered_at
                             and (next_attempt.started_at is null or h.created_at < next_attempt.started_at)
                       ) then 1 else 0 end as success_history,
                       case when exists (
                           select 1 from dbo.delivery_status_history h
                           where h.delivery_id = d.delivery_id and h.status = 'DELIVERED'
                             and h.changed_by is not null and h.changed_by <> a.delivery_staff_id
                             and h.created_at >= d.delivered_at
                       ) then 1 else 0 end as conflicting_success,
                       case when a.status = 'COMPLETED' and exists (
                           select 1 from dbo.delivery_status_history h
                           where h.delivery_id = d.delivery_id and h.status = 'FAILED'
                             and h.changed_by is not null
                             and h.note = concat('SHOP_GOODS_RETURN_CONFIRMED assignmentId=', a.assignment_id)
                       ) then 'CONFIRMED'
                       when a.status = 'ACCEPTED' and d.status = 'FAILED' and d.picked_up_at is not null
                         and d.delivered_at is null and nullif(ltrim(rtrim(d.failure_reason)), '') is not null
                         and d.redelivery_requested_at is null and d.redelivery_scheduled_at is null
                       then 'PENDING' else 'UNKNOWN' end as return_state,
                       next_attempt.started_at as next_started_at,
                       coalesce(a.accepted_at, a.assigned_at) as started_at
                from dbo.delivery_assignments a
                join dbo.deliveries d on d.delivery_id = a.delivery_id
                join dbo.shop_orders so on so.shop_order_id = d.shop_order_id
                join dbo.orders o on o.order_id = so.order_id
                join dbo.shops s on s.shop_id = so.shop_id
                left join dbo.order_addresses oa on oa.order_id = o.order_id
                cross apply (
                    select case when not exists (
                        select 1 from dbo.delivery_assignments b
                        where b.delivery_id = a.delivery_id
                          and b.status in ('ACCEPTED', 'COMPLETED', 'CANCELLED')
                          and (b.accepted_at is not null or b.status = 'COMPLETED')
                          and (b.delivery_staff_id <> a.delivery_staff_id
                            or coalesce(b.accepted_at, b.assigned_at) <> coalesce(a.accepted_at, a.assigned_at))
                    ) then 1 else 0 end as only_attempt
                ) ownership
                outer apply (
                    select min(coalesce(b.accepted_at, b.assigned_at)) as started_at
                    from dbo.delivery_assignments b
                    where b.delivery_id = a.delivery_id
                      and b.status in ('ACCEPTED', 'COMPLETED', 'CANCELLED')
                      and (b.accepted_at is not null or b.status = 'COMPLETED')
                      and coalesce(b.accepted_at, b.assigned_at) > coalesce(a.accepted_at, a.assigned_at)
                ) next_attempt
                outer apply (
                    select top (1) h.history_id, h.created_at
                    from dbo.delivery_status_history h
                    where h.delivery_id = d.delivery_id and h.status = 'FAILED'
                      and (h.note is null or charindex('SHOP_GOODS_RETURN_CONFIRMED assignmentId=', h.note) <> 1)
                      and (h.changed_by = a.delivery_staff_id or (h.changed_by is null and ownership.only_attempt = 1))
                      and h.created_at >= coalesce(a.accepted_at, a.assigned_at)
                      and (next_attempt.started_at is null or h.created_at < next_attempt.started_at)
                    order by h.created_at, h.history_id
                ) failure
                outer apply (
                    select count(distinct pm.method_code) as method_count, min(pm.name) as method_name,
                           coalesce(max(case when pm.method_code = 'COD' then 1 else 0 end), 0) as has_cod
                    from dbo.payments p join dbo.payment_methods pm on pm.payment_method_id = p.payment_method_id
                    where p.order_id = o.order_id
                ) payment
                where a.delivery_staff_id = :staffId
                  and a.status in ('ACCEPTED', 'COMPLETED', 'CANCELLED')
                  and (a.accepted_at is not null or a.status = 'COMPLETED')
                  and not exists (
                      select 1 from dbo.delivery_assignments duplicate
                      where duplicate.delivery_id = a.delivery_id
                        and duplicate.delivery_staff_id = a.delivery_staff_id
                        and duplicate.status in ('ACCEPTED', 'COMPLETED', 'CANCELLED')
                        and (duplicate.accepted_at is not null or duplicate.status = 'COMPLETED')
                        and coalesce(duplicate.accepted_at, duplicate.assigned_at) = coalesce(a.accepted_at, a.assigned_at)
                        and (
                            case duplicate.status when 'COMPLETED' then 0 when 'ACCEPTED' then 1 else 2 end
                              < case a.status when 'COMPLETED' then 0 when 'ACCEPTED' then 1 else 2 end
                            or (duplicate.status = a.status and duplicate.assignment_id > a.assignment_id)
                        )
                  )
            ), outcomes as (
                select assignment_data.*,
                       case when failed_history_id is not null then 'FAILED'
                            when current_status = 'DELIVERED' and assignment_status = 'COMPLETED'
                              and ((delivered_at >= started_at and (next_started_at is null or delivered_at < next_started_at))
                                or (delivered_at is null and only_attempt = 1 and accepted_at is not null))
                            then 'DELIVERED'
                            when current_status = 'FAILED' and only_attempt = 1 and accepted_at is not null
                              and missing_failure_history = 1
                              and assignment_status in ('ACCEPTED', 'COMPLETED') then 'FAILED'
                       end as outcome,
                       case when conflicting_success = 0
                              and (success_history = 1 or (only_attempt = 1 and accepted_at is not null))
                       then 1 else 0 end as attribution_verified
                from assignment_data
            ), history as (
                select outcomes.*,
                       case when outcome = 'DELIVERED' then delivered_at else failed_at end as result_at
                from outcomes
            )
            """;

    public DailyCodSummary dailyCod(Long staffId, LocalDate date, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("staffId", staffId);
        parameters.put("start", start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        String endCondition = "";
        if (end != null) {
            parameters.put("end", end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            endCondition = " and delivered_at < convert(datetime2(0), :end, 126)";
        }
        String eligible = """
                from history where outcome = 'DELIVERED' and current_status = 'DELIVERED'
                  and delivered_at >= convert(datetime2(0), :start, 126) and has_cod = 1
                """ + endCondition;
        Object[] values = (Object[]) query(HISTORY + """
                select coalesce(sum(eligible.total_amount), 0), count(*)
                from (select delivery_id, max(total_amount) as total_amount
                """ + eligible + """
                  and attribution_verified = 1 and method_count = 1 group by delivery_id) eligible
                """, parameters).getSingleResult();
        long unresolved = ((Number) query(HISTORY + """
                select count(distinct delivery_id) from history
                where current_status = 'DELIVERED' and assignment_status = 'COMPLETED' and has_cod = 1
                  and delivered_at >= convert(datetime2(0), :start, 126)
                """ + endCondition + """
                  and delivered_at >= started_at
                  and (next_started_at is null or delivered_at < next_started_at)
                  and (outcome is null or outcome <> 'DELIVERED' or attribution_verified = 0 or method_count <> 1)
                """, parameters).getSingleResult()).longValue();
        return new DailyCodSummary(date, decimal(values[0]), ((Number) values[1]).longValue(), unresolved);
    }

    public Page<DeliveryHistoryRow> findHistory(Long staffId, DeliveryHistoryFilter filter,
                                               LocalDateTime from, LocalDateTime until, Pageable pageable) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("staffId", staffId);
        String where = filters(filter, from, until, parameters);
        long count = ((Number) query(HISTORY + "select count(*) from history " + where, parameters)
                .getSingleResult()).longValue();
        List<?> values = query(HISTORY + selectColumns() + where + ordering(filter.getSort()), parameters)
                .setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize())
                .getResultList();
        List<DeliveryHistoryRow> rows = values.stream().map(value -> toRow((Object[]) value)).toList();
        return new PageImpl<>(rows, pageable, count);
    }

    public Optional<DeliveryHistoryRow> findDetail(Long staffId, Long assignmentId) {
        List<?> rows = query(HISTORY + selectColumns()
                + "where outcome is not null and assignment_id = :assignmentId",
                Map.of("staffId", staffId, "assignmentId", assignmentId)).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(toRow((Object[]) rows.getFirst()));
    }

    public List<Option> shops(Long staffId) {
        return options(HISTORY + """
                select distinct cast(shop_id as varchar(20)), shop_name from history
                where outcome is not null order by shop_name, cast(shop_id as varchar(20))
                """, staffId);
    }

    public List<Option> paymentMethods(Long staffId) {
        return options(HISTORY + """
                select distinct pm.method_code, pm.name
                from dbo.payment_methods pm join dbo.payments p on p.payment_method_id = pm.payment_method_id
                where exists (select 1 from history h where h.outcome is not null and h.order_id = p.order_id)
                order by pm.name, pm.method_code
                """, staffId);
    }

    public long countAll(Long staffId) {
        return ((Number) query(HISTORY + "select count(*) from history where outcome is not null",
                Map.of("staffId", staffId)).getSingleResult()).longValue();
    }

    public long countUnresolved(Long staffId) {
        return ((Number) query(HISTORY + """
                select count(*) from history
                where (outcome is null and (current_status in ('DELIVERED', 'FAILED') or assignment_status = 'COMPLETED'))
                   or (outcome = 'FAILED' and current_status = 'DELIVERED' and assignment_status = 'COMPLETED'
                     and delivered_at >= started_at and (next_started_at is null or delivered_at < next_started_at))
                """, Map.of("staffId", staffId)).getSingleResult()).longValue();
    }

    private String filters(DeliveryHistoryFilter filter, LocalDateTime from, LocalDateTime until,
                           Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder("where outcome is not null");
        if (!filter.getKeyword().isEmpty()) {
            where.append("""
                     and (lower(order_code) like :keyword escape '\\'
                       or lower(recipient_name) like :keyword escape '\\'
                       or lower(phone) like :keyword escape '\\'
                       or lower(shop_name) like :keyword escape '\\')
                    """);
            String escaped = filter.getKeyword().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
            parameters.put("keyword", "%" + escaped + "%");
        }
        if (!"ALL".equals(filter.getStatus())) {
            where.append(" and outcome = :outcome");
            parameters.put("outcome", filter.getStatus());
        }
        if (from != null) {
            where.append(" and result_at >= convert(datetime2(0), :fromTime, 126)");
            parameters.put("fromTime", from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (until != null) {
            where.append(" and result_at < convert(datetime2(0), :untilTime, 126)");
            parameters.put("untilTime", until.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (filter.getShopId() != null) {
            where.append(" and shop_id = :shopId");
            parameters.put("shopId", filter.getShopId());
        }
        if (!filter.getPaymentMethod().isEmpty()) {
            where.append("""
                     and exists (select 1 from dbo.payments p join dbo.payment_methods pm
                       on pm.payment_method_id = p.payment_method_id
                       where p.order_id = history.order_id and pm.method_code = :paymentMethod)
                    """);
            parameters.put("paymentMethod", filter.getPaymentMethod());
        }
        if (filter.getMinAmount() != null) {
            BigDecimal minimum = filter.getMinAmount().setScale(2, RoundingMode.CEILING);
            if (minimum.precision() > 18) {
                where.append(" and 1 = 0");
            } else {
                where.append(" and total_amount >= :minAmount");
                parameters.put("minAmount", minimum);
            }
        }
        if (filter.getMaxAmount() != null) {
            BigDecimal maximum = filter.getMaxAmount().setScale(2, RoundingMode.FLOOR);
            if (maximum.precision() <= 18) {
                where.append(" and total_amount <= :maxAmount");
                parameters.put("maxAmount", maximum);
            }
        }
        if (!"ALL".equals(filter.getReturnStatus())) {
            where.append(" and outcome = 'FAILED' and return_state = :returnState");
            parameters.put("returnState", filter.getReturnStatus());
        }
        return where.toString();
    }

    private String ordering(String sort) {
        return switch (sort) {
            case "NEWEST" -> " order by case when result_at is null then 1 else 0 end, result_at desc, delivery_id desc, assignment_id desc";
            case "OLDEST" -> " order by case when result_at is null then 1 else 0 end, result_at asc, delivery_id asc, assignment_id asc";
            case "AMOUNT_HIGH" -> " order by total_amount desc, delivery_id desc, assignment_id desc";
            case "AMOUNT_LOW" -> " order by total_amount asc, delivery_id asc, assignment_id asc";
            default -> throw new IllegalArgumentException("Lựa chọn sắp xếp không hợp lệ.");
        };
    }

    private String selectColumns() {
        return """
                select assignment_id, delivery_id, order_code, shop_id, shop_name, recipient_name, phone,
                       delivery_address, outcome, total_amount,
                       convert(varchar(19), result_at, 126) as result_time,
                       case when method_count = 1 then method_name
                            when method_count = 0 then N'Chưa có dữ liệu thanh toán'
                            else N'Nhiều phương thức (cần đối soát)' end,
                       case when outcome = 'FAILED' then return_state else 'NONE' end, attribution_verified,
                       convert(varchar(19), accepted_at, 126) as accepted_time,
                       convert(varchar(19), coalesce(
                           case when picked_up_at >= started_at
                             and (next_started_at is null or picked_up_at < next_started_at)
                             and (result_at is null or picked_up_at <= result_at) then picked_up_at end,
                           (select min(h.created_at) from dbo.delivery_status_history h
                            where h.delivery_id = history.delivery_id and h.status = 'PICKED_UP'
                              and (h.changed_by = history.delivery_staff_id
                                or (h.changed_by is null and history.only_attempt = 1))
                              and h.created_at >= history.started_at
                              and (history.next_started_at is null or h.created_at < history.next_started_at)
                              and (history.result_at is null or h.created_at <= history.result_at))
                       ), 126) as pickup_time,
                       case when outcome = 'FAILED' and current_status = 'FAILED'
                         and next_started_at is null then failure_reason end as attempt_failure_reason,
                       case when method_count = 1 and has_cod = 1 then total_amount else 0 end as cash_to_collect
                from history
                """;
    }

    private Query query(String sql, Map<String, ?> parameters) {
        Query query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        return query;
    }

    private List<Option> options(String sql, Long staffId) {
        List<?> rows = query(sql, Map.of("staffId", staffId)).getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new Option(row[0].toString(), (String) row[1]);
        }).toList();
    }

    private DeliveryHistoryRow toRow(Object[] row) {
        return new DeliveryHistoryRow(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                (String) row[2], ((Number) row[3]).longValue(), (String) row[4], (String) row[5],
                (String) row[6], (String) row[7], (String) row[8], decimal(row[9]), dateTime(row[10]),
                (String) row[11], (String) row[12], ((Number) row[13]).intValue() == 1,
                dateTime(row[14]), dateTime(row[15]), (String) row[16], decimal(row[17]));
    }

    private LocalDateTime dateTime(Object value) {
        return value == null ? null : LocalDateTime.parse((String) value);
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }
}
