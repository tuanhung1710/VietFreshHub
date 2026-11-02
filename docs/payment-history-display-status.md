# Payment History display status — 2026-11-03

## Scope
Read-only Payment History mapping, filtering and rendering. No entity, enum, schema, migration, callback, retry, cancellation, checkout, cart, delivery or export implementation was changed. Stored payment status, amount, reference and timestamps remain intact.

## Files
- src/main/java/VietFreshHub/payment/dto/PaymentHistoryDisplay.java: labels and shared HQL display expression.
- src/main/java/VietFreshHub/payment/dto/PaymentHistoryRow.java: separate displayStatus, preserve persisted status and order references.
- src/main/java/VietFreshHub/payment/dto/PaymentHistoryFilter.java: five display filter values.
- src/main/java/VietFreshHub/payment/repository/CustomerPaymentReadRepository.java: select/filter the same expression before pagination; no join that duplicates payments.
- src/main/java/VietFreshHub/payment/service/impl/CustomerPaymentHistoryServiceImpl.java: validate display filters.
- src/main/java/VietFreshHub/payment/controller/CustomerPaymentHistoryController.java: local display filter labels.
- src/main/resources/templates/customer/payment-history.html: display badge and refund explanation.
- src/main/resources/static/css/customer-orders.css: refund badge scoped to Payment History.
- Tests: CustomerPaymentHistoryTest, CustomerPaymentHistoryControllerTest, CustomerOrderHistorySummaryTest.

## Mapping
Previously badges and filters primarily followed raw Payment.status, including PROCESSING and completed-refund labels.
Now:
- PENDING/PROCESSING -> PENDING (Chưa thanh toán), unless all related shop orders have persisted cancellation status and cancellation timestamps -> CANCELLED.
- PAID -> PAID; no delivery state implies collection.
- Online VNPAY/BANK_TRANSFER PAID -> REFUND_PROCESSING only if amount is positive and equals the receipt total, all related shop orders are cancelled with persisted timestamps, all are PAID, and none has a confirmation timestamp.
- FAILED -> FAILED per attempt. Retry creates a new payment record in the current gateway implementation; it does not erase failed attempts.
- CANCELLED -> CANCELLED per recorded attempt.
- Existing legacy REFUNDED/PARTIALLY_REFUNDED records are presented as REFUND_PROCESSING instead of claiming money was returned. Their stored status is unchanged.

## Limits
- Partial shop cancellation does not convert the whole PAID record to a full-refund display.
- Missing cancellation timestamps, confirmed shop orders, unknown online method codes or mismatched amounts do not qualify for the new derived refund state. Such cases retain PAID pending sufficient evidence; no underlying rules were expanded.
- This change does not infer gateway failure or success from timeouts or delivery status.
- Refund export state is deliberately not read: exporting Excel cannot change the badge. No end-to-end export operation was executed.
- H2 validates the read queries; a live SQL Server/browser workflow was not exercised.

## Validation
49 targeted tests passed: 25 service/DTO tests, 22 MVC/render tests and 2 real H2 read-query tests. Coverage includes customer isolation, canonical order lookup, multi-shop payment uniqueness, partial versus full cancellation, raw PAID preservation, COD pending cancellation, COD paid preservation and display filters. Fixture changes run in a transaction rolled back after the test. This is targeted regression coverage, not a claim that the complete repository suite passed.

## Follow-up: per-shop presentation
Payment History now pages over (payment attempt, shop order) pairs. COD display status reads the shop payment status: PAID takes precedence, cancellation with a stored timestamp follows, otherwise PENDING. The parent payment status is preserved and does not imply all shops have collected cash. Online attempts retain their recorded outcome; eligible cancellation/refund mapping applies only to the cancelled shop. Each card shows the shop total, not a repeated receipt payment amount. An online attempt remains one stored transaction even when represented under multiple shop orders. Status and canonical-code filtering happen before pagination. Shared COD paidAt is not presented as a shop-specific collection timestamp because the current projection lacks that timestamp.
