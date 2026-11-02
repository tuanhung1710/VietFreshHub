# Current merge resolution (2026-11-02)

- Local HEAD: `cc324454bd0c373858f40f064b9c3983c3e9ce08`.
- Incoming MERGE_HEAD and freshly fetched origin/main: `4deeff6c11a346a19e98e15ae25e26797f265e12`.
- The in-progress merge had four modify/delete conflicts, all in tests. Keep the exact local contents of CustomerOrderHistorySummaryTest, CustomerOrderQueryServiceTest, CustomerPaymentHistoryControllerTest and CustomerPaymentHistoryTest.
- The user explicitly chose main behavior where catalog out-of-stock handling and the removed receipt confirmation route differ, and authorized retaining main's deletion of the other legacy tests.
- Retain CustomerHttpHarness because the preserved summary test requires it. Register main's LoginRedirectService in that test context; no production business code was edited during this resolution.
- No unmerged index entries or source conflict markers remain. The existing automatic production merge is preserved, including local payment-label/filter changes.
- Validation: Java 21, cached Maven 3.9.16, offline clean test. Production and test compilation pass. 56 tests run: 55 pass, one fails, no errors or skips.
- Known incompatibility intentionally retained: CustomerOrderHistorySummaryTest.samePersistedCodOrderSurvivesNavigationAcrossAllFivePagesWithoutWrites expects HTTP 200 from `/customer/order-receipts/10/confirmation`; main removed that route, so it returns HTTP 404. Neither the preserved assertion nor main's route behavior was changed to hide this mismatch.
- Full log: `target/merge-verification.log`; reports: `target/surefire-reports`. These tests do not establish complete business equivalence or verify external providers/production SQL Server.
- Resolution is staged, with the merge left uncommitted and unpushed.

---

# Historical merge notes (previous merge; validation below is not the current result)

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
