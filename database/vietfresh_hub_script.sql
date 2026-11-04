-- Complete VietFreshHub schema: run this file, then data.sql. No external SQL includes.
-- Empty database: install all tables, indexes, views, procedures and triggers.
-- Existing database: upgrade supported legacy columns and reload all shared modules.
-- Existing data is preserved; all schema changes below run in one transaction.
-- Includes product origin and complete shop addresses derived only from shop_locations.
SET NOEXEC OFF;
GO
USE [master];
GO
IF DB_ID(N'VietFresh_Hub') IS NULL
BEGIN
    CREATE DATABASE [VietFresh_Hub];
END;
GO
USE [VietFresh_Hub];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() IN ('master','model','msdb','tempdb') THROW 51500,'Select the application database.',1;
IF @@TRANCOUNT<>0 THROW 51500,'An existing transaction is open; inspect it first.',1;
-- Historical conversion and fallback quota; existing monetary balances/quotas are preserved.
DECLARE @LegacyVndPerPoint DECIMAL(18,6)=100, @LegacyPlanMaxProducts INT=20;
DECLARE @freshInstall BIT;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @schemaLockResult INT;
EXEC @schemaLockResult=sys.sp_getapplock @Resource=N'VietFreshHub.Schema',
 @LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=10000;
IF @schemaLockResult<0 THROW 51500,'Cannot acquire schema installation lock.',1;
SET @freshInstall=CASE WHEN EXISTS(SELECT 1 FROM sys.tables WHERE is_ms_shipped=0) THEN 0 ELSE 1 END;
IF @freshInstall=1
BEGIN
-- BEGIN FRESH TABLES AND INDEXES

EXEC sys.sp_executesql N'-- 01. users
CREATE TABLE dbo.users (
    user_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    email               NVARCHAR(255) NOT NULL,
    username            VARCHAR(64) COLLATE Latin1_General_100_CI_AS NULL,
    password_hash       NVARCHAR(500) NULL,
    full_name           NVARCHAR(150) NOT NULL,
    phone               NVARCHAR(30) NULL,
    avatar_url          NVARCHAR(1000) NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_users_status DEFAULT (''ACTIVE''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_users_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_users_updated_at DEFAULT (SYSUTCDATETIME()),
	email_verified_at DATETIME2(0) NULL,

    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT CK_users_username_format CHECK (username IS NULL OR (LEN(username) BETWEEN 3 AND 64 AND username COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^a-z0-9._-]%'' AND LEFT(username,1) COLLATE Latin1_General_100_BIN2 LIKE ''[a-z0-9]'')),
    CONSTRAINT CK_users_status CHECK (status IN (''ACTIVE'', ''INACTIVE'', ''BLOCKED'', ''DELETED''))
);';

EXEC sys.sp_executesql N'CREATE UNIQUE INDEX UX_users_username ON dbo.users(username) WHERE username IS NOT NULL;';

EXEC sys.sp_executesql N'-- 02. roles
CREATE TABLE dbo.roles (
    role_id             INT IDENTITY(1,1) PRIMARY KEY,
    role_name           VARCHAR(50) NOT NULL,
    description         NVARCHAR(255) NULL,

    CONSTRAINT UQ_roles_role_name UNIQUE (role_name)
);';

EXEC sys.sp_executesql N'-- 03. user_roles
CREATE TABLE dbo.user_roles (
    user_id             BIGINT NOT NULL,
    role_id             INT NOT NULL,
    assigned_at         DATETIME2(0) NOT NULL CONSTRAINT DF_user_roles_assigned_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_user_roles_role FOREIGN KEY (role_id)
        REFERENCES dbo.roles(role_id)
);';

EXEC sys.sp_executesql N'-- 04. user_external_accounts
CREATE TABLE dbo.user_external_accounts (
    external_account_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    provider            VARCHAR(30) NOT NULL,
    provider_user_id    VARCHAR(255)
                        COLLATE Latin1_General_100_BIN2 NOT NULL,
    created_at          DATETIME2(0) NOT NULL
        CONSTRAINT DF_user_external_accounts_created_at
        DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_user_external_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),

    -- Một tài khoản Google chỉ liên kết với một user.
    CONSTRAINT UQ_user_external_accounts_provider_identity
        UNIQUE (provider, provider_user_id),

    -- Mỗi user chỉ liên kết một tài khoản cho mỗi nhà cung cấp.
    CONSTRAINT UQ_user_external_accounts_user_provider
        UNIQUE (user_id, provider)
);';

EXEC sys.sp_executesql N'-- 05. addresses
CREATE TABLE dbo.addresses (
    address_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    recipient_name      NVARCHAR(150) NOT NULL,
    phone               NVARCHAR(30) NOT NULL,
    province            NVARCHAR(100) NOT NULL,
    district            NVARCHAR(100) NULL,
    ward                NVARCHAR(100) NOT NULL,
    address_line        NVARCHAR(500) NOT NULL,
    is_default          BIT NOT NULL CONSTRAINT DF_addresses_is_default DEFAULT (0),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_addresses_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_addresses_updated_at DEFAULT (SYSUTCDATETIME()),
    province_id INT NULL,
    district_id INT NULL,
    ward_id INT NULL,
    formatted_address NVARCHAR(1000) NULL,
    latitude DECIMAL(9,6) NULL,
    longitude DECIMAL(10,6) NULL,

    CONSTRAINT FK_addresses_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_addresses_province_id
        CHECK (province_id IS NULL OR province_id > 0),
    CONSTRAINT CK_addresses_district_id
        CHECK (district_id IS NULL OR district_id > 0),
    CONSTRAINT CK_addresses_ward_id
        CHECK (ward_id IS NULL OR ward_id > 0),
    CONSTRAINT CK_addresses_formatted_address_not_blank
        CHECK (
            formatted_address IS NULL
            OR LEN(LTRIM(RTRIM(formatted_address))) > 0
        ),
    CONSTRAINT CK_addresses_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90.000000 AND 90.000000),
    CONSTRAINT CK_addresses_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180.000000 AND 180.000000),
    CONSTRAINT CK_addresses_coordinate_pair
        CHECK (
            (latitude IS NULL AND longitude IS NULL)
            OR
            (latitude IS NOT NULL AND longitude IS NOT NULL)
        )
);';

EXEC sys.sp_executesql N'-- 06. password_reset_tokens
CREATE TABLE dbo.password_reset_tokens (
    reset_token_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    token_hash          NVARCHAR(500) NOT NULL,
    expires_at          DATETIME2(0) NOT NULL,
    used_at             DATETIME2(0) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_password_reset_tokens_created_at DEFAULT (SYSUTCDATETIME()),
    token_type VARCHAR(30) NOT NULL CONSTRAINT DF_password_reset_tokens_token_type DEFAULT (''PASSWORD_RESET''),
    attempt_count INT NOT NULL CONSTRAINT DF_password_reset_tokens_attempt_count DEFAULT (0),

    CONSTRAINT UQ_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT FK_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_password_reset_tokens_token_type CHECK (token_type IN (''PASSWORD_RESET'', ''EMAIL_VERIFICATION'')),
    CONSTRAINT CK_password_reset_tokens_attempt_count CHECK (attempt_count >= 0)
);';

EXEC sys.sp_executesql N'-- 07. seller_applications
CREATE TABLE dbo.seller_applications (
    application_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    business_name       NVARCHAR(200) NOT NULL,
    tax_code            NVARCHAR(50) NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_seller_applications_status DEFAULT (''PENDING''),
    submitted_at        DATETIME2(0) NOT NULL CONSTRAINT DF_seller_applications_submitted_at DEFAULT (SYSUTCDATETIME()),
    reviewed_by         BIGINT NULL,
    reviewed_at         DATETIME2(0) NULL,
    rejection_reason    NVARCHAR(1000) NULL,
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_seller_applications_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_seller_applications_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_seller_applications_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_seller_applications_status CHECK (status IN (''PENDING'', ''UNDER_REVIEW'', ''APPROVED'', ''REJECTED'', ''CANCELLED''))
);';

EXEC sys.sp_executesql N'-- 08. seller_application_documents
CREATE TABLE dbo.seller_application_documents (
    document_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    application_id      BIGINT NOT NULL,
    document_type       VARCHAR(50) NOT NULL,
    file_url            NVARCHAR(1000) NOT NULL,
    verification_status VARCHAR(30) NOT NULL CONSTRAINT DF_seller_application_documents_status DEFAULT (''PENDING''),
    uploaded_at         DATETIME2(0) NOT NULL CONSTRAINT DF_seller_application_documents_uploaded_at DEFAULT (SYSUTCDATETIME()),
    verified_at         DATETIME2(0) NULL,
    cloudinary_resource_type VARCHAR(20) NULL,
    cloudinary_format VARCHAR(20) NULL,

    CONSTRAINT FK_seller_application_documents_application FOREIGN KEY (application_id)
        REFERENCES dbo.seller_applications(application_id),
    CONSTRAINT CK_seller_application_documents_status CHECK (verification_status IN (''PENDING'', ''VERIFIED'', ''REJECTED''))
);';

EXEC sys.sp_executesql N'-- 09. seller_application_status_history
CREATE TABLE dbo.seller_application_status_history (
    history_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    application_id      BIGINT NOT NULL,
    old_status          VARCHAR(30) NULL,
    new_status          VARCHAR(30) NOT NULL,
    changed_by          BIGINT NULL,
    note                NVARCHAR(1000) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_seller_application_status_history_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_seller_application_status_history_application FOREIGN KEY (application_id)
        REFERENCES dbo.seller_applications(application_id),
    CONSTRAINT FK_seller_application_status_history_user FOREIGN KEY (changed_by)
        REFERENCES dbo.users(user_id)
);';

EXEC sys.sp_executesql N'-- 10. shops
CREATE TABLE dbo.shops (
    shop_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    application_id      BIGINT NULL,
    shop_name           NVARCHAR(200) NOT NULL,
    description         NVARCHAR(MAX) NULL,
    logo_url            NVARCHAR(1000) NULL,
    phone               NVARCHAR(30) NULL,
    email               NVARCHAR(255) NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_shops_status DEFAULT (''ACTIVE''),
    availability_status VARCHAR(30) NOT NULL CONSTRAINT DF_shops_availability DEFAULT (''OPEN''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_shops_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_shops_updated_at DEFAULT (SYSUTCDATETIME()),
    banner_url NVARCHAR(1000) NULL,

    CONSTRAINT FK_shops_application FOREIGN KEY (application_id)
        REFERENCES dbo.seller_applications(application_id),
    CONSTRAINT CK_shops_status CHECK (status IN (''ACTIVE'', ''INACTIVE'', ''SUSPENDED'', ''CLOSED'')),
    CONSTRAINT CK_shops_availability CHECK (availability_status IN (''OPEN'', ''CLOSED'', ''TEMPORARILY_UNAVAILABLE''))
);';

EXEC sys.sp_executesql N'-- 11. shop_members
CREATE TABLE dbo.shop_members (
    shop_member_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id             BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    member_role         VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_shop_members_status DEFAULT (''ACTIVE''),
    joined_at           DATETIME2(0) NOT NULL CONSTRAINT DF_shop_members_joined_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_shop_members_shop_user UNIQUE (shop_id, user_id),
    CONSTRAINT FK_shop_members_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_shop_members_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_shop_members_role CHECK (member_role IN (''OWNER'', ''MANAGER'')),
    CONSTRAINT CK_shop_members_status CHECK (status IN (''ACTIVE'', ''INACTIVE'', ''REMOVED'')),
    CONSTRAINT CK_shop_members_active_owner CHECK (status <> ''ACTIVE'' OR member_role = ''OWNER'')
);';

EXEC sys.sp_executesql N'-- 12. shop_business_hours
CREATE TABLE dbo.shop_business_hours (
    shop_hour_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id             BIGINT NOT NULL,
    day_of_week         TINYINT NOT NULL,
    open_time           TIME(0) NULL,
    close_time          TIME(0) NULL,
    is_closed           BIT NOT NULL CONSTRAINT DF_shop_business_hours_closed DEFAULT (0),

    CONSTRAINT UQ_shop_business_hours_shop_day UNIQUE (shop_id, day_of_week),
    CONSTRAINT FK_shop_business_hours_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT CK_shop_business_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT CK_shop_business_hours_times CHECK (
        (is_closed = 1 AND open_time IS NULL AND close_time IS NULL)
        OR
        (is_closed = 0 AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time)
    )
);';

EXEC sys.sp_executesql N'-- 13. favorite_shops
CREATE TABLE dbo.favorite_shops (
    user_id             BIGINT NOT NULL,
    shop_id             BIGINT NOT NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_favorite_shops_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_favorite_shops PRIMARY KEY (user_id, shop_id),
    CONSTRAINT FK_favorite_shops_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_favorite_shops_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id)
);';

