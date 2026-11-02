package VietFreshHub.inventory.service;

import org.springframework.security.core.Authentication;

import java.util.List;

/** Giữ hàng sau khi người bán xác nhận đơn; ghi xuất bán khi đơn đủ điều kiện giao hàng. */
public interface StockAllocationService {

    /** Giữ hàng cho dòng đơn đã xác nhận, trả ID các phân bổ và tránh giữ trùng khi gọi lại. */
    List<Long> reserve(Authentication authentication, Long orderItemId);

    /** Xuất bán phần đã giữ khi đơn đủ điều kiện, không trừ kho lần nữa nếu đã bán. */
    void sell(Authentication authentication, Long orderItemId);
}
