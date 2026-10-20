-- Apply explicitly to the intended database. Does not change existing COD/BANK_TRANSFER.
-- No USE statement: verify SELECT DB_NAME() before running.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM dbo.payment_methods WHERE method_code='VNPAY')
    INSERT dbo.payment_methods(name,method_code,status) VALUES(N'VNPAY Sandbox','VNPAY','ACTIVE');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.payments') AND name='UX_TV3_VnpayReference')
    CREATE UNIQUE INDEX UX_TV3_VnpayReference ON dbo.payments(transaction_reference)
        WHERE provider=N'VNPAY' AND transaction_reference IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.payment_transactions') AND name='UX_TV3_VnpayReceipt')
    CREATE UNIQUE INDEX UX_TV3_VnpayReceipt ON dbo.payment_transactions(provider_transaction_id)
        WHERE event_type='VNPAY_RECEIPT' AND provider_transaction_id IS NOT NULL;
COMMIT;
GO
-- Database guard protects TV4 transitions without editing TV4 source.
CREATE OR ALTER TRIGGER dbo.TR_TV3_VnpayPaymentGate ON dbo.shop_orders AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    -- Initial PENDING inserts (including COD) never need to lock parent orders.
    IF NOT EXISTS (SELECT 1 FROM inserted WHERE status NOT IN ('PENDING','CANCELLED')) RETURN;
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.orders o WITH (UPDLOCK,HOLDLOCK) ON o.order_id=i.order_id
        WHERE i.status NOT IN ('PENDING','CANCELLED') AND o.payment_status<>'PAID'
          AND EXISTS (SELECT 1 FROM dbo.payments p WHERE p.order_id=i.order_id AND p.provider=N'VNPAY')
    ) THROW 59013, N'Online payment must be verified before shop processing.', 1;
END;
GO
