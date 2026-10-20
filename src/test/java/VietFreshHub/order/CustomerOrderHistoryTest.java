package VietFreshHub.order;

import VietFreshHub.exception.CustomerOrderException;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.impl.CustomerOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerOrderHistoryTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final CustomerOrderServiceImpl service = new CustomerOrderServiceImpl(orders, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);
    final CustomerOrderFilterRequest filter = new CustomerOrderFilterRequest();
    void emptyResult() { when(orders.findHistory(anyLong(), any(), anyString(), any(), any(), anyBoolean(), any())).thenReturn(Page.empty()); }

    @Test void convertsInclusiveVietnamDateToHalfOpenUtcRangeAndTrimsKeyword() {
        emptyResult(); filter.setFromDate(LocalDate.of(2026,10,20)); filter.setToDate(LocalDate.of(2026,10,20)); filter.setKeyword("  VF-123  ");
        service.history(1L, filter);
        verify(orders).findHistory(eq(1L), isNull(), eq("VF-123"), eq(LocalDateTime.of(2026,10,19,17,0)),
                eq(LocalDateTime.of(2026,10,20,17,0)), eq(false), argThat(p -> p.getPageNumber()==0 && p.getPageSize()==10
                        && p.getSort().equals(Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("orderId")))));
    }
    @Test void maximumDateDoesNotBindYear10000() {
        emptyResult(); filter.setToDate(LocalDate.of(9999,12,31)); service.history(1L, filter);
        verify(orders).findHistory(eq(1L), isNull(), eq(""), isNull(), isNull(), eq(false), any());
    }
    @Test void minimumDateDoesNotBindYearZero() {
        emptyResult(); filter.setFromDate(LocalDate.of(1,1,1)); service.history(1L, filter);
        verify(orders).findHistory(eq(1L), isNull(), eq(""), eq(LocalDateTime.of(1,1,1,0,0)), isNull(), eq(false), any());
    }
    @Test void deliveringUsesChildStatusRatherThanAnInvalidParentEnum() {
        emptyResult(); filter.setStatus("OUT_FOR_DELIVERY"); service.history(1L, filter);
        verify(orders).findHistory(eq(1L), isNull(), eq(""), isNull(), isNull(), eq(true), any());
    }
    @ParameterizedTest @ValueSource(strings={"PROCESSING","PARTIALLY_COMPLETED","INVALID"})
    void rejectsRemovedOrInvalidStatus(String status) {
        filter.setStatus(status); assertThrows(CustomerOrderException.class, () -> service.history(1L, filter)); verifyNoInteractions(orders);
    }
    @Test void rejectsReversedDateRange() {
        filter.setFromDate(LocalDate.of(2026,10,21)); filter.setToDate(LocalDate.of(2026,10,20));
        assertThrows(CustomerOrderException.class, () -> service.history(1L, filter)); verifyNoInteractions(orders);
    }
    @ParameterizedTest @ValueSource(ints={0,10001})
    void rejectsPageOutsideAllowedRange(int page) {
        filter.setPage(page); assertThrows(CustomerOrderException.class, () -> service.history(1L, filter)); verifyNoInteractions(orders);
    }
    @Test void clampsMissingPageToLastExistingPage() {
        filter.setPage(100);
        var row = new OrderListRow(10L,"VF-10",Order.Status.PENDING,"UNPAID",BigDecimal.ONE,LocalDateTime.now());
        when(orders.findHistory(anyLong(), any(), anyString(), any(), any(), anyBoolean(), argThat(p -> p!=null && p.getPageNumber()==99)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(99,10), 11));
        var last = new PageImpl<>(List.of(row), PageRequest.of(1,10), 11);
        when(orders.findHistory(anyLong(), any(), anyString(), any(), any(), anyBoolean(), argThat(p -> p!=null && p.getPageNumber()==1))).thenReturn(last);
        assertSame(last, service.history(1L, filter));
    }
    @Test void emptyResultsReturnFirstPage() {
        emptyResult(); filter.setPage(8); assertEquals(0, service.history(1L, filter).getNumber());
    }
}
