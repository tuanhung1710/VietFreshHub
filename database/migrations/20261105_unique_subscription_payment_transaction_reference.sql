USE [VietFresh_Hub];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.subscription_payments', N'U') IS NULL
    THROW 51000, 'Expected dbo.subscription_payments to exist.', 1;

-- Ensure transaction_reference is unique among non-NULL values before creating the index.
IF EXISTS (
    SELECT transaction_reference
    FROM dbo.subscription_payments
    WHERE transaction_reference IS NOT NULL
    GROUP BY transaction_reference
    HAVING COUNT(*) > 1
)
    THROW 51001, 'Duplicate transaction_reference values already exist in dbo.subscription_payments; resolve duplicate data before creating the index.', 1;

-- Filtered unique index: NULL (not yet assigned) remains allowed.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.subscription_payments') AND name = N'UX_subscription_payments_transaction_reference')
    CREATE UNIQUE INDEX UX_subscription_payments_transaction_reference
        ON dbo.subscription_payments(transaction_reference)
        WHERE transaction_reference IS NOT NULL;

COMMIT TRANSACTION;
GO
