#!/usr/bin/env bash
# Run once as root after reviewing this file. Existing database data is retained.
set -Eeuo pipefail
umask 077
[[ $EUID == 0 ]] || { echo 'Run this installer with sudo.' >&2; exit 1; }
image=${1:?Pass the immutable image reference}
[[ "$image" =~ ^ghcr\.io/tuanhung1710/vietfreshhub:[a-f0-9]{40}$ ]] || { echo 'Unexpected image reference.' >&2; exit 1; }
source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
target=/opt/vietfresh
legacy=/home/sonhg84/vietfresh-deploy
[[ -f "$legacy/compose.yaml" ]] || { echo 'Existing deployment was not found; refusing a fresh database installation.' >&2; exit 1; }
if [[ -f "$target/.installed" ]]; then
    echo 'Deployment is already installed. Use vietfresh-ops to deploy a new release.'
    exit 0
fi
install -d -m 700 "$target" "$target/migration-backup"
install -d -m 750 -o 10001 -g 10001 "$target/backups" "$target/logs" "$target/logs/access"
crontab -l > "$target/migration-backup/root.crontab" 2>/dev/null || true
docker compose --project-directory "$legacy" -f "$legacy/compose.yaml" config --format json > "$target/migration-backup/legacy-compose.json"
cp -p "$legacy/compose.yaml" "$legacy/.env" "$legacy/.env.app" "$target/migration-backup/"
old_db=$(docker compose --project-directory "$legacy" -f "$legacy/compose.yaml" ps -q sqlserver)
old_app=$(docker compose --project-directory "$legacy" -f "$legacy/compose.yaml" ps -q app)
[[ -n "$old_db" && -n "$old_app" ]] || { echo 'The existing app/database must be running before migration.' >&2; exit 1; }
old_pid=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["services"]["sqlserver"]["environment"]["MSSQL_PID"])' "$target/migration-backup/legacy-compose.json")
convert_express=0
if [[ "$old_pid" == Developer ]]; then
    eligible=$(docker exec "$old_db" bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -d VietFresh_Hub -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN (SELECT SUM(CONVERT(bigint,size))*8/1024 FROM sys.database_files WHERE type=0)<10240 AND NOT EXISTS(SELECT 1 FROM sys.dm_db_persisted_sku_features) THEN 1 ELSE 0 END"' | tr -d '[:space:]')
    if [[ "$eligible" != 1 ]]; then
        echo 'Express preflight did not pass. No runtime migration was performed.' >&2
        docker exec "$old_db" bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -d VietFresh_Hub -Q "SET NOCOUNT ON; SELECT SUM(CONVERT(bigint,size))*8/1024 AS allocated_data_mb FROM sys.database_files WHERE type=0; SELECT feature_name FROM sys.dm_db_persisted_sku_features;"'
        exit 1
    fi
    convert_express=1
fi
docker inspect --format '{{.Image}}' "$old_db" > "$target/migration-backup/sql-image-id"
docker inspect --format '{{.Image}}' "$old_app" > "$target/migration-backup/app-image-id"
if [[ -f "$source_dir/image.tar" ]]; then docker load -i "$source_dir/image.tar"; fi
docker image inspect "$image" >/dev/null
docker pull nginx:stable-alpine
docker exec "$old_db" mkdir -p /var/opt/mssql/backup
has_uploads=0
if docker exec "$old_app" test -d /app/uploads/product; then
    has_uploads=1
    if docker volume inspect vietfresh_product_uploads >/dev/null 2>&1; then
        echo 'An upload volume already exists; inspect it before migrating files.' >&2
        exit 1
    fi
fi
restore_legacy() {
    local result=$?
    trap - EXIT
    [[ "$result" == 0 ]] && return
    echo 'Migration failed. Restoring the existing application stack and original database volume.' >&2
    systemctl disable --now vietfresh-update.timer vietfresh-backup.timer vietfresh.service 2>/dev/null || true
    docker compose --project-directory "$target" -f "$target/compose.yaml" stop 2>/dev/null || true
    docker compose --project-directory "$legacy" -f "$legacy/compose.yaml" up -d --pull never
    crontab "$target/migration-backup/root.crontab"
    exit "$result"
}
trap restore_legacy EXIT
grep -vF '/usr/local/bin/vietfresh-deploy' "$target/migration-backup/root.crontab" > "$target/migration-backup/root.crontab.filtered" || [[ $? == 1 ]]
crontab "$target/migration-backup/root.crontab.filtered"
docker compose --project-directory "$legacy" -f "$legacy/compose.yaml" stop -t 60 app
if [[ "$has_uploads" == 1 ]]; then
    docker cp "$old_app:/app/uploads/product" "$target/migration-backup/product-uploads"
    docker volume create vietfresh_product_uploads >/dev/null
    docker run --rm --user 0 --entrypoint sh \
        -v "$target/migration-backup/product-uploads:/source:ro" \
        -v vietfresh_product_uploads:/destination "$image" \
        -c 'cp -a /source/. /destination/ && chown -R 10001:10001 /destination'
fi
backup_name="pre-devops-$(date -u +%Y%m%dT%H%M%SZ).bak"
printf "BACKUP DATABASE [VietFresh_Hub] TO DISK=N'/var/opt/mssql/backup/%s' WITH INIT,CHECKSUM;\nRESTORE VERIFYONLY FROM DISK=N'/var/opt/mssql/backup/%s' WITH CHECKSUM;\nSELECT CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition;\nGO\n" "$backup_name" "$backup_name" |
    docker exec -i "$old_db" bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -r1'
docker cp "$old_db:/var/opt/mssql/backup/$backup_name" "$target/backups/$backup_name"
chown 10001:10001 "$target/backups/$backup_name"
chmod 640 "$target/backups/$backup_name"
sha256sum "$target/backups/$backup_name" > "$target/backups/$backup_name.sha256"
echo 'Existing database backup has passed RESTORE VERIFYONLY.'
docker exec "$old_db" bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -d VietFresh_Hub -h -1 -W -s "|" -Q "SET NOCOUNT ON; SELECT name,type,file_id FROM sys.database_files ORDER BY file_id"' > "$target/migration-backup/database-files.txt"
export VF_TARGET="$target" VF_IMAGE="$image" VF_SOURCE="$source_dir" VF_CONVERT_EXPRESS="$convert_express" VF_BACKUP_NAME="$backup_name"
python3 <<'PY'
import json, os, pathlib, secrets
root=pathlib.Path(os.environ['VF_TARGET'])
old=json.loads((root/'migration-backup/legacy-compose.json').read_text())
db=old['services']['sqlserver']
def quote(value):
    return "'"+str(value).replace("'","\\'")+"'"
values={
 'APP_IMAGE':os.environ['VF_IMAGE'],
 'SQL_IMAGE':(root/'migration-backup/sql-image-id').read_text().strip(),
 'SQL_VOLUME':old['volumes']['sql_data']['name'],
 'MSSQL_SA_PASSWORD':db['environment']['MSSQL_SA_PASSWORD'],
 'MSSQL_PID':db['environment']['MSSQL_PID'],
 'APP_DB_USER':'vietfresh_app',
 'APP_DB_PASSWORD':secrets.token_urlsafe(36),
 'PUBLIC_URL':'http://192.168.250.130:8088',
 'HTTP_PORT':'8088', 'COOKIE_SECURE':'false', 'VNPAY_ENABLED':'false'
}
if os.environ['VF_CONVERT_EXPRESS']=='1':
    import datetime
    values['MSSQL_PID']='Express'
    values['SQL_VOLUME']='vietfresh_sql_express_'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
(root/'.env').write_text(''.join(k+'='+quote(v)+'\n' for k,v in values.items()))
(root/'current-image').write_text(values['APP_IMAGE']+'\n')
password=values['APP_DB_PASSWORD']
(root/'migration-backup/create-app-login.sql').write_text(
 "USE [master];\nIF SUSER_ID(N'vietfresh_app') IS NULL CREATE LOGIN [vietfresh_app] WITH PASSWORD=N'"+password+"';\n"
 "ELSE ALTER LOGIN [vietfresh_app] WITH PASSWORD=N'"+password+"';\nGO\nUSE [VietFresh_Hub];\n"
 "IF USER_ID(N'vietfresh_app') IS NULL CREATE USER [vietfresh_app] FOR LOGIN [vietfresh_app];\n"
 "ELSE ALTER USER [vietfresh_app] WITH LOGIN=[vietfresh_app];\n"
 "IF IS_ROLEMEMBER(N'db_datareader',N'vietfresh_app')<>1 ALTER ROLE [db_datareader] ADD MEMBER [vietfresh_app];\n"
 "IF IS_ROLEMEMBER(N'db_datawriter',N'vietfresh_app')<>1 ALTER ROLE [db_datawriter] ADD MEMBER [vietfresh_app];\n"
 "GRANT EXECUTE TO [vietfresh_app];\nGRANT VIEW DEFINITION TO [vietfresh_app];\nGO\n")
if os.environ['VF_CONVERT_EXPRESS']=='1':
    moves=[]
    for line in (root/'migration-backup/database-files.txt').read_text().splitlines():
        if not line.strip(): continue
        name,kind,file_id=line.strip().split('|')
        logical=name.replace("'","''")
        extension='ldf' if kind.strip()=='1' else 'mdf'
        moves.append("MOVE N'"+logical+"' TO N'/var/opt/mssql/data/VietFresh_Hub_"+file_id.strip()+'.'+extension+"'")
    if not moves: raise RuntimeError('Database file metadata is missing')
    (root/'migration-backup/restore-express.sql').write_text(
        "RESTORE DATABASE [VietFresh_Hub] FROM DISK=N'/var/opt/mssql/backup/"+os.environ['VF_BACKUP_NAME']+"' WITH "+', '.join(moves)+", CHECKSUM;\nGO\n")
PY
install -m 600 "$source_dir/.env.integrations" "$target/.env.app"
install -m 644 "$source_dir/compose.yaml" "$target/compose.yaml"
install -d -m 755 "$target/nginx"
install -m 644 "$source_dir/nginx/default.conf" "$target/nginx/default.conf"
install -m 755 "$source_dir/vietfresh-ops" /usr/local/sbin/vietfresh-ops
for unit in vietfresh.service vietfresh-update.service vietfresh-update.timer vietfresh-backup.service vietfresh-backup.timer; do
    install -m 644 "$source_dir/$unit" "/etc/systemd/system/$unit"
done
cd "$target"
docker compose config -q
if [[ "$convert_express" == 1 ]]; then
    new_volume=$(python3 -c 'import shlex; print(next(shlex.split(x.split("=",1)[1])[0] for x in open(".env") if x.startswith("SQL_VOLUME=")))')
    if docker volume inspect "$new_volume" >/dev/null 2>&1; then echo 'New Express volume already exists; refusing to overwrite.' >&2; exit 1; fi
    docker volume create "$new_volume" >/dev/null
fi
docker compose up -d --wait --wait-timeout 180 --pull never sqlserver
if [[ "$convert_express" == 1 ]]; then
    docker compose exec -T sqlserver bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -r1' < migration-backup/restore-express.sql
    echo 'Restored the database into a new SQL Server Express volume; original volume is retained.'
fi
docker compose exec -T sqlserver bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -r1' < migration-backup/create-app-login.sql
systemctl daemon-reload
systemctl enable docker.service vietfresh.service
systemctl start vietfresh.service
curl --fail --silent http://127.0.0.1:8088/healthz
systemctl enable --now vietfresh-backup.timer vietfresh-update.timer
printf 'sonhg84 ALL=(root) NOPASSWD: /usr/local/sbin/vietfresh-ops\n' > /etc/sudoers.d/vietfresh-ops
chmod 440 /etc/sudoers.d/vietfresh-ops
visudo -cf /etc/sudoers.d/vietfresh-ops
if docker inspect vietfresh-tunnel >/dev/null 2>&1; then docker stop vietfresh-tunnel; fi
touch "$target/.installed"
trap - EXIT
echo 'Installed successfully. Application URL: http://192.168.250.130:8088'
echo 'SQL Server edition and application readiness were checked during installation.'
