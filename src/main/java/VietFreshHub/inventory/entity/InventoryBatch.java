package VietFreshHub.inventory.entity;

import VietFreshHub.common.BusinessCodes;
import VietFreshHub.common.VietnamDatabaseTime;
import VietFreshHub.inventory.enums.BatchStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một lô của một SKU; trạng thái khóa lô tách biệt với việc hết hạn tính từ expiryDate. */
@Entity
@Table(name = "inventory_batches", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class InventoryBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "variant_id")
    private Long variantId;

    /** Tồn thực tế còn nằm trong kho, bao gồm cả phần đã giữ cho đơn. */
    @Column(name = "quantity_on_hand")
    private Integer quantityOnHand;

    /** Phần tồn đã phân bổ cho đơn; chỉ tồn chưa giữ và đủ điều kiện mới được bán tiếp. */
    @Column(name = "reserved_quantity")
    private Integer reservedQuantity;

    @Column(name = "cost_price", precision = 18, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "received_at")
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime receivedAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private BatchStatus status;

    @Column(name = "created_at")
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @Convert(converter = VietnamDatabaseTime.class)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updatedAt;

    /** Tạo mã LOT từ ID lô để hiển thị, không lưu thành một cột dữ liệu riêng. */
    @Transient
    public String getBatchCode() {
        return BusinessCodes.format("LOT", batchId);
    }
}
