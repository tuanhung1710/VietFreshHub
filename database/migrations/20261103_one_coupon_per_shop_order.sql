USE [VietFresh_Hub];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.order_discounts', N'U') IS NULL
    THROW 51000, 'Expected dbo.order_discounts to exist.', 1;

IF EXISTS (
    SELECT shop_order_id
    FROM dbo.order_discounts
    WHERE coupon_id IS NOT NULL AND shop_order_id IS NOT NULL
    GROUP BY shop_order_id
    HAVING COUNT(*) > 1
)
    THROW 51001, 'Multiple coupon discounts already exist for a shop order; resolve duplicate data before creating the index.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.order_discounts') AND name = N'UX_order_discounts_one_coupon_per_shop_order')
    CREATE UNIQUE INDEX UX_order_discounts_one_coupon_per_shop_order
        ON dbo.order_discounts(shop_order_id)
        WHERE coupon_id IS NOT NULL AND shop_order_id IS NOT NULL;

COMMIT TRANSACTION;
GO
