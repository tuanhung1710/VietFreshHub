# Merge main into Tuan-Hung

## Sources

- Local starting commit: `6eab9dcfedc98c93f151dee72d531ace45d9858f`.
- Fetched `origin/main`: `5a452df5263da0fb093e6ddfb2b63d1e38958808`.
- The working tree was clean before the merge. No reset, force push, or remote update was performed.

## Business decisions confirmed by the user

The branches have incompatible business rules, so preserving every observable behavior of both at the same URL is impossible. The user explicitly selected main for these two differences:

1. Cancellation follows the individual shop order, before shop confirmation, including main's payment reconciliation restrictions. The old aggregate cancellation endpoint cannot bypass that rule.
2. Active products may sell while awaiting post-review unless an administrator blocks them. `moderationBlocked` governs catalog visibility and purchasing, including related-product suggestions. Approval alone no longer blocks sales.

## Integration

- `/customer/orders` retains main's shop-order IDs, history, tracking, cancellation, reorder, refunds and VNPAY retry flow. The copied shop-order classes use distinct names to avoid duplicate Spring beans and incompatible DTO constructors.
- `/customer/order-receipts` retains local aggregate purchase history, detail snapshots, confirmation, tracking, payment-history links and aggregate reorder. The shop-order list links to this history. Aggregate views direct cancellation to the main shop-order flow.
- Local checkout orchestration and helpers remain in place. Main's BANK_TRANSFER/VNPAY availability, pending payment states, provider references, initial redirect and duplicate-request payment recovery are integrated into them. COD confirmation and infrastructure-failure recovery remain available.
- Local catalog categories, available-stock filtering, out-of-stock details, live stock updates, cart mapping and recommendation features remain. The chosen moderation rule is applied consistently to catalog list, detail, batch cart checks and related-product selection.
- Local inventory reservation validation, deterministic batch locking, refresh after locking, FEFO allocation and detailed shortage exceptions remain. Main's reservation-state and retry checks are added for VNPAY.
- Repository methods and DTO overloads support both aggregate receipt reads and shop-order reads. Main's other automatically merged feature updates remain in the merge.
- Seller application document preview scripts from main are combined with the local shared header/footer.

## Validation

- Java 21, cached Maven 3.9.16, offline `mvn test`.
- Result: 387 Java tests discovered, 360 passed, 27 SQL Server integration tests skipped, no failures or errors; 16 browser stock tests passed.
- Regression tests include COD and VNPAY checkout, duplicate confirmation, failure recovery, ownership, aggregate/shop ID separation, cancellation routing, main's cancellation restrictions, catalog post-review eligibility, stock and payment views.
- SQL Server integration tests require `TV3_TEST_JDBC_URL`; they are skipped when it is absent. Passing unit/H2 tests does not prove production SQL Server concurrency or provider integration.
- JavaScript stock tests use the existing Playwright dependency from the installed Codex runtime; no project dependency was installed.
- Detailed local reports are in `target/surefire-reports`, `target/merge-java-tests.log` and `target/merge-js-tests.log`.

The merge is left resolved and staged for review, without creating a commit or pushing it.
