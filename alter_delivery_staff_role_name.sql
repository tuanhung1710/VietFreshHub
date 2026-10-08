SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @legacyRoleId int;
    DECLARE @canonicalRoleId int;

    SELECT @legacyRoleId = role_id
    FROM dbo.roles WITH (UPDLOCK, HOLDLOCK)
    WHERE role_name = 'DELIVERY_STAFF';

    SELECT @canonicalRoleId = role_id
    FROM dbo.roles WITH (UPDLOCK, HOLDLOCK)
    WHERE role_name = 'ROLE_DELIVERY_STAFF';

    IF @legacyRoleId IS NOT NULL
    BEGIN
        IF @canonicalRoleId IS NULL
        BEGIN
            UPDATE dbo.roles
            SET role_name = 'ROLE_DELIVERY_STAFF'
            WHERE role_id = @legacyRoleId;
        END
        ELSE
        BEGIN
            UPDATE legacyUserRole
            SET role_id = @canonicalRoleId
            FROM dbo.user_roles AS legacyUserRole
            WHERE legacyUserRole.role_id = @legacyRoleId
              AND NOT EXISTS (
                  SELECT 1
                  FROM dbo.user_roles AS canonicalUserRole WITH (UPDLOCK, HOLDLOCK)
                  WHERE canonicalUserRole.user_id = legacyUserRole.user_id
                    AND canonicalUserRole.role_id = @canonicalRoleId
              );

            DELETE FROM dbo.user_roles
            WHERE role_id = @legacyRoleId;

            DELETE FROM dbo.roles
            WHERE role_id = @legacyRoleId;
        END
    END

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
