# Current merge resolution (2026-11-03)

- Local starting commit: `165d6993845dc0d17ba030a4f1a5b754126476d5`; fetched main: `97c53c846086d07a57c71596ffde69d49c0269aa`.
- Started from a clean working tree on Tuan-Hung. Merge is left staged without committing or pushing.
- Resolved five conflicts: CheckoutController, checkout.html, orders.html, payment-history.html, and the modified/deleted order-detail.html.
- Retained local buy-now checkout, isolated cart contents, reorder eligibility, payment display/status filters, redesigned history screens and pagination.
- Retained main coupon and reward calculations, confirmation validation, and other automatically merged features. As previously authorized, retain main's removal of aggregate receipt detail and its pre-confirmation cancellation restrictions. These are existing differences between the branches, so this merge does not claim identical observable behavior to both inputs.
- Combined checkout request parameters and draft routing. Coupon/reward preview uses the current buy-now draft; coupon eligibility reuses main's existing calculation and scope/usage rules with the selected variant and quantity, without reading or mutating cart lines.
- Kept local shop-order payment links and displayed amounts. Links that previously opened the removed aggregate detail now open main's order search.
- Kept the four previously protected test files unchanged. Updated test wiring for removed controller/new coupon and loyalty dependencies, and checkout render fixtures. Receipt-detail UI checks now expect the retired route to return 404.
- Added BuyNowCouponTest for quantity-based eligibility/discounts and absence of cart reads/writes. Production and test sources compile with Java 21 and cached Maven 3.9.16.
- Final clean-test result: 93 run, 87 passed, 6 failed, no errors or skips. Buy-now checkout, buy-now coupon eligibility, checkout rendering, cart rendering, reorder eligibility, purchase-history rendering and shop-order detail rendering pass.
- All six failures are in the unchanged protected test files: two summary tests request removed receipt-detail routes (404), three payment rendering tests expect links to that removed route, and one summary fixture expects 11 IDs to exceed the old page limit while local PageSizes supports up to 50; it reaches the fixture's null ShopOrderRepository. The history-summary implementation/page-size rule was not changed by this conflict resolution.
- Full log: `target/merge-current-verification.log`; reports: `target/surefire-reports`. The complete Maven test command fails because of those six preserved assertions. External provider integrations and production database runtime are not exercised by this merge validation.

---

# Previous merge resolution (2026-11-02)

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
