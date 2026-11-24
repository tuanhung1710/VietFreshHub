#!/usr/bin/env bash
set -Eeuo pipefail
image=${1:?image reference required}
suffix="${GITHUB_RUN_ID:-local}-$$"
network="vietfresh-test-$suffix"
db="vietfresh-db-$suffix"
express="vietfresh-express-$suffix"
proxy="vietfresh-proxy-$suffix"
app="vietfresh-app-$suffix"
password="Test_$(openssl rand -hex 16)!"
cleanup() {
    result=$?
    if (( result != 0 )); then docker logs --tail 100 "$app" 2>/dev/null || true; docker logs --tail 50 "$express" 2>/dev/null || true; docker logs --tail 30 "$db" 2>/dev/null || true; fi
    docker rm -f "$app" "$db" "$express" "$proxy" >/dev/null 2>&1 || true
    docker network rm "$network" >/dev/null 2>&1 || true
    exit "$result"
}
trap cleanup EXIT
docker network create "$network" >/dev/null
docker run -d --name "$db" --network "$network" --network-alias sqlserver --memory 3g \
    -e ACCEPT_EULA=Y -e MSSQL_PID=Developer -e MSSQL_SA_PASSWORD="$password" \
    -e MSSQL_MEMORY_LIMIT_MB=2048 mcr.microsoft.com/mssql/server:2022-latest >/dev/null
ready=false
for ((i=0; i<90; i++)); do
    if docker exec -e SQLCMDPASSWORD="$password" "$db" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -Q 'SELECT 1' >/dev/null 2>&1; then ready=true; break; fi
    sleep 2
done
$ready || { echo 'Test SQL Server did not become ready.' >&2; exit 1; }
sed '1s/^\xEF\xBB\xBF//' database/vietfresh_hub_script.sql |
    docker exec -i -e SQLCMDPASSWORD="$password" "$db" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -f 65001
docker exec "$db" mkdir -p /var/opt/mssql/backup
legacy_probe=$(docker exec -e SQLCMDPASSWORD="$password" "$db" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -h -1 -W -Q 'SET NOCOUNT ON; USE [VietFresh_Hub]; SELECT 1' | tr -d '[:space:]')
[[ "$legacy_probe" != 1 ]] && echo 'Confirmed: USE emits a context message into parsed sqlcmd output.'
eligible=$(docker exec -e SQLCMDPASSWORD="$password" "$db" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -d VietFresh_Hub -h -1 -W -Q 'SET NOCOUNT ON; SELECT CASE WHEN (SELECT SUM(CONVERT(bigint,size))*8/1024 FROM sys.database_files WHERE type=0)<10240 AND NOT EXISTS(SELECT 1 FROM sys.dm_db_persisted_sku_features) THEN 1 ELSE 0 END' | tr -d '[:space:]')
[[ "$eligible" == 1 ]] || { echo 'Express preflight output was not the exact expected scalar.' >&2; exit 1; }
metadata=$(docker exec -e SQLCMDPASSWORD="$password" "$db" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -d VietFresh_Hub -h -1 -W -s '|' -Q 'SET NOCOUNT ON; SELECT name,type,file_id FROM sys.database_files ORDER BY file_id')
while IFS='|' read -r logical kind file_id; do
    [[ -n "$logical" && "$kind" =~ ^[01]$ && "$file_id" =~ ^[0-9]+$ ]] || { echo 'Unexpected database file metadata output.' >&2; exit 1; }
done <<< "$metadata"
printf "USE [VietFresh_Hub]; CREATE TABLE dbo.devops_restore_probe(id int NOT NULL); INSERT dbo.devops_restore_probe VALUES(42); BACKUP DATABASE [VietFresh_Hub] TO DISK=N'/var/opt/mssql/backup/ci.bak' WITH INIT,CHECKSUM; RESTORE VERIFYONLY FROM DISK=N'/var/opt/mssql/backup/ci.bak' WITH CHECKSUM;\nGO\n" |
    docker exec -i -e SQLCMDPASSWORD="$password" "$db" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b
docker run -d --name "$express" --network "$network" --network-alias sqlserver-express --memory 3g \
    -e ACCEPT_EULA=Y -e MSSQL_PID=Express -e MSSQL_SA_PASSWORD="$password" \
    -e MSSQL_MEMORY_LIMIT_MB=2048 mcr.microsoft.com/mssql/server:2022-latest >/dev/null
