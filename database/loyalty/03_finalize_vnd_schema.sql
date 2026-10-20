SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS (SELECT 1 FROM dbo.loyalty_accounts WHERE reward_balance IS NULL OR total_earned_amount IS NULL OR total_redeemed_amount IS NULL)
 OR EXISTS (SELECT 1 FROM dbo.loyalty_transactions WHERE amount IS NULL OR balance_after_amount IS NULL)
    THROW 51000, 'Legacy conversion is incomplete; application must remain stopped.', 1;
ALTER TABLE dbo.loyalty_accounts ALTER COLUMN reward_balance DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.loyalty_accounts ALTER COLUMN total_earned_amount DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.loyalty_accounts ALTER COLUMN total_redeemed_amount DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.loyalty_transactions ALTER COLUMN amount DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.loyalty_transactions ALTER COLUMN balance_after_amount DECIMAL(18,2) NOT NULL;
-- New application writes do not populate legacy fields; retain them for audit.
ALTER TABLE dbo.loyalty_accounts ALTER COLUMN legacy_points_balance INT NULL;
ALTER TABLE dbo.loyalty_accounts ALTER COLUMN legacy_total_earned_points INT NULL;
ALTER TABLE dbo.loyalty_accounts ALTER COLUMN legacy_total_redeemed_points INT NULL;
ALTER TABLE dbo.loyalty_transactions ALTER COLUMN legacy_points INT NULL;
ALTER TABLE dbo.loyalty_transactions ALTER COLUMN legacy_balance_after_points INT NULL;
ALTER TABLE dbo.loyalty_accounts ADD CONSTRAINT CK_loyalty_vnd_balances CHECK (
    reward_balance >= 0 AND reward_balance = FLOOR(reward_balance)
    AND total_earned_amount >= 0 AND total_earned_amount = FLOOR(total_earned_amount)
    AND total_redeemed_amount >= 0 AND total_redeemed_amount = FLOOR(total_redeemed_amount));
ALTER TABLE dbo.loyalty_transactions ADD CONSTRAINT CK_loyalty_vnd_transactions CHECK (
    amount = FLOOR(amount) AND balance_after_amount >= 0 AND balance_after_amount = FLOOR(balance_after_amount)
    AND (transaction_type <> 'EARN' OR amount > 0) AND (transaction_type <> 'REDEEM' OR amount < 0));
-- Fail instead of deleting any duplicate historical processing records.
CREATE UNIQUE INDEX UX_loyalty_vnd_earn ON dbo.loyalty_transactions(loyalty_account_id, order_id)
    WHERE transaction_type = 'EARN' AND order_id IS NOT NULL;
CREATE UNIQUE INDEX UX_loyalty_vnd_redeem ON dbo.loyalty_transactions(loyalty_account_id, order_id)
    WHERE transaction_type = 'REDEEM' AND order_id IS NOT NULL;
COMMIT;
