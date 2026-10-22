-- Optional setup for future shopping groups. Run against the intended VietFresh Hub database.
-- Does not assign products or change existing category statuses.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @root BIGINT = (SELECT category_id FROM dbo.categories WHERE slug = 'trai-cay');
INSERT INTO dbo.categories (parent_category_id, name, slug, status)
SELECT @root, groups.name, groups.slug, 'ACTIVE'
FROM (VALUES
    (N'Trái cây theo mùa', 'trai-cay-theo-mua'),
    (N'Đặc sản vùng miền', 'dac-san-vung-mien'),
    (N'Trái cây hữu cơ', 'trai-cay-huu-co'),
    (N'Combo gia đình', 'combo-gia-dinh'),
    (N'Trái cây ép và sinh tố', 'trai-cay-nuoc-ep-sinh-to'),
    (N'Quà biếu lễ Tết', 'qua-bieu-le-tet')
) AS groups(name, slug)
WHERE NOT EXISTS (SELECT 1 FROM dbo.categories c WITH (UPDLOCK, HOLDLOCK) WHERE c.slug = groups.slug);
COMMIT TRANSACTION;