ready=false
for ((i=0; i<90; i++)); do
    if docker exec -e SQLCMDPASSWORD="$password" "$express" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -Q 'SELECT 1' >/dev/null 2>&1; then ready=true; break; fi
    sleep 2
done
$ready || { echo 'Express SQL Server did not become ready.' >&2; exit 1; }
docker exec "$express" mkdir -p /var/opt/mssql/backup
docker cp "$db:/var/opt/mssql/backup/ci.bak" - | docker cp - "$express:/var/opt/mssql/backup/"
docker exec --user 0 "$express" chown 10001:0 /var/opt/mssql/backup/ci.bak
docker stop "$db" >/dev/null
printf "RESTORE DATABASE [VietFresh_Hub] FROM DISK=N'/var/opt/mssql/backup/ci.bak' WITH MOVE N'VietFresh_Hub' TO N'/var/opt/mssql/data/VietFresh_Hub_1.mdf', MOVE N'VietFresh_Hub_log' TO N'/var/opt/mssql/data/VietFresh_Hub_2.ldf',CHECKSUM;\nGO\nUSE [VietFresh_Hub]; IF NOT EXISTS(SELECT 1 FROM dbo.devops_restore_probe WHERE id=42) THROW 51000,'Restore lost test data',1; DROP TABLE dbo.devops_restore_probe; CREATE LOGIN [vietfresh_ci] WITH PASSWORD=N'%s'; CREATE USER [vietfresh_ci] FOR LOGIN [vietfresh_ci]; ALTER ROLE db_datareader ADD MEMBER [vietfresh_ci]; ALTER ROLE db_datawriter ADD MEMBER [vietfresh_ci]; GRANT EXECUTE TO [vietfresh_ci]; GRANT VIEW DEFINITION TO [vietfresh_ci];\nGO\n" "$password" |
    docker exec -i -e SQLCMDPASSWORD="$password" "$express" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b
docker run -d --name "$app" --network "$network" --network-alias app --memory 1536m \
    -e SPRING_DATASOURCE_URL='jdbc:sqlserver://sqlserver-express:1433;databaseName=VietFresh_Hub;encrypt=true;trustServerCertificate=true' \
    -e SPRING_DATASOURCE_USERNAME=vietfresh_ci -e SPRING_DATASOURCE_PASSWORD="$password" \
    -e CLOUDINARY_CLOUD_NAME=test -e CLOUDINARY_API_KEY=test -e CLOUDINARY_API_SECRET=test \
    -e SPRING_MAIL_USERNAME=test@example.invalid -e SPRING_MAIL_PASSWORD=test \
    -e GOOGLE_CLIENT_ID=test -e GOOGLE_CLIENT_SECRET=test -e VIETMAP_API_KEY=test \
    -e VNPAY_ENABLED=false -e COOKIE_SECURE=false \
    -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=65.0' "$image" >/dev/null
healthy=false
for ((i=0; i<90; i++)); do
    if docker exec "$app" curl -fsS http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1; then healthy=true; break; fi
    sleep 2
done
$healthy || { echo 'Application failed its real SQL Server readiness check.' >&2; exit 1; }
for route in /login /products; do
    code=$(docker exec "$app" curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:8080$route")
    [[ "$code" == 200 ]] || { echo "Unexpected $code for $route" >&2; exit 1; }
done
code=$(docker exec "$app" curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/admin)
[[ "$code" == 302 ]] || { echo "Anonymous admin access was not redirected: $code" >&2; exit 1; }
docker create --name "$proxy" --network "$network" --memory 96m nginx:stable-alpine >/dev/null
docker cp deploy/nginx/default.conf "$proxy:/etc/nginx/conf.d/default.conf"
docker start "$proxy" >/dev/null
docker exec "$proxy" nginx -t
proxy_ready=false
for ((i=0; i<15; i++)); do
    if docker exec "$app" curl -fsS "http://$proxy/healthz" >/dev/null 2>&1; then proxy_ready=true; break; fi
    sleep 1
done
$proxy_ready || { echo 'Nginx did not proxy application readiness.' >&2; exit 1; }
code=$(docker exec "$app" curl -s -o /dev/null -w '%{http_code}' "http://$proxy/actuator/health")
[[ "$code" == 404 ]] || { echo "Management endpoint exposed through proxy: $code" >&2; exit 1; }
echo 'PASS: SQL schema, verified backup/Express restore, least-privilege login, readiness, public pages, admin protection and Nginx management isolation.'