EXEC sys.sp_executesql N'-- 14. categories
CREATE TABLE dbo.categories (
    category_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    parent_category_id  BIGINT NULL,
    name                NVARCHAR(150) NOT NULL,
    slug                VARCHAR(200) NOT NULL, /* Là chuỗi để tạo url dễ đọc: https://vietfreshhub.com/shops/trai-cay-son-fresh*/
    description         NVARCHAR(1000) NULL,
    image_url           NVARCHAR(1000) NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_categories_status DEFAULT (''ACTIVE''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_categories_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_categories_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_categories_slug UNIQUE (slug),
    CONSTRAINT FK_categories_parent FOREIGN KEY (parent_category_id)
        REFERENCES dbo.categories(category_id),
    CONSTRAINT CK_categories_status CHECK (status IN (''ACTIVE'', ''INACTIVE'')),
    CONSTRAINT CK_categories_not_self_parent CHECK (parent_category_id IS NULL OR parent_category_id <> category_id)
);';

EXEC sys.sp_executesql N'-- 15. products
CREATE TABLE dbo.products (
    product_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id             BIGINT NOT NULL,
    name                NVARCHAR(250) NOT NULL,
    normalized_name     AS (LTRIM(RTRIM(name)) COLLATE Latin1_General_100_CI_AS) PERSISTED,
    slug                VARCHAR(300) NOT NULL,
    description         NVARCHAR(MAX) NULL,
    origin              NVARCHAR(200) NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_products_status DEFAULT (''INACTIVE''),
    moderation_blocked  BIT NOT NULL CONSTRAINT DF_products_moderation_blocked DEFAULT (0),
    moderation_reason   NVARCHAR(1000) NULL,
    seller_hidden       BIT NULL,
    approval_status     VARCHAR(30) NOT NULL CONSTRAINT DF_products_approval_status DEFAULT (''PENDING''),
    approved_by         BIGINT NULL,
    approved_at         DATETIME2(0) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_products_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_products_updated_at DEFAULT (SYSUTCDATETIME()),
    requires_preparation_check BIT NOT NULL CONSTRAINT DF_products_preparation DEFAULT (0),
    preparation_note NVARCHAR(1000) NULL,
    expiry_warning_days INT NOT NULL CONSTRAINT DF_products_expiry_warning_days DEFAULT (3),

    CONSTRAINT UQ_products_shop_slug UNIQUE (shop_id, slug),
    CONSTRAINT FK_products_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_products_approved_by FOREIGN KEY (approved_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_products_origin_not_blank CHECK (origin IS NULL OR LEN(LTRIM(RTRIM(origin))) > 0),
    CONSTRAINT CK_products_status CHECK (status IN (''ACTIVE'', ''INACTIVE'', ''DELETED'')),
    CONSTRAINT CK_products_approval_status CHECK (approval_status IN (''PENDING'', ''APPROVED'', ''REJECTED'')),
    CONSTRAINT CK_products_expiry_warning_days CHECK (expiry_warning_days >= 0),
    CONSTRAINT CK_products_moderation_reason CHECK (moderation_blocked=0 OR NULLIF(LTRIM(RTRIM(moderation_reason)),N'''') IS NOT NULL)
);';

EXEC sys.sp_executesql N'-- 16. product_categories
CREATE TABLE dbo.product_categories (
    product_id          BIGINT NOT NULL,
    category_id         BIGINT NOT NULL,

    CONSTRAINT PK_product_categories PRIMARY KEY (product_id, category_id),
    CONSTRAINT FK_product_categories_product FOREIGN KEY (product_id)
        REFERENCES dbo.products(product_id),
    CONSTRAINT FK_product_categories_category FOREIGN KEY (category_id)
        REFERENCES dbo.categories(category_id)
);';

EXEC sys.sp_executesql N'-- 17. product_variants
CREATE TABLE dbo.product_variants (
    variant_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    sku                 VARCHAR(100) NOT NULL,
    variant_name        NVARCHAR(200) NOT NULL,
    thumbnail_url       NVARCHAR(1000) NOT NULL,
    price               DECIMAL(18,2) NOT NULL,
    compare_at_price    DECIMAL(18,2) NULL,
    weight              DECIMAL(18,3) NULL,
    unit                NVARCHAR(30) NOT NULL,
    low_stock_threshold INT NOT NULL CONSTRAINT DF_product_variants_low_stock DEFAULT (0),
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_product_variants_status DEFAULT (''ACTIVE''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_product_variants_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_product_variants_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_product_variants_sku UNIQUE (sku),
    CONSTRAINT UQ_product_variants_product_variant UNIQUE (product_id, variant_id),
    CONSTRAINT CK_product_variants_thumbnail CHECK (LEN(LTRIM(RTRIM(thumbnail_url))) > 0),
    CONSTRAINT FK_product_variants_product FOREIGN KEY (product_id)
        REFERENCES dbo.products(product_id),
    CONSTRAINT CK_product_variants_price CHECK (price >= 0),
    CONSTRAINT CK_product_variants_compare_price CHECK (compare_at_price IS NULL OR compare_at_price >= 0),
    CONSTRAINT CK_product_variants_weight CHECK (weight IS NULL OR weight > 0),
    CONSTRAINT CK_product_variants_low_stock CHECK (low_stock_threshold >= 0),
    CONSTRAINT CK_product_variants_status CHECK (status IN (''ACTIVE'', ''INACTIVE'', ''OUT_OF_STOCK'', ''DELETED'')),
    CONSTRAINT CK_product_variants_unit_required CHECK (LEN(LTRIM(RTRIM(unit))) > 0),
    CONSTRAINT CK_product_variants_compare_not_below_price CHECK (compare_at_price IS NULL OR compare_at_price >= price),
    CONSTRAINT CK_product_variants_min_selling_price CHECK (status = ''DELETED'' OR (price >= 10000 AND price = FLOOR(price)))
);';

EXEC sys.sp_executesql N'-- 18. product_images
CREATE TABLE dbo.product_images (
    image_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    variant_id          BIGINT NULL,
    image_url           NVARCHAR(1000) NOT NULL,
    is_primary          BIT NOT NULL CONSTRAINT DF_product_images_primary DEFAULT (0),
    sort_order          INT NOT NULL CONSTRAINT DF_product_images_sort DEFAULT (0),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_product_images_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_product_images_product FOREIGN KEY (product_id)
        REFERENCES dbo.products(product_id),
    CONSTRAINT FK_product_images_variant FOREIGN KEY (product_id, variant_id)
        REFERENCES dbo.product_variants(product_id, variant_id),
    CONSTRAINT CK_product_images_sort_order CHECK (sort_order >= 0)
);';

EXEC sys.sp_executesql N'-- 19. price_history
CREATE TABLE dbo.price_history (
    price_history_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id          BIGINT NOT NULL,
    price               DECIMAL(18,2) NOT NULL,
    effective_from      DATETIME2(0) NOT NULL,
    effective_to        DATETIME2(0) NULL,
    changed_by          BIGINT NULL,

    CONSTRAINT FK_price_history_variant FOREIGN KEY (variant_id)
        REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT FK_price_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_price_history_price CHECK (price >= 0),
    CONSTRAINT CK_price_history_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);';

EXEC sys.sp_executesql N'-- 20. inventory_batches
CREATE TABLE dbo.inventory_batches (
    batch_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id          BIGINT NOT NULL,
    quantity_on_hand    INT NOT NULL CONSTRAINT DF_inventory_batches_qty DEFAULT (0),
    reserved_quantity   INT NOT NULL CONSTRAINT DF_inventory_batches_reserved DEFAULT (0),
    cost_price          DECIMAL(18,2) NULL,
    received_at         DATETIME2(0) NOT NULL CONSTRAINT DF_inventory_batches_received_at DEFAULT (SYSUTCDATETIME()),
    expiry_date         DATE NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_inventory_batches_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_inventory_batches_updated_at DEFAULT (SYSUTCDATETIME()),
    status VARCHAR(20) NOT NULL CONSTRAINT DF_pi_batch_status DEFAULT (''ACTIVE'') CONSTRAINT CK_pi_batch_status CHECK (status IN (''ACTIVE'',''BLOCKED'')),

    CONSTRAINT FK_inventory_batches_variant FOREIGN KEY (variant_id)
        REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT CK_inventory_batches_qty CHECK (quantity_on_hand >= 0),
    CONSTRAINT CK_inventory_batches_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity_on_hand),
    CONSTRAINT CK_inventory_batches_cost CHECK (cost_price IS NULL OR cost_price >= 0),
    CONSTRAINT UQ_inventory_batches_variant_batch UNIQUE (variant_id, batch_id)
);';

EXEC sys.sp_executesql N'-- 21. inventory_transactions
CREATE TABLE dbo.inventory_transactions (
    transaction_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    variant_id          BIGINT NOT NULL,
    batch_id            BIGINT NULL,
    transaction_type    VARCHAR(30) NOT NULL,
    quantity            INT NOT NULL,
    reference_type      VARCHAR(50) NULL,
    reference_id        BIGINT NULL,
    note                NVARCHAR(1000) NULL,
    created_by          BIGINT NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_inventory_transactions_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_inventory_transactions_variant FOREIGN KEY (variant_id)
        REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT FK_inventory_transactions_variant_batch FOREIGN KEY (variant_id, batch_id)
        REFERENCES dbo.inventory_batches(variant_id, batch_id),
    CONSTRAINT FK_inventory_transactions_created_by FOREIGN KEY (created_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_inventory_transactions_type CHECK (
        transaction_type IN (''RESTOCK'', ''RESERVE'', ''RELEASE'', ''SALE'', ''RETURN'', ''ADJUSTMENT'', ''EXPIRED'', ''DAMAGED'')
    ),
    CONSTRAINT CK_inventory_transactions_quantity CHECK (quantity <> 0)
);';

EXEC sys.sp_executesql N'-- 22. carts
CREATE TABLE dbo.carts (
    cart_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_carts_status DEFAULT (''ACTIVE''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_carts_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_carts_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_carts_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_carts_status CHECK (status IN (''ACTIVE'', ''CHECKED_OUT'', ''ABANDONED''))
);';

EXEC sys.sp_executesql N'-- 23. cart_items
CREATE TABLE dbo.cart_items (
    cart_item_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    cart_id             BIGINT NOT NULL,
    variant_id          BIGINT NOT NULL,
    quantity            INT NOT NULL,
    unit_price_snapshot DECIMAL(18,2) NOT NULL,
    added_at            DATETIME2(0) NOT NULL CONSTRAINT DF_cart_items_added_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_cart_items_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_cart_items_cart_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT FK_cart_items_cart FOREIGN KEY (cart_id)
        REFERENCES dbo.carts(cart_id),
    CONSTRAINT FK_cart_items_variant FOREIGN KEY (variant_id)
        REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT CK_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT CK_cart_items_price CHECK (unit_price_snapshot >= 0)
);';

EXEC sys.sp_executesql N'-- 24. orders
CREATE TABLE dbo.orders (
    order_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id         BIGINT NOT NULL,
    order_code          VARCHAR(50) NOT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_orders_status DEFAULT (''PENDING''),
    payment_status      VARCHAR(30) NOT NULL CONSTRAINT DF_orders_payment_status DEFAULT (''UNPAID''),
    subtotal            DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_subtotal DEFAULT (0),
    discount_total      DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_discount DEFAULT (0),
    shipping_fee        DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_shipping DEFAULT (0),
    grand_total         DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_grand_total DEFAULT (0),
    placed_at           DATETIME2(0) NOT NULL CONSTRAINT DF_orders_placed_at DEFAULT (SYSUTCDATETIME()),
    cancelled_at        DATETIME2(0) NULL,
    completed_at        DATETIME2(0) NULL,

    CONSTRAINT UQ_orders_order_code UNIQUE (order_code),
    CONSTRAINT FK_orders_customer FOREIGN KEY (customer_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_orders_status CHECK (status IN (''PENDING'', ''CONFIRMED'', ''PROCESSING'', ''PARTIALLY_COMPLETED'', ''COMPLETED'', ''CANCELLED'')),
    CONSTRAINT CK_orders_payment_status CHECK (payment_status IN (''UNPAID'', ''PENDING'', ''PAID'', ''PARTIALLY_REFUNDED'', ''REFUNDED'', ''FAILED'')),
    CONSTRAINT CK_orders_amounts CHECK (
        subtotal >= 0 AND discount_total >= 0 AND shipping_fee >= 0 AND grand_total >= 0
    )
);';

EXEC sys.sp_executesql N'-- 25. order_addresses
CREATE TABLE dbo.order_addresses (
    order_address_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id            BIGINT NOT NULL,
    recipient_name      NVARCHAR(150) NOT NULL,
    phone               NVARCHAR(30) NOT NULL,
    province            NVARCHAR(100) NOT NULL,
    district            NVARCHAR(100) NULL,
    ward                NVARCHAR(100) NOT NULL,
    address_line        NVARCHAR(500) NOT NULL,
    province_id INT NULL,
    district_id INT NULL,
    ward_id INT NULL,
    formatted_address NVARCHAR(1000) NULL,
    latitude DECIMAL(9,6) NULL,
    longitude DECIMAL(10,6) NULL,

    CONSTRAINT UQ_order_addresses_order_id UNIQUE (order_id),
    CONSTRAINT FK_order_addresses_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id),
    CONSTRAINT CK_order_addresses_province_id
        CHECK (province_id IS NULL OR province_id > 0),
    CONSTRAINT CK_order_addresses_district_id
        CHECK (district_id IS NULL OR district_id > 0),
    CONSTRAINT CK_order_addresses_ward_id
        CHECK (ward_id IS NULL OR ward_id > 0),
    CONSTRAINT CK_order_addresses_formatted_address_not_blank
        CHECK (
            formatted_address IS NULL
            OR LEN(LTRIM(RTRIM(formatted_address))) > 0
        ),
    CONSTRAINT CK_order_addresses_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90.000000 AND 90.000000),
    CONSTRAINT CK_order_addresses_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180.000000 AND 180.000000),
    CONSTRAINT CK_order_addresses_coordinate_pair
        CHECK (
            (latitude IS NULL AND longitude IS NULL)
            OR
            (latitude IS NOT NULL AND longitude IS NOT NULL)
        )
);';

EXEC sys.sp_executesql N'-- 26. shop_orders
CREATE TABLE dbo.shop_orders (
    shop_order_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id            BIGINT NOT NULL,
    shop_id             BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_shop_orders_status DEFAULT (''PENDING''),
    payment_status      VARCHAR(30) NOT NULL CONSTRAINT DF_shop_orders_payment_status DEFAULT (''UNPAID''),
    subtotal            DECIMAL(18,2) NOT NULL CONSTRAINT DF_shop_orders_subtotal DEFAULT (0),
    discount_total      DECIMAL(18,2) NOT NULL CONSTRAINT DF_shop_orders_discount DEFAULT (0),
    shipping_fee        DECIMAL(18,2) NOT NULL CONSTRAINT DF_shop_orders_shipping DEFAULT (0),
    total_amount        DECIMAL(18,2) NOT NULL CONSTRAINT DF_shop_orders_total DEFAULT (0),
    confirmed_at        DATETIME2(0) NULL,
    ready_at            DATETIME2(0) NULL,
    completed_at        DATETIME2(0) NULL,
    cancelled_at        DATETIME2(0) NULL,
    shipping_distance_meters DECIMAL(12,1) NULL,

    CONSTRAINT UQ_shop_orders_order_shop UNIQUE (order_id, shop_id),
    CONSTRAINT UQ_shop_orders_id_shop UNIQUE (shop_order_id, shop_id),
    CONSTRAINT UQ_shop_orders_id_order UNIQUE (shop_order_id, order_id),
    CONSTRAINT FK_shop_orders_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id),
    CONSTRAINT FK_shop_orders_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT CK_shop_orders_status CHECK (
        status IN (''PENDING'', ''CONFIRMED'', ''PREPARING'', ''READY_FOR_DELIVERY'', ''OUT_FOR_DELIVERY'', ''COMPLETED'', ''CANCELLED'')
    ),
    CONSTRAINT CK_shop_orders_payment_status CHECK (payment_status IN (''UNPAID'', ''PENDING'', ''PAID'', ''PARTIALLY_REFUNDED'', ''REFUNDED'', ''FAILED'')),
    CONSTRAINT CK_shop_orders_amounts CHECK (
        subtotal >= 0 AND discount_total >= 0 AND shipping_fee >= 0 AND total_amount >= 0
    ),
    CONSTRAINT CK_shop_orders_shipping_distance CHECK (shipping_distance_meters IS NULL OR shipping_distance_meters >= 0)
);';

EXEC sys.sp_executesql N'-- 27. order_items
CREATE TABLE dbo.order_items (
    order_item_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_order_id           BIGINT NOT NULL,
    variant_id              BIGINT NOT NULL,
    product_name_snapshot   NVARCHAR(250) NOT NULL,
    variant_name_snapshot   NVARCHAR(200) NOT NULL,
    sku_snapshot            VARCHAR(100) NOT NULL,
    unit_price              DECIMAL(18,2) NOT NULL,
    quantity                INT NOT NULL,
    discount_amount         DECIMAL(18,2) NOT NULL CONSTRAINT DF_order_items_discount DEFAULT (0),
    total_amount            DECIMAL(18,2) NOT NULL,
    requires_preparation_check_snapshot BIT NOT NULL CONSTRAINT DF_order_items_preparation DEFAULT (0),
    preparation_note_snapshot NVARCHAR(1000) NULL,

    CONSTRAINT FK_order_items_shop_order FOREIGN KEY (shop_order_id)
        REFERENCES dbo.shop_orders(shop_order_id),
    CONSTRAINT FK_order_items_variant FOREIGN KEY (variant_id)
        REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT CK_order_items_price CHECK (unit_price >= 0),
    CONSTRAINT CK_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT CK_order_items_discount CHECK (discount_amount >= 0),
    CONSTRAINT CK_order_items_total CHECK (total_amount >= 0)
);';

EXEC sys.sp_executesql N'-- 28. order_status_history
CREATE TABLE dbo.order_status_history (
    history_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_order_id       BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL,
    changed_by          BIGINT NULL,
    note                NVARCHAR(1000) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_order_status_history_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_order_status_history_shop_order FOREIGN KEY (shop_order_id)
        REFERENCES dbo.shop_orders(shop_order_id),
    CONSTRAINT FK_order_status_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES dbo.users(user_id)
);';

EXEC sys.sp_executesql N'-- 29. promotions
CREATE TABLE dbo.promotions (
    promotion_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id             BIGINT NULL,
    name                NVARCHAR(200) NOT NULL,
    description         NVARCHAR(1000) NULL,
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      DECIMAL(18,2) NOT NULL,
    max_discount        DECIMAL(18,2) NULL,
    minimum_order_value DECIMAL(18,2) NOT NULL CONSTRAINT DF_promotions_min_order DEFAULT (0),
    start_at            DATETIME2(0) NOT NULL,
    end_at              DATETIME2(0) NOT NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_promotions_status DEFAULT (''INACTIVE''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_promotions_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_promotions_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_promotions_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT CK_promotions_discount_type CHECK (discount_type IN (''PERCENT'', ''FIXED'')),
    CONSTRAINT CK_promotions_discount_value CHECK (
        discount_value > 0
        AND (discount_type <> ''PERCENT'' OR discount_value <= 100)
    ),
    CONSTRAINT CK_promotions_max_discount CHECK (max_discount IS NULL OR max_discount >= 0),
    CONSTRAINT CK_promotions_min_order CHECK (minimum_order_value >= 0),
    CONSTRAINT CK_promotions_period CHECK (end_at > start_at),
    CONSTRAINT CK_promotions_status CHECK (status IN (''INACTIVE'', ''ACTIVE'', ''EXPIRED'', ''CANCELLED''))
);';

EXEC sys.sp_executesql N'-- 30. promotion_products
CREATE TABLE dbo.promotion_products (
    promotion_id        BIGINT NOT NULL,
    product_id          BIGINT NOT NULL,

    CONSTRAINT PK_promotion_products PRIMARY KEY (promotion_id, product_id),
    CONSTRAINT FK_promotion_products_promotion FOREIGN KEY (promotion_id)
        REFERENCES dbo.promotions(promotion_id),
    CONSTRAINT FK_promotion_products_product FOREIGN KEY (product_id)
        REFERENCES dbo.products(product_id)
);';

EXEC sys.sp_executesql N'-- 31. promotion_variants
CREATE TABLE dbo.promotion_variants (
    promotion_id        BIGINT NOT NULL,
    variant_id          BIGINT NOT NULL,

    CONSTRAINT PK_promotion_variants PRIMARY KEY (promotion_id, variant_id),
    CONSTRAINT FK_promotion_variants_promotion FOREIGN KEY (promotion_id)
        REFERENCES dbo.promotions(promotion_id),
    CONSTRAINT FK_promotion_variants_variant FOREIGN KEY (variant_id)
        REFERENCES dbo.product_variants(variant_id)
);';

EXEC sys.sp_executesql N'-- 32. promotion_categories
CREATE TABLE dbo.promotion_categories (
    promotion_id        BIGINT NOT NULL,
    category_id         BIGINT NOT NULL,

    CONSTRAINT PK_promotion_categories PRIMARY KEY (promotion_id, category_id),
    CONSTRAINT FK_promotion_categories_promotion FOREIGN KEY (promotion_id)
        REFERENCES dbo.promotions(promotion_id),
    CONSTRAINT FK_promotion_categories_category FOREIGN KEY (category_id)
        REFERENCES dbo.categories(category_id)
);';

EXEC sys.sp_executesql N'-- 33. coupons
CREATE TABLE dbo.coupons (
    coupon_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    promotion_id        BIGINT NOT NULL,
    code                VARCHAR(100) NOT NULL,
    usage_limit         INT NULL,
    per_user_limit      INT NULL,
    minimum_order_value DECIMAL(18,2) NOT NULL CONSTRAINT DF_coupons_min_order DEFAULT (0),
    start_at            DATETIME2(0) NOT NULL,
    end_at              DATETIME2(0) NOT NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_coupons_status DEFAULT (''ACTIVE''),

    CONSTRAINT UQ_coupons_code UNIQUE (code),
    CONSTRAINT FK_coupons_promotion FOREIGN KEY (promotion_id)
        REFERENCES dbo.promotions(promotion_id),
    CONSTRAINT CK_coupons_usage_limit CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT CK_coupons_per_user_limit CHECK (per_user_limit IS NULL OR per_user_limit > 0),
    CONSTRAINT CK_coupons_min_order CHECK (minimum_order_value >= 0),
    CONSTRAINT CK_coupons_period CHECK (end_at > start_at),
    CONSTRAINT CK_coupons_status CHECK (status IN (''ACTIVE'', ''INACTIVE'', ''EXPIRED'', ''DISABLED''))
);';

EXEC sys.sp_executesql N'-- 34. coupon_usages
CREATE TABLE dbo.coupon_usages (
    coupon_usage_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    coupon_id           BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    order_id            BIGINT NOT NULL,
    discount_amount     DECIMAL(18,2) NOT NULL,
    used_at             DATETIME2(0) NOT NULL CONSTRAINT DF_coupon_usages_used_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_coupon_usages_coupon_order UNIQUE (coupon_id, order_id),
    CONSTRAINT FK_coupon_usages_coupon FOREIGN KEY (coupon_id)
        REFERENCES dbo.coupons(coupon_id),
    CONSTRAINT FK_coupon_usages_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_coupon_usages_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id),
    CONSTRAINT CK_coupon_usages_discount CHECK (discount_amount >= 0)
);';

EXEC sys.sp_executesql N'-- 35. order_discounts
CREATE TABLE dbo.order_discounts (
    order_discount_id   BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id            BIGINT NULL,
    shop_order_id       BIGINT NULL,
    order_item_id       BIGINT NULL,
    promotion_id        BIGINT NULL,
    coupon_id           BIGINT NULL,
    discount_amount     DECIMAL(18,2) NOT NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_order_discounts_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_order_discounts_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id),
    CONSTRAINT FK_order_discounts_shop_order FOREIGN KEY (shop_order_id)
        REFERENCES dbo.shop_orders(shop_order_id),
    CONSTRAINT FK_order_discounts_order_item FOREIGN KEY (order_item_id)
        REFERENCES dbo.order_items(order_item_id),
    CONSTRAINT FK_order_discounts_promotion FOREIGN KEY (promotion_id)
        REFERENCES dbo.promotions(promotion_id),
    CONSTRAINT FK_order_discounts_coupon FOREIGN KEY (coupon_id)
        REFERENCES dbo.coupons(coupon_id),
    CONSTRAINT CK_order_discounts_amount CHECK (discount_amount >= 0),
    CONSTRAINT CK_order_discounts_target CHECK (
        (CASE WHEN order_id IS NOT NULL THEN 1 ELSE 0 END)
      + (CASE WHEN shop_order_id IS NOT NULL THEN 1 ELSE 0 END)
      + (CASE WHEN order_item_id IS NOT NULL THEN 1 ELSE 0 END) = 1
    ),
    CONSTRAINT CK_order_discounts_source CHECK (
        promotion_id IS NOT NULL OR coupon_id IS NOT NULL
    )
);';

EXEC sys.sp_executesql N'-- 36. payment_methods
CREATE TABLE dbo.payment_methods (
    payment_method_id   INT IDENTITY(1,1) PRIMARY KEY,
    name                NVARCHAR(100) NOT NULL,
    method_code         VARCHAR(50) NOT NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_payment_methods_status DEFAULT (''ACTIVE''),

    CONSTRAINT UQ_payment_methods_name UNIQUE (name),
    CONSTRAINT UQ_payment_methods_code UNIQUE (method_code),
    CONSTRAINT CK_payment_methods_status CHECK (status IN (''ACTIVE'', ''INACTIVE''))
);';

EXEC sys.sp_executesql N'-- 37. payments
CREATE TABLE dbo.payments (
    payment_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id            BIGINT NOT NULL,
    payment_method_id   INT NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_payments_status DEFAULT (''PENDING''),
    provider            NVARCHAR(100) NULL,
    transaction_reference NVARCHAR(255) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_payments_created_at DEFAULT (SYSUTCDATETIME()),
    paid_at             DATETIME2(0) NULL,

    CONSTRAINT FK_payments_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id),
    CONSTRAINT FK_payments_method FOREIGN KEY (payment_method_id)
        REFERENCES dbo.payment_methods(payment_method_id),
    CONSTRAINT CK_payments_amount CHECK (amount >= 0),
    CONSTRAINT CK_payments_status CHECK (status IN (''PENDING'', ''PROCESSING'', ''PAID'', ''FAILED'', ''CANCELLED'', ''REFUNDED'', ''PARTIALLY_REFUNDED''))
);';

EXEC sys.sp_executesql N'-- 38. payment_transactions
CREATE TABLE dbo.payment_transactions (
    transaction_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    payment_id              BIGINT NOT NULL,
    event_type              VARCHAR(50) NOT NULL,
    provider_transaction_id NVARCHAR(255) NULL,
    amount                  DECIMAL(18,2) NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    provider_payload        NVARCHAR(MAX) NULL,
    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_payment_transactions_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_payment_transactions_payment FOREIGN KEY (payment_id)
        REFERENCES dbo.payments(payment_id),
    CONSTRAINT CK_payment_transactions_amount CHECK (amount >= 0)
);';

EXEC sys.sp_executesql N'-- 39. seller_payouts
CREATE TABLE dbo.seller_payouts (
    payout_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id             BIGINT NOT NULL,
    shop_order_id       BIGINT NOT NULL,
    gross_amount        DECIMAL(18,2) NOT NULL,
    commission_amount   DECIMAL(18,2) NOT NULL,
    net_amount          DECIMAL(18,2) NOT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_seller_payouts_status DEFAULT (''PENDING''),
    processed_at        DATETIME2(0) NULL,
    transaction_reference NVARCHAR(255) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_seller_payouts_created_at DEFAULT (SYSUTCDATETIME()),
    eligible_at         DATETIME2(0) NULL,
    funds_source        VARCHAR(20) NOT NULL CONSTRAINT DF_seller_payouts_funds_source DEFAULT (''UNCONFIRMED''),
    funds_confirmed_at  DATETIME2(0) NULL,
    funds_confirmed_by  BIGINT NULL,

    CONSTRAINT UQ_seller_payouts_shop_order UNIQUE (shop_order_id),
    CONSTRAINT UQ_seller_payouts_id_shop UNIQUE (payout_id, shop_id),
    CONSTRAINT FK_seller_payouts_funds_confirmed_by FOREIGN KEY (funds_confirmed_by) REFERENCES dbo.users(user_id),
    CONSTRAINT CK_seller_payouts_funds_source CHECK (funds_source IN (''UNCONFIRMED'', ''COD'', ''BANK_TRANSFER'', ''VNPAY'', ''MANUAL'', ''ONLINE'')),
    CONSTRAINT CK_seller_payouts_funds_confirmation CHECK (
        (funds_confirmed_at IS NULL AND funds_confirmed_by IS NULL)
        OR (funds_confirmed_at IS NOT NULL AND funds_source <> ''UNCONFIRMED'')
    ),
    CONSTRAINT FK_seller_payouts_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_seller_payouts_shop_order FOREIGN KEY (shop_order_id, shop_id)
        REFERENCES dbo.shop_orders(shop_order_id, shop_id),
    CONSTRAINT CK_seller_payouts_amounts CHECK (
        gross_amount >= 0
        AND commission_amount >= 0
        AND net_amount >= 0
        AND net_amount <= gross_amount
    ),
    CONSTRAINT CK_seller_payouts_status CHECK (status IN (''PENDING'', ''PROCESSING'', ''PAID'', ''FAILED'', ''CANCELLED''))
);';

EXEC sys.sp_executesql N'-- 40. deliveries
CREATE TABLE dbo.deliveries (
    delivery_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_order_id       BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_deliveries_status DEFAULT (''UNASSIGNED''),
    assigned_at         DATETIME2(0) NULL,
    picked_up_at        DATETIME2(0) NULL,
    delivered_at        DATETIME2(0) NULL,
    failure_reason      NVARCHAR(1000) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_deliveries_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_deliveries_updated_at DEFAULT (SYSUTCDATETIME()),
    redelivery_requested_at DATETIME2(0) NULL,
    redelivery_scheduled_at DATETIME2(0) NULL,

    CONSTRAINT UQ_deliveries_shop_order UNIQUE (shop_order_id),
    CONSTRAINT FK_deliveries_shop_order FOREIGN KEY (shop_order_id)
        REFERENCES dbo.shop_orders(shop_order_id),
    CONSTRAINT CK_deliveries_status CHECK (
    status IN (
        ''UNASSIGNED'',
        ''ASSIGNED'',
        ''ACCEPTED'',
        ''PICKED_UP'',
        ''OUT_FOR_DELIVERY'',
        ''REDELIVERY_SCHEDULED'',
        ''DELIVERED'',
        ''FAILED'',
        ''CANCELLED''
    )
),
    CONSTRAINT CK_deliveries_failed_reason CHECK (
    status <> ''FAILED''
    OR (
        failure_reason IS NOT NULL
        AND LEN(LTRIM(RTRIM(failure_reason))) > 0
    )
),
    CONSTRAINT CK_deliveries_failure_reason_not_blank CHECK (
    failure_reason IS NULL
    OR LEN(LTRIM(RTRIM(failure_reason))) > 0
),
    CONSTRAINT CK_deliveries_redelivery_schedule CHECK (
    (
        redelivery_requested_at IS NULL
        AND redelivery_scheduled_at IS NULL
    )
    OR
    (
        redelivery_requested_at IS NOT NULL
        AND redelivery_scheduled_at IS NOT NULL
        AND redelivery_scheduled_at > redelivery_requested_at
        AND CAST(DATEADD(HOUR, 7, redelivery_scheduled_at) AS DATE)
            = CAST(DATEADD(HOUR, 7, redelivery_requested_at) AS DATE)
        AND CAST(DATEADD(HOUR, 7, redelivery_scheduled_at) AS TIME(0))
            <= CAST(''23:00:00'' AS TIME(0))
    )
),
    CONSTRAINT CK_deliveries_redelivery_status CHECK (
    status <> ''REDELIVERY_SCHEDULED''
    OR (
        redelivery_requested_at IS NOT NULL
        AND redelivery_scheduled_at IS NOT NULL
    )
)
);';

EXEC sys.sp_executesql N'-- 41. delivery_assignments
CREATE TABLE dbo.delivery_assignments (
    assignment_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_id         BIGINT NOT NULL,
    delivery_staff_id   BIGINT NOT NULL,
    assigned_by         BIGINT NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_delivery_assignments_status DEFAULT (''ASSIGNED''),
    assigned_at         DATETIME2(0) NOT NULL CONSTRAINT DF_delivery_assignments_assigned_at DEFAULT (SYSUTCDATETIME()),
    accepted_at         DATETIME2(0) NULL,

    CONSTRAINT FK_delivery_assignments_delivery FOREIGN KEY (delivery_id)
        REFERENCES dbo.deliveries(delivery_id),
    CONSTRAINT FK_delivery_assignments_staff FOREIGN KEY (delivery_staff_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_delivery_assignments_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_delivery_assignments_status CHECK (status IN (''ASSIGNED'', ''ACCEPTED'', ''REJECTED'', ''CANCELLED'', ''COMPLETED''))
);';

EXEC sys.sp_executesql N'-- 42. delivery_status_history
CREATE TABLE dbo.delivery_status_history (
    history_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_id         BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL,
    changed_by          BIGINT NULL,
    note                NVARCHAR(1000) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_delivery_status_history_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_delivery_status_history_delivery FOREIGN KEY (delivery_id)
        REFERENCES dbo.deliveries(delivery_id),
    CONSTRAINT FK_delivery_status_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_delivery_status_history_status CHECK (
    status IN (
        ''UNASSIGNED'',
        ''ASSIGNED'',
        ''ACCEPTED'',
        ''PICKED_UP'',
        ''OUT_FOR_DELIVERY'',
        ''REDELIVERY_SCHEDULED'',
        ''DELIVERED'',
        ''FAILED'',
        ''CANCELLED''
    )
)
);';

EXEC sys.sp_executesql N'-- 43. loyalty_accounts
CREATE TABLE dbo.loyalty_accounts (
    loyalty_account_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    reward_balance      DECIMAL(18,2) NOT NULL CONSTRAINT DF_loyalty_vnd_account_balance DEFAULT (0),
    total_earned_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_loyalty_vnd_account_earned DEFAULT (0),
    total_redeemed_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_loyalty_vnd_account_redeemed DEFAULT (0),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_loyalty_accounts_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_loyalty_accounts_user UNIQUE (user_id),
    CONSTRAINT FK_loyalty_accounts_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_loyalty_vnd_balances CHECK (
        reward_balance >= 0 AND reward_balance = FLOOR(reward_balance)
        AND total_earned_amount >= 0 AND total_earned_amount = FLOOR(total_earned_amount)
        AND total_redeemed_amount >= 0 AND total_redeemed_amount = FLOOR(total_redeemed_amount)
    )
);';

EXEC sys.sp_executesql N'-- 44. loyalty_transactions
CREATE TABLE dbo.loyalty_transactions (
    loyalty_transaction_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    loyalty_account_id     BIGINT NOT NULL,
    order_id               BIGINT NULL,
    transaction_type       VARCHAR(30) NOT NULL,
    amount                 DECIMAL(18,2) NOT NULL,
    balance_after_amount   DECIMAL(18,2) NOT NULL,
    note                   NVARCHAR(500) NULL,
    created_at             DATETIME2(0) NOT NULL CONSTRAINT DF_loyalty_transactions_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_loyalty_transactions_account FOREIGN KEY (loyalty_account_id)
        REFERENCES dbo.loyalty_accounts(loyalty_account_id),
    CONSTRAINT FK_loyalty_transactions_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id),
    CONSTRAINT CK_loyalty_vnd_transactions CHECK (
        amount <> 0 AND amount = FLOOR(amount)
        AND balance_after_amount >= 0 AND balance_after_amount = FLOOR(balance_after_amount)
        AND transaction_type IN (''EARN'', ''REDEEM'', ''REFUND'', ''ADJUSTMENT'')
        AND (transaction_type <> ''EARN'' OR amount > 0)
        AND (transaction_type <> ''REDEEM'' OR amount < 0)
    )
);';

EXEC sys.sp_executesql N'-- 61. loyalty_rules: amounts are whole VND; earning_percent applies to eligible merchandise.
CREATE TABLE dbo.loyalty_rules (
    rule_id BIGINT NOT NULL CONSTRAINT PK_loyalty_rules_vnd PRIMARY KEY,
    earning_percent DECIMAL(5,2) NOT NULL CONSTRAINT DF_loyalty_vnd_earning DEFAULT (1.00),
    earning_enabled BIT NOT NULL CONSTRAINT DF_loyalty_vnd_earn_enabled DEFAULT (1),
    min_redemption_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_loyalty_vnd_min DEFAULT (1000),
    max_redemption_percent INT NOT NULL CONSTRAINT DF_loyalty_vnd_max DEFAULT (20),
    redemption_enabled BIT NOT NULL CONSTRAINT DF_loyalty_vnd_redeem_enabled DEFAULT (1),
    updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_loyalty_vnd_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loyalty_vnd_rule_id CHECK (rule_id = 1),
    CONSTRAINT CK_loyalty_vnd_earning CHECK (earning_percent > 0 AND earning_percent <= 100),
    CONSTRAINT CK_loyalty_vnd_min CHECK (min_redemption_amount >= 1 AND min_redemption_amount = FLOOR(min_redemption_amount)),
    CONSTRAINT CK_loyalty_vnd_max CHECK (max_redemption_percent BETWEEN 1 AND 100)
);';

EXEC sys.sp_executesql N'-- 62. refund_requests. A rejected/cancelled request can be corrected and resubmitted.
CREATE TABLE dbo.refund_requests (
    refund_request_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_refund_requests PRIMARY KEY,
    order_id BIGINT NOT NULL,
    shop_order_id BIGINT NULL,
    bank_name NVARCHAR(120) NOT NULL,
    bank_account_number NVARCHAR(50) NOT NULL,
    account_holder_name NVARCHAR(150) NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL,
    submitted_at DATETIME2(0) NOT NULL CONSTRAINT DF_refund_requests_submitted_at DEFAULT SYSUTCDATETIME(),
    status VARCHAR(20) NOT NULL CONSTRAINT DF_refund_requests_status DEFAULT (''PENDING''),
    CONSTRAINT FK_refund_requests_order FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id),
    CONSTRAINT FK_refund_requests_shop_order FOREIGN KEY (shop_order_id, order_id)
        REFERENCES dbo.shop_orders(shop_order_id, order_id),
    CONSTRAINT CK_refund_requests_amount CHECK (refund_amount > 0 AND refund_amount = FLOOR(refund_amount)),
    CONSTRAINT CK_refund_requests_status CHECK (status IN (''PENDING'', ''PROCESSING'', ''PAID'', ''REJECTED'', ''CANCELLED''))
);';

EXEC sys.sp_executesql N'CREATE UNIQUE INDEX UQ_refund_requests_order ON dbo.refund_requests(order_id) WHERE shop_order_id IS NULL;
CREATE UNIQUE INDEX UQ_refund_requests_shop_order ON dbo.refund_requests(shop_order_id) WHERE shop_order_id IS NOT NULL;
CREATE INDEX IX_refund_requests_order_status ON dbo.refund_requests(order_id, status) INCLUDE (shop_order_id, refund_amount);';

EXEC sys.sp_executesql N'-- 63. shop_payout_accounts
CREATE TABLE dbo.shop_payout_accounts (
    shop_id BIGINT NOT NULL CONSTRAINT PK_shop_payout_accounts PRIMARY KEY,
    bank_code VARCHAR(30) NOT NULL,
    bank_name NVARCHAR(100) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_holder NVARCHAR(150) NOT NULL,
    verified BIT NOT NULL CONSTRAINT DF_shop_payout_accounts_verified DEFAULT (0),
    updated_by BIGINT NULL,
    updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_shop_payout_accounts_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_shop_payout_accounts_shops FOREIGN KEY (shop_id) REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_shop_payout_accounts_users FOREIGN KEY (updated_by) REFERENCES dbo.users(user_id),
    CONSTRAINT CK_shop_payout_accounts_not_blank CHECK (
        LEN(LTRIM(RTRIM(bank_code))) > 0 AND LEN(LTRIM(RTRIM(bank_name))) > 0
        AND LEN(LTRIM(RTRIM(account_number))) > 0 AND LEN(LTRIM(RTRIM(account_holder))) > 0)
);';

EXEC sys.sp_executesql N'-- 64. seller_payout_batches
CREATE TABLE dbo.seller_payout_batches (
    batch_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_seller_payout_batches PRIMARY KEY,
    scheduled_slot VARCHAR(40) NOT NULL,
    scheduled_at DATETIME2(0) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_seller_payout_batches_status DEFAULT (''READY''),
    total_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_seller_payout_batches_total DEFAULT (0),
    payout_count INT NOT NULL CONSTRAINT DF_seller_payout_batches_payout_count DEFAULT (0),
    shop_count INT NOT NULL CONSTRAINT DF_seller_payout_batches_shop_count DEFAULT (0),
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_seller_payout_batches_created_at DEFAULT SYSUTCDATETIME(),
    completed_at DATETIME2(0) NULL,
    CONSTRAINT UQ_seller_payout_batches_slot UNIQUE (scheduled_slot),
    CONSTRAINT CK_seller_payout_batches_status CHECK (status IN (''READY'', ''PARTIAL'', ''PAID'', ''FAILED'')),
    CONSTRAINT CK_seller_payout_batches_totals CHECK (total_amount >= 0 AND payout_count >= 0 AND shop_count >= 0)
);';

EXEC sys.sp_executesql N'-- 65. seller_payout_batch_items: failed attempts remain auditable and may be retried.
CREATE TABLE dbo.seller_payout_batch_items (
    batch_item_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_seller_payout_batch_items PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    payout_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    shop_name_snapshot NVARCHAR(200) NOT NULL,
    bank_name_snapshot NVARCHAR(100) NOT NULL,
    bank_code_snapshot VARCHAR(30) NOT NULL,
    account_number_snapshot VARCHAR(50) NOT NULL,
    account_holder_snapshot NVARCHAR(150) NOT NULL,
    transfer_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_seller_payout_batch_items_status DEFAULT (''PENDING''),
    transfer_reference NVARCHAR(255) NULL,
    failure_reason NVARCHAR(1000) NULL,
    paid_at DATETIME2(0) NULL,
    result_by BIGINT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_seller_payout_batch_items_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_seller_payout_batch_items_batches FOREIGN KEY (batch_id) REFERENCES dbo.seller_payout_batches(batch_id),
    CONSTRAINT FK_seller_payout_batch_items_payouts FOREIGN KEY (payout_id, shop_id) REFERENCES dbo.seller_payouts(payout_id, shop_id),
    CONSTRAINT FK_seller_payout_batch_items_shops FOREIGN KEY (shop_id) REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_seller_payout_batch_items_users FOREIGN KEY (result_by) REFERENCES dbo.users(user_id),
    CONSTRAINT UQ_seller_payout_batch_items_batch_payout UNIQUE (batch_id, payout_id),
    CONSTRAINT CK_seller_payout_batch_items_status CHECK (status IN (''PENDING'', ''PAID'', ''FAILED'')),
    CONSTRAINT CK_seller_payout_batch_items_amount CHECK (transfer_amount > 0 AND transfer_amount = FLOOR(transfer_amount)),
    CONSTRAINT CK_seller_payout_batch_items_paid CHECK (
        status <> ''PAID'' OR (paid_at IS NOT NULL AND transfer_reference IS NOT NULL AND LEN(LTRIM(RTRIM(transfer_reference))) > 0))
);';

EXEC sys.sp_executesql N'CREATE UNIQUE INDEX UX_seller_payout_batch_items_active_payout
    ON dbo.seller_payout_batch_items(payout_id) WHERE status IN (''PENDING'', ''PAID'');
CREATE INDEX IX_seller_payout_batch_items_batch_shop ON dbo.seller_payout_batch_items(batch_id, shop_id, status);
CREATE INDEX IX_seller_payouts_batch_eligibility ON dbo.seller_payouts(status, eligible_at, funds_confirmed_at, shop_id);
CREATE UNIQUE INDEX UX_loyalty_vnd_earn ON dbo.loyalty_transactions(loyalty_account_id, order_id)
    WHERE transaction_type = ''EARN'' AND order_id IS NOT NULL;
CREATE UNIQUE INDEX UX_loyalty_vnd_redeem ON dbo.loyalty_transactions(loyalty_account_id, order_id)
    WHERE transaction_type = ''REDEEM'' AND order_id IS NOT NULL;';

EXEC sys.sp_executesql N'-- 45. subscription_plans
CREATE TABLE dbo.subscription_plans (
    plan_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    name                NVARCHAR(100) NOT NULL,
    description         NVARCHAR(1000) NULL,
    price               DECIMAL(18,2) NOT NULL,
    billing_period      VARCHAR(20) NOT NULL,
    benefits            NVARCHAR(MAX) NULL,
    max_products        INT NOT NULL CONSTRAINT DF_subscription_plans_max_products DEFAULT (20),
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_subscription_plans_status DEFAULT (''ACTIVE''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_subscription_plans_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_subscription_plans_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT UQ_subscription_plans_name UNIQUE (name),
    CONSTRAINT CK_subscription_plans_price CHECK (price >= 0),
    CONSTRAINT CK_subscription_plans_max_products CHECK (max_products > 0),
    CONSTRAINT CK_subscription_plans_period CHECK (billing_period IN (''MONTHLY'', ''QUARTERLY'', ''YEARLY'')),
    CONSTRAINT CK_subscription_plans_status CHECK (status IN (''ACTIVE'', ''INACTIVE''))
);';

EXEC sys.sp_executesql N'-- 46. shop_subscriptions
CREATE TABLE dbo.shop_subscriptions (
    subscription_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id                 BIGINT NOT NULL,
    plan_id                 BIGINT NOT NULL,
    start_at                DATETIME2(0) NOT NULL,
    end_at                  DATETIME2(0) NOT NULL,
    status                  VARCHAR(30) NOT NULL CONSTRAINT DF_shop_subscriptions_status DEFAULT (''ACTIVE''),
    auto_renew              BIT NOT NULL CONSTRAINT DF_shop_subscriptions_auto_renew DEFAULT (0),
    previous_subscription_id BIGINT NULL,
    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_shop_subscriptions_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_shop_subscriptions_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_shop_subscriptions_plan FOREIGN KEY (plan_id)
        REFERENCES dbo.subscription_plans(plan_id),
    CONSTRAINT FK_shop_subscriptions_previous FOREIGN KEY (previous_subscription_id)
        REFERENCES dbo.shop_subscriptions(subscription_id),
    CONSTRAINT CK_shop_subscriptions_period CHECK (end_at > start_at),
    CONSTRAINT CK_shop_subscriptions_status CHECK (status IN (''ACTIVE'', ''EXPIRED'', ''CANCELLED'', ''UPGRADED''))
);';

EXEC sys.sp_executesql N'-- 47. reviews
CREATE TABLE dbo.reviews (
    review_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    product_id          BIGINT NULL,
    shop_id             BIGINT NULL,
    order_item_id       BIGINT NULL,
    shop_order_id       BIGINT NULL,
    rating              TINYINT NOT NULL,
    content             NVARCHAR(3000) NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_reviews_status DEFAULT (''PENDING''),
    moderated_by        BIGINT NULL,
    moderated_at        DATETIME2(0) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_reviews_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_reviews_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_reviews_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_reviews_product FOREIGN KEY (product_id)
        REFERENCES dbo.products(product_id),
    CONSTRAINT FK_reviews_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_reviews_order_item FOREIGN KEY (order_item_id)
        REFERENCES dbo.order_items(order_item_id),
    CONSTRAINT FK_reviews_shop_order FOREIGN KEY (shop_order_id)
        REFERENCES dbo.shop_orders(shop_order_id),
    CONSTRAINT FK_reviews_moderated_by FOREIGN KEY (moderated_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT CK_reviews_status CHECK (status IN (''PENDING'', ''APPROVED'', ''REJECTED'', ''REMOVED'')),
    CONSTRAINT CK_reviews_target CHECK (
        (product_id IS NOT NULL AND shop_id IS NULL)
        OR
        (product_id IS NULL AND shop_id IS NOT NULL)
    ),
    CONSTRAINT CK_reviews_purchase_reference CHECK (
        (product_id IS NOT NULL AND order_item_id IS NOT NULL AND shop_order_id IS NULL)
        OR
        (shop_id IS NOT NULL AND shop_order_id IS NOT NULL AND order_item_id IS NULL)
    )
);';

EXEC sys.sp_executesql N'-- 48. review_reports
CREATE TABLE dbo.review_reports (
    report_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    review_id           BIGINT NOT NULL,
    reporter_user_id    BIGINT NOT NULL,
    reason              VARCHAR(100) NOT NULL,
    description         NVARCHAR(1000) NULL,
    status              VARCHAR(30) NOT NULL CONSTRAINT DF_review_reports_status DEFAULT (''PENDING''),
    reviewed_by         BIGINT NULL,
    reviewed_at         DATETIME2(0) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_review_reports_created_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT FK_review_reports_review FOREIGN KEY (review_id)
        REFERENCES dbo.reviews(review_id),
    CONSTRAINT FK_review_reports_reporter FOREIGN KEY (reporter_user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_review_reports_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_review_reports_status CHECK (status IN (''PENDING'', ''REVIEWED'', ''DISMISSED'', ''ACTION_TAKEN''))
);';

EXEC sys.sp_executesql N'-- 49. notifications
CREATE TABLE dbo.notifications (
    notification_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    type                VARCHAR(80) NOT NULL,
    title               NVARCHAR(255) NOT NULL,
    content             NVARCHAR(2000) NOT NULL,
    entity_type         VARCHAR(80) NULL,
    entity_id           BIGINT NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT (SYSUTCDATETIME()),
    read_at             DATETIME2(0) NULL,

    CONSTRAINT FK_notifications_user FOREIGN KEY (user_id)
        REFERENCES dbo.users(user_id)
);';

EXEC sys.sp_executesql N'-- 50. notification_deliveries
CREATE TABLE dbo.notification_deliveries (
    notification_delivery_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    notification_id          BIGINT NOT NULL,
    channel                  VARCHAR(30) NOT NULL,
    status                   VARCHAR(30) NOT NULL CONSTRAINT DF_notification_deliveries_status DEFAULT (''PENDING''),
    sent_at                  DATETIME2(0) NULL,
    error_message            NVARCHAR(1000) NULL,

    CONSTRAINT UQ_notification_deliveries_notification_channel UNIQUE (notification_id, channel),
    CONSTRAINT FK_notification_deliveries_notification FOREIGN KEY (notification_id)
        REFERENCES dbo.notifications(notification_id),
    CONSTRAINT CK_notification_deliveries_channel CHECK (channel IN (''IN_APP'', ''EMAIL'')),
    CONSTRAINT CK_notification_deliveries_status CHECK (status IN (''PENDING'', ''SENT'', ''FAILED''))
);';

EXEC sys.sp_executesql N'-- 51. conversations
CREATE TABLE dbo.conversations (
    conversation_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id         BIGINT NOT NULL,
    shop_id             BIGINT NOT NULL,
    order_id            BIGINT NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_conversations_created_at DEFAULT (SYSUTCDATETIME()),
    closed_at           DATETIME2(0) NULL,

    CONSTRAINT FK_conversations_customer FOREIGN KEY (customer_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT FK_conversations_shop FOREIGN KEY (shop_id)
        REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_conversations_order FOREIGN KEY (order_id)
        REFERENCES dbo.orders(order_id)
);';

EXEC sys.sp_executesql N'-- 52. messages
CREATE TABLE dbo.messages (
    message_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    conversation_id     BIGINT NOT NULL,
    sender_user_id      BIGINT NOT NULL,
    content             NVARCHAR(MAX) NOT NULL,
    message_type        VARCHAR(30) NOT NULL CONSTRAINT DF_messages_type DEFAULT (''TEXT''),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_messages_created_at DEFAULT (SYSUTCDATETIME()),
    read_at             DATETIME2(0) NULL,

    CONSTRAINT FK_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES dbo.conversations(conversation_id),
    CONSTRAINT FK_messages_sender FOREIGN KEY (sender_user_id)
        REFERENCES dbo.users(user_id),
    CONSTRAINT CK_messages_type CHECK (message_type IN (''TEXT'', ''IMAGE'', ''FILE'', ''SYSTEM''))
);';

EXEC sys.sp_executesql N'-- 53. stock_allocations
CREATE TABLE [dbo].[stock_allocations] (
    [allocation_id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_item_id] BIGINT NOT NULL,
    [batch_id] BIGINT NOT NULL,
    [quantity] INT NOT NULL,
    [status] VARCHAR(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,

    CONSTRAINT [PK__stock_al__5DFAFF306BFEF9FF]
        PRIMARY KEY CLUSTERED ([allocation_id] ASC) ON [PRIMARY],
    CONSTRAINT [CK__stock_all__quant__4924D839]
        CHECK ([quantity]>(0)),
    CONSTRAINT [CK__stock_all__statu__4A18FC72]
        CHECK ([status]=''RELEASED'' OR [status]=''SOLD'' OR [status]=''RESERVED''),
    CONSTRAINT [FK__stock_all__order__473C8FC7]
        FOREIGN KEY ([order_item_id]) REFERENCES [dbo].[order_items] ([order_item_id]),
    CONSTRAINT [FK__stock_all__batch__4830B400]
        FOREIGN KEY ([batch_id]) REFERENCES [dbo].[inventory_batches] ([batch_id])
) ON [PRIMARY];';

EXEC sys.sp_executesql N'-- 54. stored_files
CREATE TABLE [dbo].[stored_files] (
    [file_id] VARCHAR(36) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [owner_id] BIGINT NOT NULL,
    [original_name] NVARCHAR(255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [content_type] VARCHAR(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [size_bytes] BIGINT NOT NULL,
    [private_file] BIT NOT NULL,
    [created_at] DATETIME2(0) NOT NULL,

    CONSTRAINT [PK__stored_f__07D884C641F47191]
        PRIMARY KEY CLUSTERED ([file_id] ASC) ON [PRIMARY],
    CONSTRAINT [FK__stored_fi__owner__4FD1D5C8]
        FOREIGN KEY ([owner_id]) REFERENCES [dbo].[users] ([user_id])
) ON [PRIMARY];';

EXEC sys.sp_executesql N'-- 55. audit_events
CREATE TABLE [dbo].[audit_events] (
    [audit_id] BIGINT IDENTITY(1,1) NOT NULL,
    [actor_id] BIGINT NOT NULL,
    [action] VARCHAR(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [entity_type] VARCHAR(100) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [entity_id] BIGINT NOT NULL,
    [reason] NVARCHAR(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [created_at] DATETIME2(0) NOT NULL,

    CONSTRAINT [PK__audit_ev__5AF33E33579623BC]
        PRIMARY KEY CLUSTERED ([audit_id] ASC) ON [PRIMARY],
    CONSTRAINT [FK__audit_eve__actor__4CF5691D]
        FOREIGN KEY ([actor_id]) REFERENCES [dbo].[users] ([user_id])
) ON [PRIMARY];';

EXEC sys.sp_executesql N'-- 56. subscription_payments
CREATE TABLE [dbo].[subscription_payments] (
    [subscription_payment_id] BIGINT IDENTITY(1,1) NOT NULL,
    [shop_id] BIGINT NOT NULL,
    [plan_id] BIGINT NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [action] VARCHAR(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [auto_renew] BIT NOT NULL,
    [status] VARCHAR(20) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
    [transaction_reference] VARCHAR(255) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [note] NVARCHAR(1000) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
    [reviewed_by] BIGINT NULL,
    [subscription_id] BIGINT NULL,
    [created_at] DATETIME2(0) NOT NULL,
    [reviewed_at] DATETIME2(0) NULL,

    CONSTRAINT [PK__subscrip__F07FB720CE17E847]
        PRIMARY KEY CLUSTERED ([subscription_payment_id] ASC) ON [PRIMARY],
    CONSTRAINT [CK__subscript__actio__558AAF1E]
        CHECK ([action]=''UPGRADE'' OR [action]=''RENEW'' OR [action]=''SUBSCRIBE''),
    CONSTRAINT [CK__subscript__amoun__54968AE5]
        CHECK ([amount]>=(0)),
    CONSTRAINT [CK__subscript__statu__567ED357]
        CHECK ([status]=''FAILED'' OR [status]=''PAID'' OR [status]=''PENDING''),
    CONSTRAINT [FK__subscript__shop___52AE4273]
        FOREIGN KEY ([shop_id]) REFERENCES [dbo].[shops] ([shop_id]),
    CONSTRAINT [FK__subscript__plan___53A266AC]
        FOREIGN KEY ([plan_id]) REFERENCES [dbo].[subscription_plans] ([plan_id]),
    CONSTRAINT [FK__subscript__revie__5772F790]
        FOREIGN KEY ([reviewed_by]) REFERENCES [dbo].[users] ([user_id]),
    CONSTRAINT [FK__subscript__subsc__58671BC9]
        FOREIGN KEY ([subscription_id]) REFERENCES [dbo].[shop_subscriptions] ([subscription_id])
) ON [PRIMARY];';

EXEC sys.sp_executesql N'-- 57. product_review_history
CREATE TABLE dbo.product_review_history (
      review_id BIGINT IDENTITY(1,1) PRIMARY KEY,
      product_id BIGINT NOT NULL REFERENCES dbo.products(product_id),
      decision VARCHAR(20) NOT NULL CHECK (decision IN (''APPROVED'', ''REJECTED'')),
      reviewed_by BIGINT NOT NULL REFERENCES dbo.users(user_id),
      note NVARCHAR(1000) NULL,
      created_at DATETIME2(0) NOT NULL CONSTRAINT DF_product_review_history_created DEFAULT SYSUTCDATETIME()
    );';

EXEC sys.sp_executesql N'-- 58. order_item_preparation_checks
CREATE TABLE dbo.order_item_preparation_checks (
      order_item_id BIGINT PRIMARY KEY REFERENCES dbo.order_items(order_item_id),
      checked_by BIGINT NOT NULL REFERENCES dbo.users(user_id),
      checked_at DATETIME2(0) NOT NULL CONSTRAINT DF_preparation_checks_created DEFAULT SYSUTCDATETIME(),
      note NVARCHAR(1000) NULL
    );';

EXEC sys.sp_executesql N'-- 59. batch_markdowns
CREATE TABLE dbo.batch_markdowns (
        markdown_id BIGINT IDENTITY PRIMARY KEY,
        batch_id BIGINT NOT NULL REFERENCES dbo.inventory_batches(batch_id),
        price DECIMAL(18,2) NOT NULL CONSTRAINT CK_batch_markdowns_price CHECK(price>=1 AND price=FLOOR(price)),
        start_date DATE NOT NULL,
        end_date DATE NOT NULL,
        status VARCHAR(20) NOT NULL CHECK(status IN (''ACTIVE'',''CANCELLED'')),
        note NVARCHAR(1000) NOT NULL,
        created_by BIGINT NOT NULL REFERENCES dbo.users(user_id),
        created_at DATETIME2(0) NOT NULL,
        CHECK(end_date>=start_date)
    );';

EXEC sys.sp_executesql N'-- 60. shop_locations
CREATE TABLE dbo.shop_locations
(
    shop_id BIGINT NOT NULL,
    province_id INT NULL,
    province NVARCHAR(100) NOT NULL,
    district_id INT NULL,
    district NVARCHAR(100) NULL,
    ward_id INT NULL,
    ward NVARCHAR(100) NOT NULL,
    address_line NVARCHAR(500) NOT NULL,
    formatted_address NVARCHAR(1000) NULL,
    latitude DECIMAL(9,6) NULL,
    longitude DECIMAL(10,6) NULL,
    created_at DATETIME2(0) NOT NULL
        CONSTRAINT DF_shop_locations_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(0) NOT NULL
        CONSTRAINT DF_shop_locations_updated_at DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_shop_locations PRIMARY KEY (shop_id),
    CONSTRAINT FK_shop_locations_shop
        FOREIGN KEY (shop_id) REFERENCES dbo.shops(shop_id),

    CONSTRAINT CK_shop_locations_province_id
        CHECK (province_id IS NULL OR province_id > 0),
    CONSTRAINT CK_shop_locations_district_id
        CHECK (district_id IS NULL OR district_id > 0),
    CONSTRAINT CK_shop_locations_ward_id
        CHECK (ward_id IS NULL OR ward_id > 0),
    CONSTRAINT CK_shop_locations_province_not_blank
        CHECK (LEN(LTRIM(RTRIM(province))) > 0),
    CONSTRAINT CK_shop_locations_district_not_blank
        CHECK (
            district IS NULL
            OR LEN(LTRIM(RTRIM(district))) > 0
        ),
    CONSTRAINT CK_shop_locations_ward_not_blank
        CHECK (LEN(LTRIM(RTRIM(ward))) > 0),
    CONSTRAINT CK_shop_locations_address_line_not_blank
        CHECK (LEN(LTRIM(RTRIM(address_line))) > 0),
    CONSTRAINT CK_shop_locations_formatted_address_not_blank
        CHECK (
            formatted_address IS NULL
            OR LEN(LTRIM(RTRIM(formatted_address))) > 0
        ),
    CONSTRAINT CK_shop_locations_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90.000000 AND 90.000000),
    CONSTRAINT CK_shop_locations_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180.000000 AND 180.000000),
    CONSTRAINT CK_shop_locations_coordinate_pair
        CHECK (
            (latitude IS NULL AND longitude IS NULL)
            OR
            (latitude IS NOT NULL AND longitude IS NOT NULL)
        )
);';

EXEC sys.sp_executesql N'-- Index phuc vu truy van va rang buoc duy nhat.
CREATE UNIQUE INDEX UX_users_phone
ON dbo.users(phone)
WHERE phone IS NOT NULL;
CREATE INDEX IX_user_roles_role_id ON dbo.user_roles(role_id);
CREATE INDEX IX_addresses_user_id ON dbo.addresses(user_id);
CREATE UNIQUE INDEX UX_addresses_one_default_per_user
ON dbo.addresses(user_id)
WHERE is_default = 1;
CREATE INDEX IX_seller_applications_user_id ON dbo.seller_applications(user_id);
CREATE INDEX IX_seller_applications_status ON dbo.seller_applications(status);
CREATE INDEX IX_seller_application_documents_application_id
ON dbo.seller_application_documents(application_id);
CREATE INDEX IX_seller_application_status_history_application_id
ON dbo.seller_application_status_history(application_id);
CREATE INDEX IX_shops_shop_name ON dbo.shops(shop_name);
CREATE INDEX IX_shop_members_user_id ON dbo.shop_members(user_id);
CREATE UNIQUE INDEX UX_shop_members_one_active_per_user
ON dbo.shop_members(user_id) WHERE status = ''ACTIVE'';
CREATE UNIQUE INDEX UX_shop_members_one_active_per_shop
ON dbo.shop_members(shop_id) WHERE status = ''ACTIVE'';
CREATE INDEX IX_favorite_shops_shop_id ON dbo.favorite_shops(shop_id);
CREATE INDEX IX_categories_parent_category_id ON dbo.categories(parent_category_id);
CREATE INDEX IX_products_shop_id ON dbo.products(shop_id);
CREATE INDEX IX_products_name ON dbo.products(name);
CREATE UNIQUE INDEX UX_products_shop_name ON dbo.products(shop_id, normalized_name);
CREATE INDEX IX_products_approval_status ON dbo.products(approval_status);
CREATE INDEX IX_product_categories_category_id ON dbo.product_categories(category_id);
CREATE INDEX IX_product_variants_price ON dbo.product_variants(price);
CREATE INDEX IX_product_images_product_id ON dbo.product_images(product_id);
CREATE INDEX IX_product_images_variant_id ON dbo.product_images(variant_id);
CREATE UNIQUE INDEX UX_product_images_one_primary_product
ON dbo.product_images(product_id) WHERE variant_id IS NULL AND is_primary = 1;
CREATE UNIQUE INDEX UX_product_images_one_primary_variant
ON dbo.product_images(variant_id) WHERE variant_id IS NOT NULL AND is_primary = 1;
CREATE INDEX IX_price_history_variant_period
ON dbo.price_history(variant_id, effective_from DESC);
CREATE INDEX IX_inventory_batches_expiry_date ON dbo.inventory_batches(expiry_date);
CREATE INDEX IX_inventory_transactions_variant_id ON dbo.inventory_transactions(variant_id);
CREATE INDEX IX_inventory_transactions_batch_id ON dbo.inventory_transactions(batch_id);
CREATE INDEX IX_inventory_transactions_created_at ON dbo.inventory_transactions(created_at DESC);
CREATE INDEX IX_carts_user_id ON dbo.carts(user_id);
CREATE UNIQUE INDEX UX_carts_one_active_per_user
ON dbo.carts(user_id)
WHERE status = ''ACTIVE'';
CREATE INDEX IX_cart_items_variant_id ON dbo.cart_items(variant_id);
CREATE INDEX IX_orders_customer_id ON dbo.orders(customer_id);
CREATE INDEX IX_orders_status ON dbo.orders(status);
CREATE INDEX IX_orders_placed_at ON dbo.orders(placed_at DESC);
CREATE INDEX IX_shop_orders_order_id ON dbo.shop_orders(order_id);
CREATE INDEX IX_shop_orders_shop_id ON dbo.shop_orders(shop_id);
CREATE INDEX IX_shop_orders_status ON dbo.shop_orders(status);
CREATE INDEX IX_order_items_shop_order_id ON dbo.order_items(shop_order_id);
CREATE INDEX IX_order_items_variant_id ON dbo.order_items(variant_id);
CREATE INDEX IX_order_status_history_shop_order_id
ON dbo.order_status_history(shop_order_id, created_at DESC);
CREATE INDEX IX_promotions_shop_id ON dbo.promotions(shop_id);
CREATE INDEX IX_promotions_period ON dbo.promotions(start_at, end_at);
CREATE INDEX IX_promotion_products_product_id ON dbo.promotion_products(product_id);
CREATE INDEX IX_promotion_variants_variant_id ON dbo.promotion_variants(variant_id);
CREATE INDEX IX_promotion_categories_category_id ON dbo.promotion_categories(category_id);
CREATE INDEX IX_coupons_promotion_id ON dbo.coupons(promotion_id);
CREATE INDEX IX_coupon_usages_user_id ON dbo.coupon_usages(user_id);
CREATE INDEX IX_coupon_usages_order_id ON dbo.coupon_usages(order_id);
CREATE INDEX IX_order_discounts_order_id ON dbo.order_discounts(order_id);
CREATE INDEX IX_order_discounts_shop_order_id ON dbo.order_discounts(shop_order_id);
CREATE INDEX IX_order_discounts_order_item_id ON dbo.order_discounts(order_item_id);
CREATE INDEX IX_payments_order_id ON dbo.payments(order_id);
CREATE INDEX IX_payments_status ON dbo.payments(status);
CREATE INDEX IX_payment_transactions_payment_id
ON dbo.payment_transactions(payment_id, created_at DESC);
CREATE INDEX IX_seller_payouts_shop_id ON dbo.seller_payouts(shop_id);
CREATE INDEX IX_deliveries_status ON dbo.deliveries(status);
CREATE INDEX IX_delivery_assignments_delivery_id ON dbo.delivery_assignments(delivery_id);
CREATE INDEX IX_delivery_status_history_delivery_id
ON dbo.delivery_status_history(delivery_id, created_at DESC);
CREATE INDEX IX_loyalty_transactions_account_id
ON dbo.loyalty_transactions(loyalty_account_id, created_at DESC);
CREATE INDEX IX_shop_subscriptions_shop_id ON dbo.shop_subscriptions(shop_id);
CREATE INDEX IX_shop_subscriptions_plan_id ON dbo.shop_subscriptions(plan_id);
CREATE INDEX IX_reviews_product_id ON dbo.reviews(product_id);
CREATE INDEX IX_reviews_shop_id ON dbo.reviews(shop_id);
CREATE INDEX IX_reviews_user_id ON dbo.reviews(user_id);
CREATE INDEX IX_reviews_status ON dbo.reviews(status);
CREATE UNIQUE INDEX UX_reviews_user_order_item
ON dbo.reviews(user_id, order_item_id)
WHERE order_item_id IS NOT NULL;
CREATE UNIQUE INDEX UX_reviews_user_shop_order
ON dbo.reviews(user_id, shop_order_id)
WHERE shop_order_id IS NOT NULL;
CREATE INDEX IX_review_reports_review_id ON dbo.review_reports(review_id);
CREATE INDEX IX_review_reports_status ON dbo.review_reports(status);
CREATE INDEX IX_notifications_user_read
ON dbo.notifications(user_id, read_at, created_at DESC);
CREATE INDEX IX_conversations_customer_id ON dbo.conversations(customer_id);
CREATE INDEX IX_conversations_shop_id ON dbo.conversations(shop_id);
CREATE INDEX IX_conversations_order_id ON dbo.conversations(order_id);
CREATE INDEX IX_messages_conversation_created_at
ON dbo.messages(conversation_id, created_at);
CREATE INDEX IX_stock_allocations_order_item_status
    ON dbo.stock_allocations(order_item_id, status) INCLUDE (batch_id, quantity);
CREATE INDEX IX_stock_allocations_batch_status
    ON dbo.stock_allocations(batch_id, status) INCLUDE (order_item_id, quantity);
CREATE INDEX IX_audit_events_entity_time
    ON dbo.audit_events(entity_type, entity_id, created_at DESC) INCLUDE (actor_id, action);
CREATE INDEX IX_pi_markdown_batch ON dbo.batch_markdowns(batch_id,status,end_date);
CREATE UNIQUE INDEX UX_shops_application_id ON dbo.shops(application_id) WHERE application_id IS NOT NULL;
CREATE INDEX IX_password_reset_tokens_user_type_created ON dbo.password_reset_tokens(user_id, token_type, created_at DESC) INCLUDE (expires_at, used_at, attempt_count);
CREATE INDEX IX_inventory_batches_fefo ON dbo.inventory_batches(variant_id, expiry_date, batch_id) INCLUDE (quantity_on_hand, reserved_quantity, status);
CREATE INDEX IX_delivery_assignments_staff_status ON dbo.delivery_assignments(delivery_staff_id, status, assigned_at DESC) INCLUDE (delivery_id);
CREATE UNIQUE INDEX UX_delivery_assignments_one_active ON dbo.delivery_assignments(delivery_id) WHERE status IN (''ASSIGNED'',''ACCEPTED'');
CREATE UNIQUE INDEX UX_shop_subscriptions_one_active ON dbo.shop_subscriptions(shop_id) WHERE status = ''ACTIVE'';
CREATE INDEX IX_product_review_history_product_time ON dbo.product_review_history(product_id, created_at DESC) INCLUDE (decision, reviewed_by);';

-- END FRESH TABLES AND INDEXES
END
ELSE
BEGIN
-- BEGIN EXISTING DATABASE UPGRADE
IF OBJECT_ID('dbo.orders','U') IS NULL OR OBJECT_ID('dbo.shop_orders','U') IS NULL
 OR OBJECT_ID('dbo.seller_payouts','U') IS NULL OR OBJECT_ID('dbo.subscription_plans','U') IS NULL
 THROW 51500,'Unrecognized or incomplete existing VietFreshHub schema.',1;

IF COL_LENGTH(N'dbo.products', N'origin') IS NULL
    EXEC(N'ALTER TABLE dbo.products ADD origin NVARCHAR(200) NULL;');
IF OBJECT_ID(N'dbo.CK_products_origin_not_blank', N'C') IS NULL
    EXEC(N'ALTER TABLE dbo.products WITH CHECK ADD CONSTRAINT CK_products_origin_not_blank
           CHECK (origin IS NULL OR LEN(LTRIM(RTRIM(origin))) > 0);');
EXEC sys.sp_executesql N'-- SQL Server / SSMS. Select the EXISTING VietFreshHub database first.
-- Stop Spring Boot and back up before migration. No CREATE DATABASE / no data reset.
-- Embedded loyalty stage; transaction is owned by this complete schema file.
-- No separate script needs to be loaded.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRY

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource=N''VietFreshHub.LoyaltyVndMigration'',
        @LockMode=N''Exclusive'', @LockOwner=N''Transaction'', @LockTimeout=10000;
    IF @lockResult < 0 THROW 51000, ''Cannot acquire migration lock.'', 1;
    DECLARE @columns TABLE (
        id INT PRIMARY KEY, table_name SYSNAME, old_name SYSNAME,
        legacy_name SYSNAME, money_name SYSNAME);
    INSERT @columns VALUES
    (1,N''loyalty_accounts'',N''points_balance'',N''legacy_points_balance'',N''reward_balance''),
    (2,N''loyalty_accounts'',N''total_earned'',N''legacy_total_earned_points'',N''total_earned_amount''),
    (3,N''loyalty_accounts'',N''total_redeemed'',N''legacy_total_redeemed_points'',N''total_redeemed_amount''),
    (4,N''loyalty_transactions'',N''points'',N''legacy_points'',N''amount''),
    (5,N''loyalty_transactions'',N''balance_after'',N''legacy_balance_after_points'',N''balance_after_amount'');
    IF OBJECT_ID(N''dbo.loyalty_accounts'',N''U'') IS NULL
       OR OBJECT_ID(N''dbo.loyalty_transactions'',N''U'') IS NULL
        THROW 51000, ''Loyalty base tables missing in existing schema; inspect before rerunning this file.'', 1;

    -- Both old and legacy versions of one column means ambiguous data: do not guess.
    IF EXISTS(SELECT 1 FROM @columns WHERE
        COL_LENGTH(N''dbo.''+table_name,old_name) IS NOT NULL
        AND COL_LENGTH(N''dbo.''+table_name,legacy_name) IS NOT NULL)
        THROW 51000, ''Both old and legacy columns exist; manual review required.'', 1;

    DECLARE @pointColumns INT;
    SELECT @pointColumns=COUNT(*) FROM @columns WHERE
        COL_LENGTH(N''dbo.''+table_name,old_name) IS NOT NULL
        OR COL_LENGTH(N''dbo.''+table_name,legacy_name) IS NOT NULL;
    IF @pointColumns=0
    BEGIN
        IF EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,money_name) IS NULL)
            THROW 51000, ''Unrecognized Loyalty schema.'', 1;
        IF COL_LENGTH(N''dbo.loyalty_rules'',N''earning_percent'') IS NULL
            THROW 51000, ''VND account schema has no VND rules.'', 1;

        PRINT N''Native VND schema detected; no point migration needed.'';
        RETURN;
    END;
    IF @pointColumns<>5 THROW 51000, ''Incomplete point/legacy column set; inspect schema.'', 1;

    -- Rename old rules only if still needed. Resume safely after the earlier partial 01.
    DECLARE @rc INT;
    IF OBJECT_ID(N''dbo.loyalty_rules'',N''U'') IS NOT NULL
       AND (COL_LENGTH(N''dbo.loyalty_rules'',N''earning_amount'') IS NOT NULL
         OR COL_LENGTH(N''dbo.loyalty_rules'',N''redemption_value'') IS NOT NULL
         OR COL_LENGTH(N''dbo.loyalty_rules'',N''min_redemption_points'') IS NOT NULL)
    BEGIN
        IF OBJECT_ID(N''dbo.loyalty_rules_points_legacy'',N''U'') IS NOT NULL
            THROW 51000, ''Both old rules and rules archive exist; inspect before migration.'', 1;
        IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE referenced_object_id=OBJECT_ID(N''dbo.loyalty_rules''))
            THROW 51000, ''Unexpected FK to rules; manual review required.'', 1;
        EXEC @rc=sys.sp_rename N''dbo.loyalty_rules'',N''loyalty_rules_points_legacy'';
        IF @rc<>0 THROW 51000, ''Cannot archive old rules.'', 1;
    END;
    IF OBJECT_ID(N''dbo.loyalty_rules'',N''U'') IS NULL
    BEGIN
EXEC sys.sp_executesql N''CREATE TABLE dbo.loyalty_rules (
    rule_id BIGINT NOT NULL CONSTRAINT PK_loyalty_rules_vnd PRIMARY KEY,
    earning_percent DECIMAL(5,2) NOT NULL CONSTRAINT DF_loyalty_vnd_earning DEFAULT (1.00),
    earning_enabled BIT NOT NULL CONSTRAINT DF_loyalty_vnd_earn_enabled DEFAULT (1),
    min_redemption_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_loyalty_vnd_min DEFAULT (1000),
    max_redemption_percent INT NOT NULL CONSTRAINT DF_loyalty_vnd_max DEFAULT (20),
    redemption_enabled BIT NOT NULL CONSTRAINT DF_loyalty_vnd_redeem_enabled DEFAULT (1),
    updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_loyalty_vnd_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loyalty_vnd_rule_id CHECK (rule_id = 1),
    CONSTRAINT CK_loyalty_vnd_earning CHECK (earning_percent > 0 AND earning_percent <= 100),
    CONSTRAINT CK_loyalty_vnd_min CHECK (
        min_redemption_amount >= 1 AND min_redemption_amount = FLOOR(min_redemption_amount)),
    CONSTRAINT CK_loyalty_vnd_max CHECK (max_redemption_percent BETWEEN 1 AND 100)
);'';
        IF OBJECT_ID(N''dbo.loyalty_rules_points_legacy'',N''U'') IS NOT NULL
        BEGIN
EXEC sys.sp_executesql N''INSERT dbo.loyalty_rules(rule_id,earning_enabled,redemption_enabled,max_redemption_percent)
SELECT rule_id,earning_enabled,redemption_enabled,max_redemption_percent
FROM dbo.loyalty_rules_points_legacy WHERE rule_id=1;'';
            IF COL_LENGTH(N''dbo.loyalty_rules_points_legacy'',N''earning_percent'') IS NOT NULL
EXEC sys.sp_executesql N''UPDATE r SET earning_percent=o.earning_percent FROM dbo.loyalty_rules r JOIN dbo.loyalty_rules_points_legacy o ON o.rule_id=r.rule_id;'';
            IF COL_LENGTH(N''dbo.loyalty_rules_points_legacy'',N''min_redemption_amount'') IS NOT NULL
EXEC sys.sp_executesql N''UPDATE r SET min_redemption_amount=o.min_redemption_amount FROM dbo.loyalty_rules r JOIN dbo.loyalty_rules_points_legacy o ON o.rule_id=r.rule_id;'';
        END;
    END;
    IF COL_LENGTH(N''dbo.loyalty_rules'',N''earning_percent'') IS NULL
       OR COL_LENGTH(N''dbo.loyalty_rules'',N''min_redemption_amount'') IS NULL
        THROW 51000, ''Unrecognized rules schema.'', 1;
EXEC sys.sp_executesql N''IF NOT EXISTS(SELECT 1 FROM dbo.loyalty_rules WHERE rule_id=1) INSERT dbo.loyalty_rules(rule_id) VALUES(1);'';
    -- Preserve all CHECK definitions (including transaction_type); recreate inside this transaction.
    DECLARE @checks TABLE (id INT IDENTITY PRIMARY KEY, table_name SYSNAME,
        constraint_name SYSNAME, definition NVARCHAR(MAX), is_disabled BIT, is_not_trusted BIT);
    INSERT @checks(table_name,constraint_name,definition,is_disabled,is_not_trusted)
    SELECT OBJECT_NAME(parent_object_id),name,definition,is_disabled,is_not_trusted
    FROM sys.check_constraints
    WHERE parent_object_id IN (OBJECT_ID(N''dbo.loyalty_accounts''),OBJECT_ID(N''dbo.loyalty_transactions''));
    IF EXISTS(SELECT 1 FROM @checks WHERE is_disabled=1 OR is_not_trusted=1)
        THROW 51000, ''Disabled/untrusted CHECK found: review it before migration.'', 1;
    DECLARE @i INT=1, @n INT, @t SYSNAME, @cn SYSNAME, @def NVARCHAR(MAX), @sql NVARCHAR(MAX);
    SELECT @n=COUNT(*) FROM @checks;
    -- SQL Server catalog CHECK definitions use bracketed identifiers.
    -- Replace only exact column tokens, never all occurrences of the word ''points''.
    DECLARE @j INT=1,@old SYSNAME,@legacy SYSNAME,@money SYSNAME,@full NVARCHAR(300);
    WHILE @j<=5
    BEGIN
        SELECT @t=table_name,@old=old_name,@legacy=legacy_name FROM @columns WHERE id=@j;
        IF COL_LENGTH(N''dbo.''+@t,@old) IS NOT NULL
        BEGIN
            IF EXISTS(SELECT 1 FROM @checks WHERE table_name=@t
                AND CHARINDEX(@old,definition)>0 AND CHARINDEX(QUOTENAME(@old),definition)=0)
                THROW 51000, ''Nonstandard CHECK expression: review column references before renaming.'', 1;
            UPDATE @checks SET definition=REPLACE(definition,QUOTENAME(@old),QUOTENAME(@legacy))
                WHERE table_name=@t;
        END;
        SET @j+=1;
    END;
    SET @i=1;
    WHILE @i<=@n
    BEGIN
        SELECT @t=table_name,@cn=constraint_name FROM @checks WHERE id=@i;
        SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' DROP CONSTRAINT ''+QUOTENAME(@cn)+N'';'';
        EXEC sys.sp_executesql @sql;
        SET @i+=1;
    END;
    SET @j=1;
    WHILE @j<=5
    BEGIN
        SELECT @t=table_name,@old=old_name,@legacy=legacy_name,@money=money_name
            FROM @columns WHERE id=@j;
        IF COL_LENGTH(N''dbo.''+@t,@old) IS NOT NULL
        BEGIN
            SET @full=N''dbo.''+@t+N''.''+@old;
            EXEC @rc=sys.sp_rename @full,@legacy,N''COLUMN'';
            IF @rc<>0 THROW 51000, ''Column rename failed; migration rolled back.'', 1;
        END;
        IF COL_LENGTH(N''dbo.''+@t,@money) IS NULL
        BEGIN
            SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' ADD ''+QUOTENAME(@money)+N'' DECIMAL(18,2) NULL;'';
            EXEC sys.sp_executesql @sql;
        END;
        IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.''+@t)
            AND name=@money AND system_type_id=106 AND precision=18 AND scale=2)
            THROW 51000, ''Money column must be DECIMAL(18,2).'', 1;
        SET @j+=1;
    END;
    SET @i=1;
    WHILE @i<=@n
    BEGIN
        SELECT @t=table_name,@cn=constraint_name,@def=definition FROM @checks WHERE id=@i;
        SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' WITH CHECK ADD CONSTRAINT ''
            +QUOTENAME(@cn)+N'' CHECK ''+@def+N'';'';
        EXEC sys.sp_executesql @sql;
        SET @i+=1;
    END;
    PRINT N''Loyalty legacy values preserved; VND columns prepared.'';

END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
';

EXEC sys.sp_executesql N'-- SQL Server / SSMS. Select the EXISTING VietFreshHub database first.
-- Stop Spring Boot and back up before migration. No CREATE DATABASE / no data reset.
-- Embedded loyalty stage; transaction is owned by this complete schema file.
-- No separate script needs to be loaded.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
-- Historical conversion ONLY: this is VND per OLD POINT, not the new earning %.
-- 100 is retained from the uploaded script: 50 old points -> 5,000 VND.
-- Confirm it matches your team''s old data before running on a nonempty database.
-- Mixed historical rates require a per-row migration; do not use one rate in that case.
DECLARE @ApprovedVndPerLegacyPoint DECIMAL(18,6)=@MigrationLegacyRate;
BEGIN TRY

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource=N''VietFreshHub.LoyaltyVndMigration'',
        @LockMode=N''Exclusive'', @LockOwner=N''Transaction'', @LockTimeout=10000;
    IF @lockResult < 0 THROW 51000, ''Cannot acquire migration lock.'', 1;
    DECLARE @columns TABLE (
        id INT PRIMARY KEY, table_name SYSNAME, old_name SYSNAME,
        legacy_name SYSNAME, money_name SYSNAME);
    INSERT @columns VALUES
    (1,N''loyalty_accounts'',N''points_balance'',N''legacy_points_balance'',N''reward_balance''),
    (2,N''loyalty_accounts'',N''total_earned'',N''legacy_total_earned_points'',N''total_earned_amount''),
    (3,N''loyalty_accounts'',N''total_redeemed'',N''legacy_total_redeemed_points'',N''total_redeemed_amount''),
    (4,N''loyalty_transactions'',N''points'',N''legacy_points'',N''amount''),
    (5,N''loyalty_transactions'',N''balance_after'',N''legacy_balance_after_points'',N''balance_after_amount'');
    IF EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,money_name) IS NULL)
        THROW 51000, ''Embedded loyalty preparation did not create all VND columns.'', 1;
    IF EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,old_name) IS NOT NULL)
        THROW 51000, ''Embedded loyalty preparation left old column names.'', 1;
    IF NOT EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,legacy_name) IS NOT NULL)
    BEGIN

        PRINT N''No legacy columns: native VND installation; no conversion.'';
        RETURN;
    END;
    IF EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,legacy_name) IS NULL)
        THROW 51000, ''Embedded loyalty preparation left an incomplete legacy column set.'', 1;
EXEC sys.sp_executesql N''-- Retry after a completed conversion is a no-op; never revalue an existing VND balance.
IF NOT EXISTS(SELECT 1 FROM dbo.loyalty_accounts WHERE reward_balance IS NULL
    OR total_earned_amount IS NULL OR total_redeemed_amount IS NULL)
AND NOT EXISTS(SELECT 1 FROM dbo.loyalty_transactions WHERE amount IS NULL OR balance_after_amount IS NULL)
BEGIN
    PRINT N''''02: no unconverted records (empty or already converted).'''';
END
ELSE
BEGIN
    IF @Rate IS NULL OR @Rate<=0 THROW 51000, ''''Set a positive approved historical VND-per-point rate.'''', 1;
    IF EXISTS(SELECT 1 FROM dbo.loyalty_accounts WHERE reward_balance IS NOT NULL
        OR total_earned_amount IS NOT NULL OR total_redeemed_amount IS NOT NULL)
    OR EXISTS(SELECT 1 FROM dbo.loyalty_transactions WHERE amount IS NOT NULL OR balance_after_amount IS NOT NULL)
        THROW 51000, ''''Mixed/partial VND conversion or live writes detected; do not overwrite.'''', 1;
    IF EXISTS(SELECT 1 FROM dbo.loyalty_accounts WHERE legacy_points_balance IS NULL
        OR legacy_total_earned_points IS NULL OR legacy_total_redeemed_points IS NULL)
    OR EXISTS(SELECT 1 FROM dbo.loyalty_transactions WHERE legacy_points IS NULL OR legacy_balance_after_points IS NULL)
        THROW 51000, ''''Missing source legacy values; conversion cannot proceed.'''', 1;
    IF EXISTS(SELECT 1 FROM dbo.loyalty_accounts
        CROSS APPLY(VALUES(legacy_points_balance),(legacy_total_earned_points),(legacy_total_redeemed_points)) v(n)
        WHERE v.n*@Rate<>FLOOR(v.n*@Rate) OR ABS(v.n*@Rate)>9999999999999999)
    OR EXISTS(SELECT 1 FROM dbo.loyalty_transactions
        CROSS APPLY(VALUES(legacy_points),(legacy_balance_after_points)) v(n)
        WHERE v.n*@Rate<>FLOOR(v.n*@Rate) OR ABS(v.n*@Rate)>9999999999999999)
        THROW 51000, ''''Fractional dong or DECIMAL overflow; approve a reconciled conversion.'''', 1;
    UPDATE dbo.loyalty_accounts SET reward_balance=legacy_points_balance*@Rate,
        total_earned_amount=legacy_total_earned_points*@Rate,
        total_redeemed_amount=legacy_total_redeemed_points*@Rate;
    UPDATE dbo.loyalty_transactions SET amount=legacy_points*@Rate,
        balance_after_amount=legacy_balance_after_points*@Rate;
    PRINT N''''02: converted legacy values; no historical orders or timestamps changed.'''';
END;'', N''@Rate DECIMAL(18,6)'', @Rate=@ApprovedVndPerLegacyPoint;
    PRINT N''Loyalty VND conversion stage complete.'';

END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
', N'@MigrationLegacyRate DECIMAL(18,6)', @MigrationLegacyRate=@LegacyVndPerPoint;

EXEC sys.sp_executesql N'-- SQL Server / SSMS. Select the EXISTING VietFreshHub database first.
-- Stop Spring Boot and back up before migration. No CREATE DATABASE / no data reset.
-- Embedded loyalty stage; transaction is owned by this complete schema file.
-- No separate script needs to be loaded.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRY

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource=N''VietFreshHub.LoyaltyVndMigration'',
        @LockMode=N''Exclusive'', @LockOwner=N''Transaction'', @LockTimeout=10000;
    IF @lockResult < 0 THROW 51000, ''Cannot acquire migration lock.'', 1;
    DECLARE @columns TABLE (
        id INT PRIMARY KEY, table_name SYSNAME, old_name SYSNAME,
        legacy_name SYSNAME, money_name SYSNAME);
    INSERT @columns VALUES
    (1,N''loyalty_accounts'',N''points_balance'',N''legacy_points_balance'',N''reward_balance''),
    (2,N''loyalty_accounts'',N''total_earned'',N''legacy_total_earned_points'',N''total_earned_amount''),
    (3,N''loyalty_accounts'',N''total_redeemed'',N''legacy_total_redeemed_points'',N''total_redeemed_amount''),
    (4,N''loyalty_transactions'',N''points'',N''legacy_points'',N''amount''),
    (5,N''loyalty_transactions'',N''balance_after'',N''legacy_balance_after_points'',N''balance_after_amount'');
    IF EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,money_name) IS NULL)
        THROW 51000, ''Embedded loyalty preparation did not create money columns.'', 1;
    IF COL_LENGTH(N''dbo.loyalty_rules'',N''earning_percent'') IS NULL
       OR COL_LENGTH(N''dbo.loyalty_rules'',N''min_redemption_amount'') IS NULL
        THROW 51000, ''Embedded loyalty preparation did not create VND rules.'', 1;
    IF EXISTS(SELECT 1 FROM @columns WHERE COL_LENGTH(N''dbo.''+table_name,old_name) IS NOT NULL)
        THROW 51000, ''Embedded loyalty preparation left old point column names.'', 1;
EXEC sys.sp_executesql N''IF EXISTS(SELECT 1 FROM dbo.loyalty_accounts WHERE reward_balance IS NULL
    OR total_earned_amount IS NULL OR total_redeemed_amount IS NULL)
OR EXISTS(SELECT 1 FROM dbo.loyalty_transactions WHERE amount IS NULL OR balance_after_amount IS NULL)
    THROW 51000, ''''Embedded loyalty conversion is incomplete; transaction will be rolled back.'''', 1;
IF EXISTS(SELECT 1 FROM dbo.loyalty_transactions WHERE order_id IS NOT NULL AND transaction_type IN (''''EARN'''',''''REDEEM'''')
    GROUP BY loyalty_account_id,order_id,transaction_type HAVING COUNT(*)>1)
    THROW 51000, ''''Duplicate EARN/REDEEM history: reconcile manually; no rows were deleted.'''', 1;'';
    -- Preserve all CHECK definitions (including transaction_type); recreate inside this transaction.
    DECLARE @checks TABLE (id INT IDENTITY PRIMARY KEY, table_name SYSNAME,
        constraint_name SYSNAME, definition NVARCHAR(MAX), is_disabled BIT, is_not_trusted BIT);
    INSERT @checks(table_name,constraint_name,definition,is_disabled,is_not_trusted)
    SELECT OBJECT_NAME(parent_object_id),name,definition,is_disabled,is_not_trusted
    FROM sys.check_constraints
    WHERE parent_object_id IN (OBJECT_ID(N''dbo.loyalty_accounts''),OBJECT_ID(N''dbo.loyalty_transactions''));
    IF EXISTS(SELECT 1 FROM @checks WHERE is_disabled=1 OR is_not_trusted=1)
        THROW 51000, ''Disabled/untrusted CHECK found: review it before migration.'', 1;
    DECLARE @i INT=1, @n INT, @t SYSNAME, @cn SYSNAME, @def NVARCHAR(MAX), @sql NVARCHAR(MAX);
    SELECT @n=COUNT(*) FROM @checks;
    SET @i=1;
    WHILE @i<=@n
    BEGIN
        SELECT @t=table_name,@cn=constraint_name FROM @checks WHERE id=@i;
        SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' DROP CONSTRAINT ''+QUOTENAME(@cn)+N'';'';
        EXEC sys.sp_executesql @sql;
        SET @i+=1;
    END;
    DECLARE @j INT=1,@legacy SYSNAME,@money SYSNAME,@dc SYSNAME;
    WHILE @j<=5
    BEGIN
        SELECT @t=table_name,@legacy=legacy_name,@money=money_name FROM @columns WHERE id=@j;
        IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.''+@t)
            AND name=@money AND system_type_id=106 AND precision=18 AND scale=2)
            THROW 51000, ''Money column must be DECIMAL(18,2).'', 1;
        IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.''+@t) AND name=@money AND is_nullable=1)
        BEGIN
            SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' ALTER COLUMN ''+QUOTENAME(@money)+N'' DECIMAL(18,2) NOT NULL;'';
            EXEC sys.sp_executesql @sql;
        END;
        IF COL_LENGTH(N''dbo.''+@t,@legacy) IS NOT NULL
        BEGIN
            IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.''+@t)
                AND name=@legacy AND system_type_id=56)
                THROW 51000, ''Legacy column is not INT; review before altering.'', 1;
            -- Drop legacy defaults: new VND records should have NULL legacy fields, not fabricated zeros.
            SET @dc=NULL;
            SELECT @dc=d.name FROM sys.default_constraints d JOIN sys.columns c
                ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
                WHERE c.object_id=OBJECT_ID(N''dbo.''+@t) AND c.name=@legacy;
            IF @dc IS NOT NULL
            BEGIN
                SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' DROP CONSTRAINT ''+QUOTENAME(@dc)+N'';'';
                EXEC sys.sp_executesql @sql;
            END;
            IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N''dbo.''+@t) AND name=@legacy AND is_nullable=0)
            BEGIN
                SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' ALTER COLUMN ''+QUOTENAME(@legacy)+N'' INT NULL;'';
                EXEC sys.sp_executesql @sql;
            END;
        END;
        SET @j+=1;
    END;
    SET @i=1;
    WHILE @i<=@n
    BEGIN
        SELECT @t=table_name,@cn=constraint_name,@def=definition FROM @checks WHERE id=@i;
        SET @sql=N''ALTER TABLE dbo.''+QUOTENAME(@t)+N'' WITH CHECK ADD CONSTRAINT ''
            +QUOTENAME(@cn)+N'' CHECK ''+@def+N'';'';
        EXEC sys.sp_executesql @sql;
        SET @i+=1;
    END;
    IF OBJECT_ID(N''dbo.CK_loyalty_vnd_balances'',N''C'') IS NOT NULL
        ALTER TABLE dbo.loyalty_accounts DROP CONSTRAINT CK_loyalty_vnd_balances;
EXEC sys.sp_executesql N''ALTER TABLE dbo.loyalty_accounts WITH CHECK ADD CONSTRAINT CK_loyalty_vnd_balances CHECK (reward_balance>=0 AND reward_balance=FLOOR(reward_balance) AND total_earned_amount>=0 AND total_earned_amount=FLOOR(total_earned_amount) AND total_redeemed_amount>=0 AND total_redeemed_amount=FLOOR(total_redeemed_amount));'';
    IF OBJECT_ID(N''dbo.CK_loyalty_vnd_transactions'',N''C'') IS NOT NULL
        ALTER TABLE dbo.loyalty_transactions DROP CONSTRAINT CK_loyalty_vnd_transactions;
EXEC sys.sp_executesql N''ALTER TABLE dbo.loyalty_transactions WITH CHECK ADD CONSTRAINT CK_loyalty_vnd_transactions CHECK (amount<>0 AND amount=FLOOR(amount) AND balance_after_amount>=0 AND balance_after_amount=FLOOR(balance_after_amount) AND transaction_type IN (''''EARN'''',''''REDEEM'''',''''REFUND'''',''''ADJUSTMENT'''') AND (transaction_type<>''''EARN'''' OR amount>0) AND (transaction_type<>''''REDEEM'''' OR amount<0));'';
    IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c
        ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
        WHERE c.object_id=OBJECT_ID(N''dbo.loyalty_accounts'') AND c.name=N''reward_balance'')
EXEC sys.sp_executesql N''ALTER TABLE dbo.loyalty_accounts ADD CONSTRAINT DF_loyalty_vnd_account_balance DEFAULT(0) FOR reward_balance;'';
    IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c
        ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
        WHERE c.object_id=OBJECT_ID(N''dbo.loyalty_accounts'') AND c.name=N''total_earned_amount'')
EXEC sys.sp_executesql N''ALTER TABLE dbo.loyalty_accounts ADD CONSTRAINT DF_loyalty_vnd_account_earned DEFAULT(0) FOR total_earned_amount;'';
    IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c
        ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
        WHERE c.object_id=OBJECT_ID(N''dbo.loyalty_accounts'') AND c.name=N''total_redeemed_amount'')
EXEC sys.sp_executesql N''ALTER TABLE dbo.loyalty_accounts ADD CONSTRAINT DF_loyalty_vnd_account_redeemed DEFAULT(0) FOR total_redeemed_amount;'';
    IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.loyalty_transactions'') AND name=N''UX_loyalty_vnd_earn'')
    BEGIN
        -- Rebuild only this migration-owned index to ensure its filter/keys are correct.
        DROP INDEX UX_loyalty_vnd_earn ON dbo.loyalty_transactions;
    END;
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_loyalty_vnd_earn ON dbo.loyalty_transactions(loyalty_account_id,order_id) WHERE transaction_type=''''EARN'''' AND order_id IS NOT NULL;'';
    IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N''dbo.loyalty_transactions'') AND name=N''UX_loyalty_vnd_redeem'')
    BEGIN
        -- Rebuild only this migration-owned index to ensure its filter/keys are correct.
        DROP INDEX UX_loyalty_vnd_redeem ON dbo.loyalty_transactions;
    END;
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_loyalty_vnd_redeem ON dbo.loyalty_transactions(loyalty_account_id,order_id) WHERE transaction_type=''''REDEEM'''' AND order_id IS NOT NULL;'';
    PRINT N''Loyalty VND schema ready.'';

END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
EXEC sys.sp_executesql N''SELECT rule_id,earning_percent,earning_enabled,min_redemption_amount,max_redemption_percent,redemption_enabled FROM dbo.loyalty_rules WHERE rule_id=1;'';
';

EXEC sys.sp_executesql N'IF COL_LENGTH(''dbo.shop_orders'', ''payment_status'') IS NULL
    EXEC(N''ALTER TABLE dbo.shop_orders ADD payment_status VARCHAR(30) NULL'');

EXEC(N''
    IF EXISTS (
        SELECT 1 FROM dbo.shop_orders s
        JOIN dbo.orders o ON o.order_id = s.order_id
        WHERE s.payment_status IS NULL AND o.payment_status = ''''PARTIALLY_REFUNDED''''
          AND (SELECT COUNT(*) FROM dbo.shop_orders sibling WHERE sibling.order_id = o.order_id) > 1
    )
        THROW 50001, N''''Cần đối soát trạng thái hoàn tiền từng shop của các lượt đã hoàn tiền một phần trước khi cập nhật.'''', 1;

    UPDATE s
    SET payment_status = CASE
        WHEN EXISTS (
            SELECT 1 FROM dbo.payments p
            JOIN dbo.payment_methods m ON m.payment_method_id = p.payment_method_id
            WHERE p.order_id = s.order_id AND m.method_code = ''''COD''''
        ) AND NOT EXISTS (
            SELECT 1 FROM dbo.payments p
            JOIN dbo.payment_methods m ON m.payment_method_id = p.payment_method_id
            WHERE p.order_id = s.order_id AND m.method_code <> ''''COD''''
        ) THEN CASE WHEN EXISTS (
            SELECT 1 FROM dbo.deliveries d
            WHERE d.shop_order_id = s.shop_order_id AND d.status = ''''DELIVERED''''
        ) THEN ''''PAID'''' ELSE ''''UNPAID'''' END
        ELSE o.payment_status
    END
    FROM dbo.shop_orders s
    JOIN dbo.orders o ON o.order_id = s.order_id
    WHERE s.payment_status IS NULL;
'');

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(''dbo.shop_orders'') AND name = ''payment_status'' AND is_nullable = 1)
    EXEC(N''ALTER TABLE dbo.shop_orders ALTER COLUMN payment_status VARCHAR(30) NOT NULL'');

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(''dbo.shop_orders'') AND name = ''payment_status'' AND default_object_id = 0)
    EXEC(N''ALTER TABLE dbo.shop_orders ADD CONSTRAINT DF_shop_orders_payment_status DEFAULT (''''UNPAID'''') FOR payment_status'');

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(''dbo.shop_orders'') AND name = ''CK_shop_orders_payment_status'')
    EXEC(N''ALTER TABLE dbo.shop_orders ADD CONSTRAINT CK_shop_orders_payment_status
        CHECK (payment_status IN (''''UNPAID'''', ''''PENDING'''', ''''PAID'''', ''''PARTIALLY_REFUNDED'''', ''''REFUNDED'''', ''''FAILED''''))'');

';

EXEC sys.sp_executesql N'IF COL_LENGTH(''dbo.categories'',''image_url'') IS NULL ALTER TABLE dbo.categories ADD image_url NVARCHAR(1000) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(''dbo.shop_orders'') AND name=''UQ_shop_orders_id_order'')
 ALTER TABLE dbo.shop_orders ADD CONSTRAINT UQ_shop_orders_id_order UNIQUE(shop_order_id,order_id);
IF COL_LENGTH(''dbo.seller_payouts'',''eligible_at'') IS NULL ALTER TABLE dbo.seller_payouts ADD eligible_at DATETIME2(0) NULL;
IF COL_LENGTH(''dbo.seller_payouts'',''funds_source'') IS NULL ALTER TABLE dbo.seller_payouts ADD funds_source VARCHAR(20) NOT NULL CONSTRAINT DF_seller_payouts_funds_source DEFAULT(''UNCONFIRMED'') WITH VALUES;
IF COL_LENGTH(''dbo.seller_payouts'',''funds_confirmed_at'') IS NULL ALTER TABLE dbo.seller_payouts ADD funds_confirmed_at DATETIME2(0) NULL;
IF COL_LENGTH(''dbo.seller_payouts'',''funds_confirmed_by'') IS NULL ALTER TABLE dbo.seller_payouts ADD funds_confirmed_by BIGINT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(''dbo.seller_payouts'') AND name=''UQ_seller_payouts_id_shop'')
 ALTER TABLE dbo.seller_payouts ADD CONSTRAINT UQ_seller_payouts_id_shop UNIQUE(payout_id,shop_id);';

EXEC sys.sp_executesql N'IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(''dbo.seller_payouts'') AND name=''FK_seller_payouts_funds_confirmed_by'')
 ALTER TABLE dbo.seller_payouts WITH CHECK ADD CONSTRAINT FK_seller_payouts_funds_confirmed_by FOREIGN KEY(funds_confirmed_by) REFERENCES dbo.users(user_id);
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(''dbo.seller_payouts'') AND name=''CK_seller_payouts_funds_source'' AND definition LIKE ''%ONLINE%'')
BEGIN
 IF OBJECT_ID(''dbo.CK_seller_payouts_funds_source'',''C'') IS NOT NULL ALTER TABLE dbo.seller_payouts DROP CONSTRAINT CK_seller_payouts_funds_source;
 ALTER TABLE dbo.seller_payouts WITH CHECK ADD CONSTRAINT CK_seller_payouts_funds_source CHECK(funds_source IN(''UNCONFIRMED'',''COD'',''BANK_TRANSFER'',''VNPAY'',''MANUAL'',''ONLINE''));
END;
UPDATE dbo.seller_payouts SET funds_source=''ONLINE'' WHERE funds_source=''BANK_TRANSFER'';
IF OBJECT_ID(''dbo.CK_seller_payouts_funds_confirmation'',''C'') IS NULL
 ALTER TABLE dbo.seller_payouts WITH CHECK ADD CONSTRAINT CK_seller_payouts_funds_confirmation CHECK((funds_confirmed_at IS NULL AND funds_confirmed_by IS NULL) OR (funds_confirmed_at IS NOT NULL AND funds_source<>''UNCONFIRMED''));
IF COL_LENGTH(''dbo.subscription_plans'',''max_products'') IS NULL ALTER TABLE dbo.subscription_plans ADD max_products INT NULL;';

EXEC sys.sp_executesql N'IF @Quota IS NULL OR @Quota<=0 THROW 51501,''Set a positive quota for existing unnamed/custom plans.'',1;
UPDATE dbo.subscription_plans SET max_products=CASE name WHEN N''Free'' THEN 5 WHEN N''Basic'' THEN 20 WHEN N''Pro'' THEN 100 ELSE @Quota END WHERE max_products IS NULL;
ALTER TABLE dbo.subscription_plans ALTER COLUMN max_products INT NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(''dbo.subscription_plans'') AND name=''max_products'' AND default_object_id<>0)
 ALTER TABLE dbo.subscription_plans ADD CONSTRAINT DF_subscription_plans_max_products DEFAULT(20) FOR max_products;
IF OBJECT_ID(''dbo.CK_subscription_plans_max_products'',''C'') IS NULL
 ALTER TABLE dbo.subscription_plans WITH CHECK ADD CONSTRAINT CK_subscription_plans_max_products CHECK(max_products>0);
IF NOT EXISTS(SELECT 1 FROM dbo.subscription_plans WHERE name=N''Free'')
 INSERT dbo.subscription_plans(name,description,price,billing_period,benefits,max_products,status)
 VALUES(N''Free'',N''Gói miễn phí mặc định cho cửa hàng.'',0,''MONTHLY'',N''Được public tối đa 5 sản phẩm.'',5,''ACTIVE'');', N'@Quota INT', @Quota=@LegacyPlanMaxProducts;

EXEC sys.sp_executesql N'IF OBJECT_ID(N''dbo.refund_requests'',N''U'') IS NULL BEGIN CREATE TABLE dbo.refund_requests (
    refund_request_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_refund_requests PRIMARY KEY,
    order_id BIGINT NOT NULL,
    shop_order_id BIGINT NULL,
    bank_name NVARCHAR(120) NOT NULL,
    bank_account_number NVARCHAR(50) NOT NULL,
    account_holder_name NVARCHAR(150) NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL,
    submitted_at DATETIME2(0) NOT NULL CONSTRAINT DF_refund_requests_submitted_at DEFAULT SYSUTCDATETIME(),
    status VARCHAR(20) NOT NULL CONSTRAINT DF_refund_requests_status DEFAULT (''PENDING''),
    CONSTRAINT FK_refund_requests_order FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id),
    CONSTRAINT FK_refund_requests_shop_order FOREIGN KEY (shop_order_id, order_id)
        REFERENCES dbo.shop_orders(shop_order_id, order_id),
    CONSTRAINT CK_refund_requests_amount CHECK (refund_amount > 0 AND refund_amount = FLOOR(refund_amount)),
    CONSTRAINT CK_refund_requests_status CHECK (status IN (''PENDING'', ''PROCESSING'', ''PAID'', ''REJECTED'', ''CANCELLED''))
); END;';

EXEC sys.sp_executesql N'IF OBJECT_ID(N''dbo.shop_payout_accounts'',N''U'') IS NULL BEGIN CREATE TABLE dbo.shop_payout_accounts (
    shop_id BIGINT NOT NULL CONSTRAINT PK_shop_payout_accounts PRIMARY KEY,
    bank_code VARCHAR(30) NOT NULL,
    bank_name NVARCHAR(100) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_holder NVARCHAR(150) NOT NULL,
    verified BIT NOT NULL CONSTRAINT DF_shop_payout_accounts_verified DEFAULT (0),
    updated_by BIGINT NULL,
    updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_shop_payout_accounts_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_shop_payout_accounts_shops FOREIGN KEY (shop_id) REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_shop_payout_accounts_users FOREIGN KEY (updated_by) REFERENCES dbo.users(user_id),
    CONSTRAINT CK_shop_payout_accounts_not_blank CHECK (
        LEN(LTRIM(RTRIM(bank_code))) > 0 AND LEN(LTRIM(RTRIM(bank_name))) > 0
        AND LEN(LTRIM(RTRIM(account_number))) > 0 AND LEN(LTRIM(RTRIM(account_holder))) > 0)
); END;';

EXEC sys.sp_executesql N'IF OBJECT_ID(N''dbo.seller_payout_batches'',N''U'') IS NULL BEGIN CREATE TABLE dbo.seller_payout_batches (
    batch_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_seller_payout_batches PRIMARY KEY,
    scheduled_slot VARCHAR(40) NOT NULL,
    scheduled_at DATETIME2(0) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_seller_payout_batches_status DEFAULT (''READY''),
    total_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_seller_payout_batches_total DEFAULT (0),
    payout_count INT NOT NULL CONSTRAINT DF_seller_payout_batches_payout_count DEFAULT (0),
    shop_count INT NOT NULL CONSTRAINT DF_seller_payout_batches_shop_count DEFAULT (0),
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_seller_payout_batches_created_at DEFAULT SYSUTCDATETIME(),
    completed_at DATETIME2(0) NULL,
    CONSTRAINT UQ_seller_payout_batches_slot UNIQUE (scheduled_slot),
    CONSTRAINT CK_seller_payout_batches_status CHECK (status IN (''READY'', ''PARTIAL'', ''PAID'', ''FAILED'')),
    CONSTRAINT CK_seller_payout_batches_totals CHECK (total_amount >= 0 AND payout_count >= 0 AND shop_count >= 0)
); END;';

EXEC sys.sp_executesql N'IF OBJECT_ID(N''dbo.seller_payout_batch_items'',N''U'') IS NULL BEGIN CREATE TABLE dbo.seller_payout_batch_items (
    batch_item_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_seller_payout_batch_items PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    payout_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    shop_name_snapshot NVARCHAR(200) NOT NULL,
    bank_name_snapshot NVARCHAR(100) NOT NULL,
    bank_code_snapshot VARCHAR(30) NOT NULL,
    account_number_snapshot VARCHAR(50) NOT NULL,
    account_holder_snapshot NVARCHAR(150) NOT NULL,
    transfer_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_seller_payout_batch_items_status DEFAULT (''PENDING''),
    transfer_reference NVARCHAR(255) NULL,
    failure_reason NVARCHAR(1000) NULL,
    paid_at DATETIME2(0) NULL,
    result_by BIGINT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_seller_payout_batch_items_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_seller_payout_batch_items_batches FOREIGN KEY (batch_id) REFERENCES dbo.seller_payout_batches(batch_id),
    CONSTRAINT FK_seller_payout_batch_items_payouts FOREIGN KEY (payout_id, shop_id) REFERENCES dbo.seller_payouts(payout_id, shop_id),
    CONSTRAINT FK_seller_payout_batch_items_shops FOREIGN KEY (shop_id) REFERENCES dbo.shops(shop_id),
    CONSTRAINT FK_seller_payout_batch_items_users FOREIGN KEY (result_by) REFERENCES dbo.users(user_id),
    CONSTRAINT UQ_seller_payout_batch_items_batch_payout UNIQUE (batch_id, payout_id),
    CONSTRAINT CK_seller_payout_batch_items_status CHECK (status IN (''PENDING'', ''PAID'', ''FAILED'')),
    CONSTRAINT CK_seller_payout_batch_items_amount CHECK (transfer_amount > 0 AND transfer_amount = FLOOR(transfer_amount)),
    CONSTRAINT CK_seller_payout_batch_items_paid CHECK (
        status <> ''PAID'' OR (paid_at IS NOT NULL AND transfer_reference IS NOT NULL AND LEN(LTRIM(RTRIM(transfer_reference))) > 0))
); END;';

EXEC sys.sp_executesql N'IF COL_LENGTH(''dbo.refund_requests'',''shop_order_id'') IS NULL ALTER TABLE dbo.refund_requests ADD shop_order_id BIGINT NULL;
IF COL_LENGTH(''dbo.refund_requests'',''status'') IS NULL ALTER TABLE dbo.refund_requests ADD status VARCHAR(20) NOT NULL CONSTRAINT DF_refund_requests_status DEFAULT(''PENDING'') WITH VALUES;';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(''dbo.refund_requests'') AND name=''UQ_refund_requests_order'') ALTER TABLE dbo.refund_requests DROP CONSTRAINT UQ_refund_requests_order;
IF OBJECT_ID(''dbo.FK_refund_requests_shop_order'',''F'') IS NOT NULL ALTER TABLE dbo.refund_requests DROP CONSTRAINT FK_refund_requests_shop_order;
ALTER TABLE dbo.refund_requests WITH CHECK ADD CONSTRAINT FK_refund_requests_shop_order FOREIGN KEY(shop_order_id,order_id) REFERENCES dbo.shop_orders(shop_order_id,order_id);
IF OBJECT_ID(''dbo.CK_refund_requests_amount'',''C'') IS NOT NULL ALTER TABLE dbo.refund_requests DROP CONSTRAINT CK_refund_requests_amount;
ALTER TABLE dbo.refund_requests WITH CHECK ADD CONSTRAINT CK_refund_requests_amount CHECK(refund_amount>0 AND refund_amount=FLOOR(refund_amount));
IF OBJECT_ID(''dbo.CK_refund_requests_status'',''C'') IS NULL ALTER TABLE dbo.refund_requests WITH CHECK ADD CONSTRAINT CK_refund_requests_status CHECK(status IN(''PENDING'',''PROCESSING'',''PAID'',''REJECTED'',''CANCELLED''));
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(''dbo.refund_requests'') AND name=''submitted_at'' AND default_object_id<>0)
 ALTER TABLE dbo.refund_requests ADD CONSTRAINT DF_refund_requests_submitted_at DEFAULT SYSUTCDATETIME() FOR submitted_at;
IF OBJECT_ID(''dbo.FK_seller_payout_batch_items_payouts'',''F'') IS NOT NULL ALTER TABLE dbo.seller_payout_batch_items DROP CONSTRAINT FK_seller_payout_batch_items_payouts;
ALTER TABLE dbo.seller_payout_batch_items WITH CHECK ADD CONSTRAINT FK_seller_payout_batch_items_payouts FOREIGN KEY(payout_id,shop_id) REFERENCES dbo.seller_payouts(payout_id,shop_id);
IF OBJECT_ID(''dbo.CK_shop_payout_accounts_not_blank'',''C'') IS NULL ALTER TABLE dbo.shop_payout_accounts WITH CHECK ADD CONSTRAINT CK_shop_payout_accounts_not_blank CHECK(LEN(LTRIM(RTRIM(bank_code)))>0 AND LEN(LTRIM(RTRIM(bank_name)))>0 AND LEN(LTRIM(RTRIM(account_number)))>0 AND LEN(LTRIM(RTRIM(account_holder)))>0);
IF OBJECT_ID(''dbo.CK_seller_payout_batches_totals'',''C'') IS NULL ALTER TABLE dbo.seller_payout_batches WITH CHECK ADD CONSTRAINT CK_seller_payout_batches_totals CHECK(total_amount>=0 AND payout_count>=0 AND shop_count>=0);
IF OBJECT_ID(''dbo.CK_seller_payout_batch_items_amount'',''C'') IS NULL ALTER TABLE dbo.seller_payout_batch_items WITH CHECK ADD CONSTRAINT CK_seller_payout_batch_items_amount CHECK(transfer_amount>0 AND transfer_amount=FLOOR(transfer_amount));
IF OBJECT_ID(''dbo.CK_seller_payout_batch_items_paid'',''C'') IS NULL ALTER TABLE dbo.seller_payout_batch_items WITH CHECK ADD CONSTRAINT CK_seller_payout_batch_items_paid CHECK(status<>''PAID'' OR (paid_at IS NOT NULL AND transfer_reference IS NOT NULL AND LEN(LTRIM(RTRIM(transfer_reference)))>0));';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(''dbo.refund_requests'') AND name=''UQ_refund_requests_order'') DROP INDEX UQ_refund_requests_order ON dbo.refund_requests; CREATE UNIQUE INDEX UQ_refund_requests_order ON dbo.refund_requests(order_id) WHERE shop_order_id IS NULL;';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(''dbo.refund_requests'') AND name=''UQ_refund_requests_shop_order'') DROP INDEX UQ_refund_requests_shop_order ON dbo.refund_requests; CREATE UNIQUE INDEX UQ_refund_requests_shop_order ON dbo.refund_requests(shop_order_id) WHERE shop_order_id IS NOT NULL;';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(''dbo.refund_requests'') AND name=''IX_refund_requests_order_status'') DROP INDEX IX_refund_requests_order_status ON dbo.refund_requests; CREATE INDEX IX_refund_requests_order_status ON dbo.refund_requests(order_id,status) INCLUDE(shop_order_id,refund_amount);';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(''dbo.seller_payout_batch_items'') AND name=''UX_seller_payout_batch_items_active_payout'') DROP INDEX UX_seller_payout_batch_items_active_payout ON dbo.seller_payout_batch_items; CREATE UNIQUE INDEX UX_seller_payout_batch_items_active_payout ON dbo.seller_payout_batch_items(payout_id) WHERE status IN (''PENDING'',''PAID'');';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(''dbo.seller_payout_batch_items'') AND name=''IX_seller_payout_batch_items_batch_shop'') DROP INDEX IX_seller_payout_batch_items_batch_shop ON dbo.seller_payout_batch_items; CREATE INDEX IX_seller_payout_batch_items_batch_shop ON dbo.seller_payout_batch_items(batch_id,shop_id,status);';

EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(''dbo.seller_payouts'') AND name=''IX_seller_payouts_batch_eligibility'') DROP INDEX IX_seller_payouts_batch_eligibility ON dbo.seller_payouts; CREATE INDEX IX_seller_payouts_batch_eligibility ON dbo.seller_payouts(status,eligible_at,funds_confirmed_at,shop_id);';

-- END EXISTING DATABASE UPGRADE
END;

-- Shared additions are installed for both paths, including all former standalone migrations.

-- One shared shipping policy for both new and existing databases.
EXEC sys.sp_executesql N'IF OBJECT_ID(N''dbo.shipping_fee_settings'', N''U'') IS NULL
BEGIN
    CREATE TABLE dbo.shipping_fee_settings (
        settings_id BIGINT NOT NULL CONSTRAINT PK_shipping_fee_settings PRIMARY KEY,
        base_fee DECIMAL(18,2) NOT NULL CONSTRAINT DF_shipping_fee_settings_base DEFAULT (15000),
        included_distance_km DECIMAL(11,3) NOT NULL CONSTRAINT DF_shipping_fee_settings_included DEFAULT (2),
        additional_fee_per_km DECIMAL(18,2) NOT NULL CONSTRAINT DF_shipping_fee_settings_additional DEFAULT (5000),
        max_delivery_distance_km DECIMAL(11,3) NOT NULL CONSTRAINT DF_shipping_fee_settings_range DEFAULT (15),
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_shipping_fee_settings_created DEFAULT (SYSUTCDATETIME()),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_shipping_fee_settings_updated DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_shipping_fee_settings_singleton CHECK (settings_id = 1),
        CONSTRAINT CK_shipping_fee_settings_base CHECK (base_fee >= 0 AND base_fee = FLOOR(base_fee)),
        CONSTRAINT CK_shipping_fee_settings_additional CHECK (additional_fee_per_km >= 0 AND additional_fee_per_km = FLOOR(additional_fee_per_km)),
        CONSTRAINT CK_shipping_fee_settings_distance CHECK (
            included_distance_km >= 0 AND max_delivery_distance_km > 0
            AND included_distance_km <= max_delivery_distance_km),
        CONSTRAINT CK_shipping_fee_settings_max_fee CHECK (
            ROUND(base_fee + (max_delivery_distance_km - included_distance_km) * additional_fee_per_km, -3)
            <= 9999999999999999.99),
        CONSTRAINT CK_shipping_fee_settings_dates CHECK (updated_at >= created_at)
    );
END;';

-- Complete legacy shop addresses from the same shop's stored components.
-- Preserve any formatted address already entered by the seller.
EXEC sys.sp_executesql N'UPDATE dbo.shop_locations
 SET formatted_address=CONCAT_WS(N'', '',NULLIF(LTRIM(RTRIM(address_line)),N''''),
     NULLIF(LTRIM(RTRIM(ward)),N''''),NULLIF(LTRIM(RTRIM(district)),N''''),
     NULLIF(LTRIM(RTRIM(province)),N''''))
 WHERE NULLIF(LTRIM(RTRIM(formatted_address)),N'''') IS NULL
   AND NULLIF(LTRIM(RTRIM(address_line)),N'''') IS NOT NULL
   AND NULLIF(LTRIM(RTRIM(ward)),N'''') IS NOT NULL
   AND NULLIF(LTRIM(RTRIM(province)),N'''') IS NOT NULL;';

EXEC sys.sp_executesql N'IF EXISTS (
        SELECT 1 FROM dbo.products
        GROUP BY shop_id, LTRIM(RTRIM(name)) COLLATE Latin1_General_100_CI_AS
        HAVING COUNT(*) > 1
    )
    BEGIN
        SELECT shop_id, LTRIM(RTRIM(name)) COLLATE Latin1_General_100_CI_AS AS duplicate_name,
               COUNT(*) AS product_count
        FROM dbo.products
        GROUP BY shop_id, LTRIM(RTRIM(name)) COLLATE Latin1_General_100_CI_AS
        HAVING COUNT(*) > 1;
        THROW 51123, N''Cần xử lý tên sản phẩm trùng trong cùng shop trước khi chạy migration.'', 1;
    END;

    IF COL_LENGTH(N''dbo.products'', N''normalized_name'') IS NULL
        EXEC(N''ALTER TABLE dbo.products ADD normalized_name
               AS (LTRIM(RTRIM(name)) COLLATE Latin1_General_100_CI_AS) PERSISTED;'');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID(N''dbo.products'') AND name = N''UX_products_shop_name'')
        EXEC(N''CREATE UNIQUE INDEX UX_products_shop_name ON dbo.products(shop_id, normalized_name);'');';

EXEC sys.sp_executesql N'IF NOT EXISTS(SELECT 1 FROM dbo.loyalty_rules WHERE rule_id=1)
 INSERT dbo.loyalty_rules(rule_id) VALUES(1);
IF NOT EXISTS(SELECT 1 FROM dbo.subscription_plans WHERE name=N''Free'')
 INSERT dbo.subscription_plans(name,description,price,billing_period,benefits,max_products,status)
 VALUES(N''Free'',N''Gói miễn phí mặc định cho cửa hàng.'',0,''MONTHLY'',N''Được public tối đa 5 sản phẩm.'',5,''ACTIVE'');';

-- BEGIN SHARED VIEWS, FUNCTIONS, PROCEDURES AND TRIGGERS

EXEC sys.sp_executesql N'IF NOT EXISTS (SELECT 1 FROM dbo.shipping_fee_settings WHERE settings_id = 1)
    INSERT dbo.shipping_fee_settings (settings_id) VALUES (1);';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_shipping_fee_settings_guard
ON dbo.shipping_fee_settings
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM deleted) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted)
        THROW 51600, N''Không được xóa cấu hình phí vận chuyển.'', 1;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.settings_id = i.settings_id
        WHERE i.created_at <> d.created_at
    )
        THROW 51601, N''Không được thay đổi thời điểm tạo cấu hình phí vận chuyển.'', 1;
    IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.tr_shipping_fee_settings_guard'')) > 1 RETURN;

    UPDATE s SET updated_at = SYSUTCDATETIME()
    FROM dbo.shipping_fee_settings s JOIN inserted i ON i.settings_id = s.settings_id;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER FUNCTION dbo.fn_catalog_category_active(@category_id BIGINT)
RETURNS BIT
AS
BEGIN
  DECLARE @parent BIGINT, @status VARCHAR(20), @depth INT = 0;
  WHILE @category_id IS NOT NULL AND @depth < 100
  BEGIN
    SET @status = NULL;
    SET @parent = NULL;
    SELECT @status = status, @parent = parent_category_id FROM dbo.categories WHERE category_id = @category_id;
    IF @status IS NULL OR @status <> ''ACTIVE'' RETURN 0;
    SET @category_id = @parent;
    SET @depth += 1;
  END;
  IF @category_id IS NOT NULL RETURN 0; -- a cycle or unsupported hierarchy depth
  RETURN 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER VIEW dbo.v_public_shops
AS
SELECT shop_id, shop_name, description, logo_url, banner_url, phone, availability_status
FROM dbo.shops WHERE status = ''ACTIVE'';';

EXEC sys.sp_executesql N'CREATE OR ALTER VIEW dbo.v_public_shop_business_hours
AS
SELECT h.shop_id, h.day_of_week, h.open_time, h.close_time, h.is_closed
FROM dbo.shop_business_hours h
JOIN dbo.v_public_shops s ON s.shop_id = h.shop_id;';

EXEC sys.sp_executesql N'CREATE OR ALTER VIEW dbo.v_public_shop_variants
AS
SELECT s.shop_id, s.shop_name, s.availability_status,
       p.product_id, p.name AS product_name, p.slug AS product_slug, p.description, p.origin,
       pi.image_url AS product_image_url,
       pv.variant_id, pv.variant_name, pv.thumbnail_url,
       pv.price, pv.compare_at_price, pv.weight, pv.unit, pv.status AS variant_status,
       COALESCE(stock.available_quantity, 0) AS available_quantity,
       CAST(CASE WHEN s.availability_status = ''OPEN'' AND pv.status = ''ACTIVE''
                 AND COALESCE(stock.available_quantity, 0) > 0 THEN 1 ELSE 0 END AS BIT) AS can_purchase
FROM dbo.v_public_shops s
JOIN dbo.products p ON p.shop_id = s.shop_id
JOIN dbo.product_variants pv ON pv.product_id = p.product_id
LEFT JOIN dbo.product_images pi ON pi.product_id = p.product_id
     AND pi.variant_id IS NULL AND pi.is_primary = 1
OUTER APPLY (
    SELECT SUM(CONVERT(BIGINT, ib.quantity_on_hand) - ib.reserved_quantity) AS available_quantity
    FROM dbo.inventory_batches ib
    WHERE ib.variant_id = pv.variant_id AND ib.status = ''ACTIVE''
      -- Business date in Viet Nam (UTC+7). Expiry date itself remains sellable.
      AND (ib.expiry_date IS NULL OR ib.expiry_date >= CONVERT(DATE, DATEADD(HOUR, 7, SYSUTCDATETIME())))
) stock
WHERE p.status = ''ACTIVE'' AND p.moderation_blocked = 0
  AND pv.status IN (''ACTIVE'', ''OUT_OF_STOCK'')
  AND pv.price >= 10000 AND pv.price = FLOOR(pv.price)
  AND EXISTS (SELECT 1 FROM dbo.product_categories pc
              WHERE pc.product_id = p.product_id AND dbo.fn_catalog_category_active(pc.category_id) = 1);';

EXEC sys.sp_executesql N'CREATE OR ALTER VIEW dbo.v_public_shop_categories
AS
WITH active_tree AS (
    SELECT category_id, parent_category_id, name, slug, description, 1 AS depth
    FROM dbo.categories WHERE parent_category_id IS NULL AND status = ''ACTIVE''
    UNION ALL
    SELECT c.category_id, c.parent_category_id, c.name, c.slug, c.description, t.depth + 1
    FROM dbo.categories c JOIN active_tree t ON c.parent_category_id = t.category_id
    WHERE c.status = ''ACTIVE'' AND t.depth < 32
)
SELECT DISTINCT v.shop_id, pc.product_id, c.category_id, c.name AS category_name,
       c.slug, c.description, c.parent_category_id
FROM dbo.v_public_shop_variants v
JOIN dbo.product_categories pc ON pc.product_id = v.product_id
JOIN active_tree c ON c.category_id = pc.category_id;';

EXEC sys.sp_executesql N'CREATE OR ALTER PROCEDURE dbo.usp_save_category
    @category_id BIGINT = NULL OUTPUT,
    @parent_id BIGINT = NULL,
    @name NVARCHAR(MAX),
    @slug VARCHAR(MAX),
    @description NVARCHAR(MAX) = NULL,
    @status VARCHAR(20) = ''ACTIVE''
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    -- Standalone mutation: caller must not wrap this procedure in a larger transaction.
    IF @@TRANCOUNT <> 0 THROW 51029, N''Call category mutation outside an existing transaction.'', 1;
    SET @name = LTRIM(RTRIM(@name));
    SET @slug = LTRIM(RTRIM(@slug));
    SET @description = NULLIF(LTRIM(RTRIM(@description)), N'''');
    IF @name IS NULL OR LEN(@name) NOT BETWEEN 1 AND 150
        THROW 51030, N''Ten danh muc phai co 1-150 ky tu.'', 1;
    IF @slug IS NULL OR LEN(@slug) NOT BETWEEN 1 AND 200
       OR @slug COLLATE Latin1_General_100_BIN2 LIKE ''%[^a-z0-9-]%''
       OR @slug LIKE ''-%'' OR @slug LIKE ''%-'' OR @slug LIKE ''%--%''
        THROW 51031, N''Slug khong hop le.'', 1;
    IF LEN(@description) > 1000 THROW 51032, N''Mo ta toi da 1000 ky tu.'', 1;
    IF @status IS NULL OR @status COLLATE Latin1_General_100_BIN2 NOT IN (''ACTIVE'', ''INACTIVE'')
        THROW 51033, N''Trang thai khong hop le.'', 1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock_result INT;
        EXEC @lock_result = sys.sp_getapplock @Resource = N''VietFreshHub.category-tree'',
             @LockMode = ''Exclusive'', @LockOwner = ''Transaction'', @LockTimeout = 10000;
        IF @lock_result < 0 THROW 51034, N''Khong the khoa cay danh muc, vui long thu lai.'', 1;
        IF @category_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.categories WHERE category_id = @category_id)
            THROW 51035, N''Danh muc khong ton tai.'', 1;
        IF EXISTS (SELECT 1 FROM dbo.categories WHERE LOWER(slug) = LOWER(@slug)
                   AND (@category_id IS NULL OR category_id <> @category_id))
            THROW 51036, N''Slug da ton tai.'', 1;
        IF EXISTS (SELECT 1 FROM dbo.categories
                   WHERE (parent_category_id = @parent_id OR (parent_category_id IS NULL AND @parent_id IS NULL))
                     AND LTRIM(RTRIM(name)) COLLATE Latin1_General_100_CI_AS = @name COLLATE Latin1_General_100_CI_AS
                     AND (@category_id IS NULL OR category_id <> @category_id))
            THROW 51037, N''Ten da ton tai trong cung danh muc cha.'', 1;
        DECLARE @ancestor BIGINT = @parent_id, @next BIGINT, @ancestor_status VARCHAR(20), @parent_depth INT = 0;
        DECLARE @seen TABLE (category_id BIGINT PRIMARY KEY);
        WHILE @ancestor IS NOT NULL
        BEGIN
            IF @ancestor = @category_id OR EXISTS (SELECT 1 FROM @seen WHERE category_id = @ancestor)
                THROW 51038, N''Danh muc cha tao vong lap.'', 1;
            IF NOT EXISTS (SELECT 1 FROM dbo.categories WHERE category_id = @ancestor)
                THROW 51039, N''Danh muc cha khong ton tai.'', 1;
            INSERT INTO @seen VALUES (@ancestor);
            SELECT @next = parent_category_id, @ancestor_status = status FROM dbo.categories WHERE category_id = @ancestor;
            IF @status = ''ACTIVE'' AND @ancestor_status <> ''ACTIVE''
                THROW 51040, N''Can kich hoat danh muc cha truoc.'', 1;
            SET @parent_depth += 1;
            IF @parent_depth >= 32 THROW 51042, N''Cay danh muc toi da 32 cap.'', 1;
            SET @ancestor = @next;
        END;
        IF @status = ''INACTIVE'' AND EXISTS (SELECT 1 FROM dbo.categories WHERE parent_category_id = @category_id AND status = ''ACTIVE'')
            THROW 51041, N''Can ngung danh muc con truoc.'', 1;
        DECLARE @subtree_depth INT = 1;
        ;WITH subtree AS (
            SELECT category_id, 1 AS depth FROM dbo.categories WHERE category_id = @category_id
            UNION ALL
            SELECT c.category_id, t.depth + 1 FROM dbo.categories c JOIN subtree t ON c.parent_category_id = t.category_id
            WHERE t.depth < 33
        )
        SELECT @subtree_depth = COALESCE(MAX(depth), 1) FROM subtree;
        IF @parent_depth + @subtree_depth > 32 THROW 51042, N''Cay danh muc toi da 32 cap.'', 1;
        IF @category_id IS NULL
        BEGIN
            INSERT INTO dbo.categories (parent_category_id, name, slug, description, status)
            VALUES (@parent_id, @name, @slug, @description, @status);
            SET @category_id = CONVERT(BIGINT, SCOPE_IDENTITY());
        END
        ELSE
            UPDATE dbo.categories SET parent_category_id = @parent_id, name = @name,
                slug = @slug, description = @description, status = @status, updated_at = SYSUTCDATETIME()
            WHERE category_id = @category_id;
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_products_publication ON dbo.products AFTER INSERT, UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.product_id = i.product_id
    WHERE i.status = ''ACTIVE''
      AND (NOT EXISTS (SELECT 1 FROM dbo.product_categories pc WITH (UPDLOCK, HOLDLOCK)
           WHERE pc.product_id = i.product_id AND dbo.fn_catalog_category_active(pc.category_id) = 1)
       OR NOT EXISTS (SELECT 1 FROM dbo.product_variants v WITH (UPDLOCK, HOLDLOCK)
           WHERE v.product_id = i.product_id AND v.status IN (''ACTIVE'', ''OUT_OF_STOCK''))))
    THROW 51103, N''Can danh muc hoat dong va SKU mo ban truoc khi dang ban.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_product_categories_publication ON dbo.product_categories AFTER INSERT, UPDATE, DELETE AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM dbo.products p WITH (UPDLOCK, HOLDLOCK)
    WHERE p.status = ''ACTIVE'' AND p.product_id IN (SELECT product_id FROM inserted UNION SELECT product_id FROM deleted)
      AND NOT EXISTS (SELECT 1 FROM dbo.product_categories pc WITH (UPDLOCK, HOLDLOCK)
        WHERE pc.product_id = p.product_id AND dbo.fn_catalog_category_active(pc.category_id) = 1))
    THROW 51104, N''Khong the bo danh muc hoat dong cuoi cung cua san pham dang ban.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_categories_publication ON dbo.categories AFTER UPDATE, DELETE AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM dbo.products p WITH (UPDLOCK, HOLDLOCK)
    WHERE p.status = ''ACTIVE'' AND NOT EXISTS (SELECT 1 FROM dbo.product_categories pc WITH (UPDLOCK, HOLDLOCK)
      WHERE pc.product_id = p.product_id AND dbo.fn_catalog_category_active(pc.category_id) = 1))
    THROW 51105, N''Danh muc/thay doi cha se an san pham dang ban. Chuyen san pham INACTIVE hoac gan danh muc khac truoc.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_product_variants_publication ON dbo.product_variants AFTER INSERT, UPDATE, DELETE AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM dbo.products p WITH (UPDLOCK, HOLDLOCK)
    WHERE p.status = ''ACTIVE'' AND p.product_id IN (SELECT product_id FROM inserted UNION SELECT product_id FROM deleted)
      AND NOT EXISTS (SELECT 1 FROM dbo.product_variants v WITH (UPDLOCK, HOLDLOCK)
        WHERE v.product_id = p.product_id AND v.status IN (''ACTIVE'', ''OUT_OF_STOCK'')))
    THROW 51106, N''Khong the dong SKU mo ban cuoi cung. Ngung ban san pham truoc.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER PROCEDURE dbo.usp_review_product
  @product_id BIGINT, @admin_user_id BIGINT, @decision VARCHAR(20), @note NVARCHAR(1000) = NULL
AS
BEGIN
  SET NOCOUNT ON; SET XACT_ABORT ON;
  IF @decision NOT IN (''APPROVED'', ''REJECTED'') OR @decision IS NULL THROW 51107, N''Quyet dinh khong hop le.'', 1;
  IF @decision = ''REJECTED'' AND NULLIF(LTRIM(RTRIM(@note)), N'''') IS NULL THROW 51108, N''Can ly do tu choi.'', 1;
  IF NOT EXISTS (SELECT 1 FROM dbo.users u JOIN dbo.user_roles ur ON ur.user_id = u.user_id JOIN dbo.roles r ON r.role_id = ur.role_id
    WHERE u.user_id = @admin_user_id AND u.status = ''ACTIVE'' AND r.role_name IN (''ROLE_ADMIN'', ''ADMIN'')) THROW 51109, N''Can quyen ADMIN.'', 1;
  BEGIN TRY
    BEGIN TRANSACTION;
    IF NOT EXISTS (SELECT 1 FROM dbo.products WITH (UPDLOCK, HOLDLOCK) WHERE product_id = @product_id AND status <> ''DELETED'')
      THROW 51110, N''San pham khong ton tai hoac da ngung kinh doanh.'', 1;
    UPDATE dbo.products SET approval_status = @decision, approved_by = @admin_user_id, approved_at = SYSUTCDATETIME(),
      updated_at = SYSUTCDATETIME()
    WHERE product_id = @product_id;
    INSERT INTO dbo.product_review_history(product_id, decision, reviewed_by, note) VALUES(@product_id, @decision, @admin_user_id, @note);
    COMMIT TRANSACTION;
  END TRY BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION; THROW;
  END CATCH;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_order_items_preparation_snapshot ON dbo.order_items AFTER INSERT AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.shop_orders so ON so.shop_order_id = i.shop_order_id
    WHERE so.status NOT IN (''PENDING'', ''CONFIRMED'', ''PREPARING''))
    THROW 51116, N''Khong the sua dong hang sau khi san sang giao.'', 1;
  UPDATE oi SET requires_preparation_check_snapshot = p.requires_preparation_check,
    preparation_note_snapshot = CASE WHEN p.requires_preparation_check = 1 THEN p.preparation_note ELSE NULL END
  FROM dbo.order_items oi JOIN inserted i ON i.order_item_id = oi.order_item_id
  JOIN dbo.product_variants v ON v.variant_id = i.variant_id JOIN dbo.products p ON p.product_id = v.product_id;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_order_items_preparation_invalidate ON dbo.order_items AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.order_item_id = i.order_item_id
    JOIN dbo.shop_orders so ON so.shop_order_id = i.shop_order_id
    WHERE so.status NOT IN (''PENDING'', ''CONFIRMED'', ''PREPARING'')
      AND (i.quantity <> d.quantity OR i.variant_id <> d.variant_id OR i.shop_order_id <> d.shop_order_id
        OR i.requires_preparation_check_snapshot <> d.requires_preparation_check_snapshot
        OR ISNULL(i.preparation_note_snapshot, N'''') <> ISNULL(d.preparation_note_snapshot, N'''')))
    THROW 51116, N''Khong the sua yeu cau chuan bi sau khi san sang giao.'', 1;
  DELETE pc FROM dbo.order_item_preparation_checks pc JOIN inserted i ON i.order_item_id = pc.order_item_id
  JOIN deleted d ON d.order_item_id = i.order_item_id
  WHERE i.quantity <> d.quantity OR i.variant_id <> d.variant_id OR i.shop_order_id <> d.shop_order_id
    OR i.requires_preparation_check_snapshot <> d.requires_preparation_check_snapshot
    OR ISNULL(i.preparation_note_snapshot, N'''') <> ISNULL(d.preparation_note_snapshot, N'''');
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER PROCEDURE dbo.usp_confirm_order_item_preparation
  @order_item_id BIGINT, @shop_id BIGINT, @user_id BIGINT, @note NVARCHAR(1000) = NULL
AS
BEGIN
  SET NOCOUNT ON; SET XACT_ABORT ON;
  IF NOT EXISTS (SELECT 1 FROM dbo.shop_members sm JOIN dbo.users u ON u.user_id = sm.user_id
    WHERE sm.shop_id = @shop_id AND sm.user_id = @user_id AND sm.status = ''ACTIVE'' AND u.status = ''ACTIVE'') THROW 51111, N''Can quyen nhan vien shop.'', 1;
  BEGIN TRY
    BEGIN TRANSACTION;
    IF NOT EXISTS (SELECT 1 FROM dbo.order_items oi WITH (UPDLOCK, HOLDLOCK) JOIN dbo.shop_orders so WITH (UPDLOCK, HOLDLOCK) ON so.shop_order_id = oi.shop_order_id
      WHERE oi.order_item_id = @order_item_id AND so.shop_id = @shop_id AND so.status IN (''PENDING'', ''CONFIRMED'', ''PREPARING'') AND oi.requires_preparation_check_snapshot = 1)
      THROW 51112, N''Dong hang khong cho phep xac nhan chuan bi.'', 1;
    UPDATE dbo.order_item_preparation_checks WITH (UPDLOCK, HOLDLOCK) SET checked_by = @user_id, checked_at = SYSUTCDATETIME(), note = @note WHERE order_item_id = @order_item_id;
    IF @@ROWCOUNT = 0 INSERT INTO dbo.order_item_preparation_checks(order_item_id, checked_by, note) VALUES(@order_item_id, @user_id, @note);
    COMMIT TRANSACTION;
  END TRY BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION; THROW;
  END CATCH;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER PROCEDURE dbo.usp_mark_shop_order_ready
  @shop_order_id BIGINT, @shop_id BIGINT, @user_id BIGINT
AS
BEGIN
  SET NOCOUNT ON; SET XACT_ABORT ON;
  IF NOT EXISTS (SELECT 1 FROM dbo.shop_members sm JOIN dbo.users u ON u.user_id = sm.user_id
    WHERE sm.shop_id = @shop_id AND sm.user_id = @user_id AND sm.status = ''ACTIVE'' AND u.status = ''ACTIVE'') THROW 51113, N''Can quyen nhan vien shop.'', 1;
  BEGIN TRY
    BEGIN TRANSACTION;
    IF NOT EXISTS (SELECT 1 FROM dbo.shop_orders WITH (UPDLOCK, HOLDLOCK) WHERE shop_order_id = @shop_order_id AND shop_id = @shop_id AND status IN (''CONFIRMED'', ''PREPARING''))
      THROW 51114, N''Don hang khong the chuyen san sang giao.'', 1;
    IF EXISTS (SELECT 1 FROM dbo.order_items oi WITH (UPDLOCK, HOLDLOCK) LEFT JOIN dbo.order_item_preparation_checks pc WITH (UPDLOCK, HOLDLOCK) ON pc.order_item_id = oi.order_item_id
      WHERE oi.shop_order_id = @shop_order_id AND oi.requires_preparation_check_snapshot = 1 AND pc.order_item_id IS NULL)
      THROW 51115, N''Chua xac nhan day du yeu cau chuan bi.'', 1;
    UPDATE dbo.shop_orders SET status = ''READY_FOR_DELIVERY'', ready_at = SYSUTCDATETIME() WHERE shop_order_id = @shop_order_id;
    INSERT INTO dbo.order_status_history(shop_order_id, status, changed_by, note) VALUES(@shop_order_id, ''READY_FOR_DELIVERY'', @user_id, N''Da kiem tra yeu cau chuan bi va dong goi.'');
    COMMIT TRANSACTION;
  END TRY BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION; THROW;
  END CATCH;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_stock_allocations_variant_match ON dbo.stock_allocations
AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted a
        JOIN dbo.order_items oi WITH (UPDLOCK, HOLDLOCK) ON oi.order_item_id = a.order_item_id
        JOIN dbo.inventory_batches b WITH (UPDLOCK, HOLDLOCK) ON b.batch_id = a.batch_id
        WHERE oi.variant_id <> b.variant_id)
        THROW 51200, N''Dòng đơn và lô phân bổ phải thuộc cùng SKU.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_order_items_allocation_variant_match ON dbo.order_items
AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(variant_id) AND EXISTS (SELECT 1 FROM inserted oi
        JOIN dbo.stock_allocations a WITH (UPDLOCK, HOLDLOCK) ON a.order_item_id = oi.order_item_id
        JOIN dbo.inventory_batches b WITH (UPDLOCK, HOLDLOCK) ON b.batch_id = a.batch_id
        WHERE oi.variant_id <> b.variant_id)
        THROW 51201, N''Không đổi SKU làm sai lô đã phân bổ cho dòng đơn.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_inventory_batches_allocation_variant_match ON dbo.inventory_batches
AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(variant_id) AND EXISTS (SELECT 1 FROM inserted b
        JOIN dbo.stock_allocations a WITH (UPDLOCK, HOLDLOCK) ON a.batch_id = b.batch_id
        JOIN dbo.order_items oi WITH (UPDLOCK, HOLDLOCK) ON oi.order_item_id = a.order_item_id
        WHERE oi.variant_id <> b.variant_id)
        THROW 51202, N''Không đổi SKU làm sai dòng đơn đã phân bổ từ lô.'', 1;
END;';

EXEC sys.sp_executesql N'-- Multi-seller ownership and order integrity.
CREATE OR ALTER TRIGGER dbo.tr_order_items_shop_match ON dbo.order_items
AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT (UPDATE(shop_order_id) OR UPDATE(variant_id)) RETURN;
    -- Giu khoa parent toi cuoi transaction, ke ca khi database bat RCSI.
    IF EXISTS (SELECT 1 FROM inserted i
        JOIN dbo.shop_orders so WITH (UPDLOCK, HOLDLOCK) ON so.shop_order_id = i.shop_order_id
        JOIN dbo.product_variants v WITH (UPDLOCK, HOLDLOCK) ON v.variant_id = i.variant_id
        JOIN dbo.products p WITH (UPDLOCK, HOLDLOCK) ON p.product_id = v.product_id
        WHERE p.shop_id <> so.shop_id)
        THROW 51310, N''SKU trong dong don phai thuoc dung shop cua don con.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_shop_orders_shop_history ON dbo.shop_orders
AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(shop_id) RETURN;
    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.shop_order_id = i.shop_order_id
        WHERE i.shop_id <> d.shop_id AND (
            EXISTS (SELECT 1 FROM dbo.order_items oi WITH (UPDLOCK, HOLDLOCK)
                    WHERE oi.shop_order_id = i.shop_order_id)
            OR EXISTS (SELECT 1 FROM dbo.seller_payouts sp WITH (UPDLOCK, HOLDLOCK)
                       WHERE sp.shop_order_id = i.shop_order_id)))
        THROW 51311, N''Khong doi shop cua don con da co dong hang hoac doi soat.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_products_shop_history ON dbo.products
AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(shop_id) RETURN;
    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.product_id = i.product_id
        JOIN dbo.product_variants v WITH (UPDLOCK, HOLDLOCK) ON v.product_id = i.product_id
        JOIN dbo.order_items oi WITH (UPDLOCK, HOLDLOCK) ON oi.variant_id = v.variant_id
        WHERE i.shop_id <> d.shop_id)
        THROW 51312, N''Khong chuyen shop cua san pham da phat sinh dong don.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_product_variants_product_history ON dbo.product_variants
AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(product_id) RETURN;
    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.variant_id = i.variant_id
        JOIN dbo.order_items oi WITH (UPDLOCK, HOLDLOCK) ON oi.variant_id = i.variant_id
        WHERE i.product_id <> d.product_id)
        THROW 51313, N''Khong chuyen san pham cua SKU da phat sinh dong don.'', 1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER PROCEDURE dbo.usp_moderate_product
  @product_id BIGINT, @admin_user_id BIGINT, @blocked BIT, @note NVARCHAR(1000)
AS
BEGIN
  SET NOCOUNT ON; SET XACT_ABORT ON;
  IF @blocked IS NULL OR NULLIF(LTRIM(RTRIM(@note)),N'''') IS NULL
    THROW 51120, N''Can trang thai khoa va ly do xu ly hau kiem.'', 1;
  IF NOT EXISTS (SELECT 1 FROM dbo.users u JOIN dbo.user_roles ur ON ur.user_id=u.user_id
    JOIN dbo.roles r ON r.role_id=ur.role_id WHERE u.user_id=@admin_user_id AND u.status=''ACTIVE''
      AND r.role_name IN (''ROLE_ADMIN'',''ADMIN''))
    THROW 51121, N''Can quyen ADMIN de khoa/mo khoa san pham.'', 1;
  BEGIN TRY
    BEGIN TRANSACTION;
    IF NOT EXISTS (SELECT 1 FROM dbo.products WITH (UPDLOCK,HOLDLOCK)
      WHERE product_id=@product_id AND status<>''DELETED'')
      THROW 51122, N''San pham khong ton tai hoac da ngung kinh doanh.'', 1;
    UPDATE dbo.products SET moderation_blocked=@blocked,
      moderation_reason=CASE WHEN @blocked=1 THEN LTRIM(RTRIM(@note)) ELSE NULL END,
      updated_at=SYSUTCDATETIME() WHERE product_id=@product_id;
    INSERT dbo.audit_events(actor_id,action,entity_type,entity_id,reason,created_at)
      VALUES(@admin_user_id,CASE WHEN @blocked=1 THEN ''MODERATION_BLOCK'' ELSE ''MODERATION_UNBLOCK'' END,
        ''PRODUCT'',@product_id,@note,SYSUTCDATETIME());
    COMMIT TRANSACTION;
  END TRY BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
  END CATCH;
END;';

EXEC sys.sp_executesql N'-- BEGIN FINANCIAL GUARDS 20261101
-- Loaded on every run of this file. No transfer or reward is issued here.
CREATE OR ALTER PROCEDURE dbo.usp_sync_shop_payment_status @order_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @parent VARCHAR(30), @cod BIT = 0;
    SELECT @parent = payment_status FROM dbo.orders WITH (UPDLOCK, HOLDLOCK) WHERE order_id = @order_id;
    IF @parent IS NULL RETURN;
    IF EXISTS (SELECT 1 FROM dbo.payments p JOIN dbo.payment_methods m ON m.payment_method_id=p.payment_method_id
               WHERE p.order_id=@order_id AND m.method_code=''COD'')
       AND NOT EXISTS (SELECT 1 FROM dbo.payments p JOIN dbo.payment_methods m ON m.payment_method_id=p.payment_method_id
                       WHERE p.order_id=@order_id AND m.method_code<>''COD'') SET @cod=1;
    UPDATE s SET payment_status = v.next_status
    FROM dbo.shop_orders s
    CROSS APPLY (SELECT CASE
        WHEN @parent=''REFUNDED'' THEN ''REFUNDED''
        WHEN s.payment_status IN (''PARTIALLY_REFUNDED'',''REFUNDED'') THEN s.payment_status
        WHEN @parent=''PARTIALLY_REFUNDED'' THEN s.payment_status
        WHEN @cod=1 THEN CASE WHEN EXISTS (SELECT 1 FROM dbo.deliveries d WHERE d.shop_order_id=s.shop_order_id AND d.status=''DELIVERED'') THEN ''PAID'' ELSE ''UNPAID'' END
        ELSE @parent END AS next_status) v
    WHERE s.order_id=@order_id AND s.payment_status<>v.next_status;
    -- COD is paid at order level only when every shop has collected its delivery.
    IF @cod=1 AND @parent NOT IN (''PARTIALLY_REFUNDED'',''REFUNDED'')
       AND EXISTS (SELECT 1 FROM dbo.shop_orders WHERE order_id=@order_id)
    BEGIN
        DECLARE @next VARCHAR(30)=CASE WHEN EXISTS (SELECT 1 FROM dbo.shop_orders WHERE order_id=@order_id AND payment_status<>''PAID'') THEN ''UNPAID'' ELSE ''PAID'' END;
        UPDATE dbo.orders SET payment_status=@next WHERE order_id=@order_id AND payment_status<>@next;
    END;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_orders_sync_shop_payment ON dbo.orders AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(payment_status) RETURN;
    DECLARE @id BIGINT;
    DECLARE affected CURSOR LOCAL FAST_FORWARD FOR
        SELECT i.order_id FROM inserted i JOIN deleted d ON d.order_id=i.order_id WHERE i.payment_status<>d.payment_status;
    OPEN affected; FETCH NEXT FROM affected INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN EXEC dbo.usp_sync_shop_payment_status @id; FETCH NEXT FROM affected INTO @id; END;
    CLOSE affected; DEALLOCATE affected;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_shop_orders_sync_payment ON dbo.shop_orders AFTER INSERT AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @id BIGINT;
    DECLARE affected CURSOR LOCAL FAST_FORWARD FOR SELECT DISTINCT order_id FROM inserted;
    OPEN affected; FETCH NEXT FROM affected INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN EXEC dbo.usp_sync_shop_payment_status @id; FETCH NEXT FROM affected INTO @id; END;
    CLOSE affected; DEALLOCATE affected;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_payments_sync_shop_payment ON dbo.payments AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @id BIGINT;
    DECLARE affected CURSOR LOCAL FAST_FORWARD FOR SELECT order_id FROM inserted UNION SELECT order_id FROM deleted;
    OPEN affected; FETCH NEXT FROM affected INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN EXEC dbo.usp_sync_shop_payment_status @id; FETCH NEXT FROM affected INTO @id; END;
    CLOSE affected; DEALLOCATE affected;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_deliveries_sync_shop_payment ON dbo.deliveries AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @id BIGINT;
    DECLARE affected CURSOR LOCAL FAST_FORWARD FOR
        SELECT DISTINCT s.order_id FROM dbo.shop_orders s JOIN
            (SELECT shop_order_id FROM inserted UNION SELECT shop_order_id FROM deleted) a ON a.shop_order_id=s.shop_order_id;
    OPEN affected; FETCH NEXT FROM affected INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN EXEC dbo.usp_sync_shop_payment_status @id; FETCH NEXT FROM affected INTO @id; END;
    CLOSE affected; DEALLOCATE affected;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_refund_requests_limits ON dbo.refund_requests AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    -- Shared order locks serialize refund scope/amount checks with payout admission.
    DECLARE @lockId BIGINT;
    SELECT @lockId=o.order_id FROM dbo.orders o WITH (UPDLOCK,HOLDLOCK)
        JOIN (SELECT order_id FROM inserted UNION SELECT order_id FROM deleted) a ON a.order_id=o.order_id;
    IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.refund_request_id=d.refund_request_id
        WHERE d.status=''PAID'' AND (i.refund_request_id IS NULL OR i.status<>''PAID'' OR i.order_id<>d.order_id
            OR ISNULL(i.shop_order_id,0)<>ISNULL(d.shop_order_id,0) OR i.refund_amount<>d.refund_amount
            OR i.bank_name<>d.bank_name OR i.bank_account_number<>d.bank_account_number OR i.account_holder_name<>d.account_holder_name))
        THROW 51401,N''Khong sua/xoa yeu cau hoan tien da chi tra; can but toan doi soat rieng.'',1;
    IF EXISTS (SELECT 1 FROM dbo.refund_requests r WITH (UPDLOCK,HOLDLOCK)
        JOIN (SELECT order_id FROM inserted UNION SELECT order_id FROM deleted) a ON a.order_id=r.order_id
        WHERE r.status IN (''PENDING'',''PROCESSING'',''PAID'') GROUP BY r.order_id
        HAVING MAX(CASE WHEN r.shop_order_id IS NULL THEN 1 ELSE 0 END)=1
           AND MAX(CASE WHEN r.shop_order_id IS NOT NULL THEN 1 ELSE 0 END)=1)
        THROW 51402,N''Khong hoan dong thoi toan don va tung shop cua cung don.'',1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.orders o ON o.order_id=i.order_id
        LEFT JOIN dbo.shop_orders s ON s.shop_order_id=i.shop_order_id
        WHERE i.status IN (''PENDING'',''PROCESSING'',''PAID'') AND i.refund_amount>CASE WHEN i.shop_order_id IS NULL THEN o.grand_total ELSE s.total_amount END)
        THROW 51403,N''Tien hoan vuot gia tri don/shop.'',1;
    IF EXISTS (SELECT 1 FROM dbo.refund_requests r WITH (UPDLOCK,HOLDLOCK)
        JOIN dbo.orders o ON o.order_id=r.order_id JOIN (SELECT DISTINCT order_id FROM inserted) a ON a.order_id=r.order_id
        WHERE r.status IN (''PENDING'',''PROCESSING'',''PAID'') GROUP BY r.order_id,o.grand_total HAVING SUM(r.refund_amount)>o.grand_total)
        THROW 51404,N''Tong tien yeu cau hoan vuot gia tri don.'',1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.shop_orders s ON s.order_id=i.order_id AND (i.shop_order_id IS NULL OR s.shop_order_id=i.shop_order_id)
        JOIN dbo.seller_payouts p ON p.shop_order_id=s.shop_order_id
        WHERE i.status IN (''PENDING'',''PROCESSING'',''PAID'') AND (p.status=''PAID'' OR EXISTS
            (SELECT 1 FROM dbo.seller_payout_batch_items b WITH (UPDLOCK,HOLDLOCK) WHERE b.payout_id=p.payout_id AND b.status IN (''PENDING'',''PAID''))))
        THROW 51405,N''Khoan shop da/duoc len lich chi tra; can doi soat payout truoc khi hoan tien.'',1;
END;';

EXEC sys.sp_executesql N'CREATE OR ALTER TRIGGER dbo.tr_payout_batch_items_eligibility ON dbo.seller_payout_batch_items AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @lockId BIGINT;
    SELECT @lockId=o.order_id FROM dbo.orders o WITH (UPDLOCK,HOLDLOCK) JOIN dbo.shop_orders s ON s.order_id=o.order_id
        JOIN dbo.seller_payouts p ON p.shop_order_id=s.shop_order_id
        JOIN (SELECT payout_id FROM inserted UNION SELECT payout_id FROM deleted) a ON a.payout_id=p.payout_id;
    IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.batch_item_id=d.batch_item_id
        WHERE d.status=''PAID'' AND (i.batch_item_id IS NULL OR i.status<>''PAID'' OR i.payout_id<>d.payout_id OR i.batch_id<>d.batch_id
            OR i.shop_id<>d.shop_id OR i.transfer_amount<>d.transfer_amount OR i.bank_code_snapshot<>d.bank_code_snapshot
            OR i.account_number_snapshot<>d.account_number_snapshot OR i.account_holder_snapshot<>d.account_holder_snapshot
            OR ISNULL(i.transfer_reference,N'''')<>ISNULL(d.transfer_reference,N'''')))
        THROW 51410,N''Khong sua/xoa lan chuyen tien da thanh cong.'',1;
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.batch_item_id=i.batch_item_id
        WHERE d.batch_item_id IS NOT NULL AND (i.payout_id<>d.payout_id OR i.shop_id<>d.shop_id OR i.batch_id<>d.batch_id))
        THROW 51411,N''Khong doi payout/shop/batch cua lich su chuyen tien.'',1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.seller_payouts p WITH (UPDLOCK,HOLDLOCK) ON p.payout_id=i.payout_id
        JOIN dbo.shop_orders s ON s.shop_order_id=p.shop_order_id JOIN dbo.seller_payout_batches b ON b.batch_id=i.batch_id
        LEFT JOIN deleted d ON d.batch_item_id=i.batch_item_id
        WHERE i.status IN (''PENDING'',''PAID'') AND (i.transfer_amount<>p.net_amount OR p.eligible_at IS NULL
            OR (b.scheduled_slot NOT LIKE ''MANUAL-%'' AND p.eligible_at>SYSUTCDATETIME())
            OR p.funds_confirmed_at IS NULL OR p.funds_confirmed_at>SYSUTCDATETIME() OR p.funds_source=''UNCONFIRMED''
            OR s.status<>''COMPLETED'' OR s.payment_status<>''PAID'' OR p.status IN (''FAILED'',''CANCELLED'')
            OR (p.status=''PAID'' AND (d.batch_item_id IS NULL OR d.status=''FAILED''))))
        THROW 51412,N''Payout chua du dieu kien/da chi tra hoac so tien khong khop.'',1;
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.batch_item_id=i.batch_item_id
        LEFT JOIN dbo.shop_payout_accounts a ON a.shop_id=i.shop_id
        WHERE i.status IN (''PENDING'',''PAID'') AND (d.batch_item_id IS NULL OR d.status=''FAILED'')
          AND (a.shop_id IS NULL OR i.bank_code_snapshot<>a.bank_code OR i.bank_name_snapshot<>a.bank_name
            OR i.account_number_snapshot<>a.account_number OR i.account_holder_snapshot<>a.account_holder))
        THROW 51413,N''Tai khoan nhan tien chua xac minh hoac snapshot khong khop.'',1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.seller_payouts p ON p.payout_id=i.payout_id
        JOIN dbo.shop_orders s ON s.shop_order_id=p.shop_order_id JOIN dbo.refund_requests r WITH (UPDLOCK,HOLDLOCK)
            ON r.order_id=s.order_id AND (r.shop_order_id IS NULL OR r.shop_order_id=s.shop_order_id)
        WHERE i.status IN (''PENDING'',''PAID'') AND r.status IN (''PENDING'',''PROCESSING'',''PAID''))
        THROW 51414,N''Don dang/co hoan tien; can doi soat truoc khi chi tra shop.'',1;
END;';

-- END SHARED VIEWS, FUNCTIONS, PROCEDURES AND TRIGGERS

EXEC sys.sp_executesql N'DECLARE @id BIGINT;
DECLARE affected CURSOR LOCAL FAST_FORWARD FOR SELECT order_id FROM dbo.orders ORDER BY order_id;
OPEN affected; FETCH NEXT FROM affected INTO @id;
WHILE @@FETCH_STATUS=0 BEGIN EXEC dbo.usp_sync_shop_payment_status @id; FETCH NEXT FROM affected INTO @id; END;
CLOSE affected; DEALLOCATE affected;
-- Validate any requests/attempts installed by earlier versions; do not guess how to reconcile conflicts.
UPDATE dbo.refund_requests SET status=status;
UPDATE dbo.seller_payout_batch_items SET status=status;';

COMMIT TRANSACTION;
SELECT CASE WHEN @freshInstall=1 THEN N'SCHEMA_CREATED' ELSE N'SCHEMA_UPDATED' END AS result,
       DB_NAME() AS database_name,(SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0) AS table_count;
END TRY
BEGIN CATCH
IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
