-- SQL Server. Stop the application and back up first. Do not restart until 03 succeeds.
-- Existing installations only; a second execution deliberately fails.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.loyalty_accounts', N'U') IS NULL
    THROW 51000, 'Missing loyalty_accounts; use fresh_install.sql for a new installation.', 1;
IF COL_LENGTH(N'dbo.loyalty_accounts', N'reward_balance') IS NOT NULL
    THROW 51000, 'VND preparation already applied; inspect migration state.', 1;
IF OBJECT_ID(N'dbo.loyalty_rules_points_legacy', N'U') IS NOT NULL
    THROW 51000, 'Legacy rules archive already exists; inspect migration state.', 1;
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE referenced_object_id = OBJECT_ID(N'dbo.loyalty_rules'))
    THROW 51000, 'Unexpected foreign key to loyalty_rules; review before migrating.', 1;

-- Preserve all old settings, including any partially migrated rule schema.
EXEC sys.sp_rename N'dbo.loyalty_rules', N'loyalty_rules_points_legacy';
CREATE TABLE dbo.loyalty_rules (
    rule_id BIGINT NOT NULL CONSTRAINT PK_loyalty_rules_vnd PRIMARY KEY,
    earning_percent DECIMAL(5,2) NOT NULL CONSTRAINT DF_loyalty_vnd_earning DEFAULT 1.00,
    earning_enabled BIT NOT NULL CONSTRAINT DF_loyalty_vnd_earn_enabled DEFAULT 1,
    min_redemption_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_loyalty_vnd_min DEFAULT 1000,
    max_redemption_percent INT NOT NULL CONSTRAINT DF_loyalty_vnd_max DEFAULT 20,
    redemption_enabled BIT NOT NULL CONSTRAINT DF_loyalty_vnd_redeem_enabled DEFAULT 1,
    updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_loyalty_vnd_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loyalty_vnd_rule_id CHECK (rule_id = 1),
    CONSTRAINT CK_loyalty_vnd_earning CHECK (earning_percent > 0 AND earning_percent <= 100),
    CONSTRAINT CK_loyalty_vnd_min CHECK (min_redemption_amount >= 1 AND min_redemption_amount = FLOOR(min_redemption_amount)),
    CONSTRAINT CK_loyalty_vnd_max CHECK (max_redemption_percent BETWEEN 1 AND 100)
);
INSERT dbo.loyalty_rules (rule_id, earning_enabled, redemption_enabled, max_redemption_percent)
SELECT 1, earning_enabled, redemption_enabled, max_redemption_percent
FROM dbo.loyalty_rules_points_legacy WHERE rule_id = 1;
IF NOT EXISTS (SELECT 1 FROM dbo.loyalty_rules WHERE rule_id = 1)
    INSERT dbo.loyalty_rules (rule_id) VALUES (1);
-- Honor earning_percent if a prior rules migration already introduced it.
IF COL_LENGTH(N'dbo.loyalty_rules_points_legacy', N'earning_percent') IS NOT NULL
    EXEC(N'UPDATE r SET earning_percent = old.earning_percent FROM dbo.loyalty_rules r JOIN dbo.loyalty_rules_points_legacy old ON old.rule_id = r.rule_id;');
IF COL_LENGTH(N'dbo.loyalty_rules_points_legacy', N'min_redemption_amount') IS NOT NULL
    EXEC(N'UPDATE r SET min_redemption_amount = old.min_redemption_amount FROM dbo.loyalty_rules r JOIN dbo.loyalty_rules_points_legacy old ON old.rule_id = r.rule_id;');

-- Keep original integer values as explicit legacy data, never reinterpret as VND.
EXEC sys.sp_rename N'dbo.loyalty_accounts.points_balance', N'legacy_points_balance', N'COLUMN';
EXEC sys.sp_rename N'dbo.loyalty_accounts.total_earned', N'legacy_total_earned_points', N'COLUMN';
EXEC sys.sp_rename N'dbo.loyalty_accounts.total_redeemed', N'legacy_total_redeemed_points', N'COLUMN';
EXEC sys.sp_rename N'dbo.loyalty_transactions.points', N'legacy_points', N'COLUMN';
EXEC sys.sp_rename N'dbo.loyalty_transactions.balance_after', N'legacy_balance_after_points', N'COLUMN';
ALTER TABLE dbo.loyalty_accounts ADD reward_balance DECIMAL(18,2) NULL,
    total_earned_amount DECIMAL(18,2) NULL, total_redeemed_amount DECIMAL(18,2) NULL;
ALTER TABLE dbo.loyalty_transactions ADD amount DECIMAL(18,2) NULL, balance_after_amount DECIMAL(18,2) NULL;
COMMIT;
