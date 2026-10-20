-- Alternative to 01/02/03, ONLY for a database without Loyalty tables.
-- Requires the existing dbo.users(user_id) and dbo.orders(order_id) tables.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.loyalty_accounts', N'U') IS NOT NULL OR OBJECT_ID(N'dbo.loyalty_transactions', N'U') IS NOT NULL OR OBJECT_ID(N'dbo.loyalty_rules', N'U') IS NOT NULL
    THROW 51000, 'Existing Loyalty data: use the migration scripts, not fresh_install.', 1;
CREATE TABLE dbo.loyalty_rules (
    rule_id BIGINT NOT NULL PRIMARY KEY CHECK (rule_id = 1),
    earning_percent DECIMAL(5,2) NOT NULL DEFAULT 1.00 CHECK (earning_percent > 0 AND earning_percent <= 100),
    earning_enabled BIT NOT NULL DEFAULT 1,
    min_redemption_amount DECIMAL(18,2) NOT NULL DEFAULT 1000 CHECK (min_redemption_amount >= 1 AND min_redemption_amount = FLOOR(min_redemption_amount)),
    max_redemption_percent INT NOT NULL DEFAULT 20 CHECK (max_redemption_percent BETWEEN 1 AND 100),
    redemption_enabled BIT NOT NULL DEFAULT 1,
    updated_at DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
);
INSERT dbo.loyalty_rules(rule_id) VALUES (1);
CREATE TABLE dbo.loyalty_accounts (
    loyalty_account_id BIGINT IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES dbo.users(user_id),
    reward_balance DECIMAL(18,2) NOT NULL DEFAULT 0 CHECK (reward_balance >= 0 AND reward_balance = FLOOR(reward_balance)),
    total_earned_amount DECIMAL(18,2) NOT NULL DEFAULT 0 CHECK (total_earned_amount >= 0 AND total_earned_amount = FLOOR(total_earned_amount)),
    total_redeemed_amount DECIMAL(18,2) NOT NULL DEFAULT 0 CHECK (total_redeemed_amount >= 0 AND total_redeemed_amount = FLOOR(total_redeemed_amount)),
    updated_at DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.loyalty_transactions (
    loyalty_transaction_id BIGINT IDENTITY PRIMARY KEY,
    loyalty_account_id BIGINT NOT NULL REFERENCES dbo.loyalty_accounts(loyalty_account_id),
    order_id BIGINT NULL REFERENCES dbo.orders(order_id),
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(18,2) NOT NULL CHECK (amount = FLOOR(amount)),
    balance_after_amount DECIMAL(18,2) NOT NULL CHECK (balance_after_amount >= 0 AND balance_after_amount = FLOOR(balance_after_amount)),
    note NVARCHAR(500) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
    CHECK ((transaction_type <> 'EARN' OR amount > 0) AND (transaction_type <> 'REDEEM' OR amount < 0))
);
CREATE UNIQUE INDEX UX_loyalty_vnd_earn ON dbo.loyalty_transactions(loyalty_account_id, order_id) WHERE transaction_type = 'EARN' AND order_id IS NOT NULL;
CREATE UNIQUE INDEX UX_loyalty_vnd_redeem ON dbo.loyalty_transactions(loyalty_account_id, order_id) WHERE transaction_type = 'REDEEM' AND order_id IS NOT NULL;
COMMIT;
