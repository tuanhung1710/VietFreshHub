-- Deliberately blocked until the business approves the historical conversion policy.
-- This parameter is VND per OLD POINT, never earning_percent (1% is for future orders).
-- If different periods/types require different rates, replace this conversion with an
-- approved per-row mapping; do not run a uniform rate in that case.
SET XACT_ABORT ON;
DECLARE @ApprovedVndPerLegacyPoint DECIMAL(18,6) = 100;
IF @ApprovedVndPerLegacyPoint IS NULL OR @ApprovedVndPerLegacyPoint <= 0
    THROW 51000, 'Approve and set the historical VND-per-point conversion rate first.', 1;
BEGIN TRANSACTION;
IF EXISTS (SELECT 1 FROM dbo.loyalty_accounts WHERE reward_balance IS NOT NULL OR total_earned_amount IS NOT NULL OR total_redeemed_amount IS NOT NULL)
 OR EXISTS (SELECT 1 FROM dbo.loyalty_transactions WHERE amount IS NOT NULL OR balance_after_amount IS NOT NULL)
    THROW 51000, 'Conversion has already started or VND writes exist; do not overwrite.', 1;
-- Require exact whole dong conversion. No silent rounding that breaks ledger balances.
IF EXISTS (SELECT 1 FROM dbo.loyalty_accounts
    CROSS APPLY (VALUES (legacy_points_balance), (legacy_total_earned_points), (legacy_total_redeemed_points)) v(n)
    WHERE v.n * @ApprovedVndPerLegacyPoint <> ROUND(v.n * @ApprovedVndPerLegacyPoint, 0))
 OR EXISTS (SELECT 1 FROM dbo.loyalty_transactions
    CROSS APPLY (VALUES (legacy_points), (legacy_balance_after_points)) v(n)
    WHERE v.n * @ApprovedVndPerLegacyPoint <> ROUND(v.n * @ApprovedVndPerLegacyPoint, 0))
    THROW 51000, 'Conversion creates fractional dong; approve a reconciled per-row mapping.', 1;
UPDATE dbo.loyalty_accounts SET
    reward_balance = legacy_points_balance * @ApprovedVndPerLegacyPoint,
    total_earned_amount = legacy_total_earned_points * @ApprovedVndPerLegacyPoint,
    total_redeemed_amount = legacy_total_redeemed_points * @ApprovedVndPerLegacyPoint;
UPDATE dbo.loyalty_transactions SET
    amount = legacy_points * @ApprovedVndPerLegacyPoint,
    balance_after_amount = legacy_balance_after_points * @ApprovedVndPerLegacyPoint;
-- No order, coupon, payment, note, timestamp or historical earning recalculation.
COMMIT;
