-- Add the optional category image used by the current Category entity.
-- Safe to rerun; existing rows and business rules remain unchanged.
USE [VietFresh_Hub];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.categories', N'U') IS NULL
    THROW 51000, 'Expected dbo.categories to exist.', 1;

IF COL_LENGTH(N'dbo.categories', N'image_url') IS NULL
    ALTER TABLE dbo.categories ADD image_url NVARCHAR(1000) NULL;

COMMIT TRANSACTION;
GO
