-- Run once on the existing VietFreshHub SQL Server database before enabling inventory alerts.
-- Additive migration; no existing product, stock, order or notification rows are changed.
IF OBJECT_ID(N'dbo.inventory_alert_states', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_alert_states (
        shop_id BIGINT NOT NULL,
        alert_type VARCHAR(30) NOT NULL,
        entity_id BIGINT NOT NULL,
        active BIT NOT NULL,
        updated_at DATETIME2(0) NOT NULL,
        CONSTRAINT PK_inventory_alert_states PRIMARY KEY (shop_id, alert_type, entity_id),
        CONSTRAINT FK_inventory_alert_states_shop FOREIGN KEY (shop_id) REFERENCES dbo.shops(shop_id)
    );
END;
GO
