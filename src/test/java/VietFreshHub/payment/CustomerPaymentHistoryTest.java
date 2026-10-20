package VietFreshHub.payment;

import VietFreshHub.payment.dto.*;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.CustomerPaymentReadRepository;
import VietFreshHub.payment.service.impl.CustomerPaymentHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerPaymentHistoryTest {
    final CustomerPaymentReadRepository payments = mock(CustomerPaymentReadRepository.class);
    final CustomerPaymentHistoryServiceImpl service = new CustomerPaymentHistoryServiceImpl(payments);
    final PaymentHistoryFilter filter = new PaymentHistoryFilter();
    void empty() { when(payments.findHistory(anyLong(), any(), anyString(), any(), any(), any())).thenReturn(Page.empty()); }
    PaymentHistoryRow row(Payment.Status status) {
        return new PaymentHistoryRow(1L, 10L, "VF-10", "COD", "COD", BigDecimal.TEN,
                status, LocalDateTime.of(2026,10,20,3,0), null);
    }
    @Test void bindsCustomerAndInclusiveVietnamDatesWithStablePaging() {
        empty(); filter.setFromDate(LocalDate.of(2026,10,20)); filter.setToDate(LocalDate.of(2026,10,20));
        filter.setKeyword(" VF-10 "); filter.setStatus("PAID"); service.history(7L, filter);
        verify(payments).findHistory(eq(7L), eq(Payment.Status.PAID), eq("VF-10"),
                eq(LocalDateTime.of(2026,10,19,17,0)), eq(LocalDateTime.of(2026,10,20,17,0)),
                argThat(p -> p.getPageSize()==10 && p.getSort().equals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("paymentId")))));
    }
    @Test void databaseDateExtremesDoNotBindUnsupportedYears() {
        empty(); filter.setFromDate(LocalDate.of(1,1,1)); filter.setToDate(LocalDate.of(9999,12,31)); service.history(7L, filter);
        verify(payments).findHistory(eq(7L), isNull(), eq(""), eq(LocalDateTime.of(1,1,1,0,0)), isNull(), any());
    }
    @Test void unknownStatusDoesNotQueryDatabase() {
        filter.setStatus("VERIFIED"); assertThrows(IllegalArgumentException.class, () -> service.history(7L, filter)); verifyNoInteractions(payments);
    }
    @ParameterizedTest @ValueSource(ints={0,10001})
    void invalidPagesDoNotQueryDatabase(int page) {
        filter.setPage(page); assertThrows(IllegalArgumentException.class, () -> service.history(7L, filter)); verifyNoInteractions(payments);
    }
    @Test void reversedDatesAreRejected() {
        filter.setFromDate(LocalDate.of(2026,10,21)); filter.setToDate(LocalDate.of(2026,10,20));
        assertThrows(IllegalArgumentException.class, () -> service.history(7L, filter)); verifyNoInteractions(payments);
    }
    @Test void oversizedKeywordIsRejected() {
        filter.setKeyword("a".repeat(101)); assertThrows(IllegalArgumentException.class, () -> service.history(7L, filter)); verifyNoInteractions(payments);
    }
    @Test void missingCustomerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.history(null, filter)); verifyNoInteractions(payments);
    }
    @Test void emptyHistoryReturnsFirstPage() {
        empty(); filter.setPage(9); assertEquals(0, service.history(7L, filter).getNumber());
    }
    @Test void pageBeyondLastReturnsLastPage() {
        filter.setPage(999);
        var last = new PageImpl<>(List.of(row(Payment.Status.PENDING)), PageRequest.of(1,10), 11);
        when(payments.findHistory(anyLong(), any(), anyString(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(998,10), 11), last);
        assertSame(last, service.history(7L, filter));
    }
    @Test void codPendingDoesNotClaimMoneyWasCollected() {
        assertEquals("Chờ thu tiền", row(Payment.Status.PENDING).statusLabel());
        assertEquals("Đã thanh toán", row(Payment.Status.PAID).statusLabel());
        assertEquals(LocalDateTime.of(2026,10,20,10,0), row(Payment.Status.PENDING).createdAtLocal());
        assertNull(row(Payment.Status.PENDING).paidAtLocal());
    }
}
