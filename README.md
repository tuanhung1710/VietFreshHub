# VietFresh Hub 🍃

**Fresh commerce, seamlessly connected.**

VietFresh Hub is a multi-vendor marketplace for fresh food, bringing customers, sellers, delivery staff, and administrators together in a single web application. It goes beyond conventional e-commerce to address the realities of perishable goods: batch-level inventory, expiry-aware stock allocation, multi-shop fulfilment, and seller settlement.

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white) ![SQL Server](https://img.shields.io/badge/Database-SQL%20Server-CC2927?logo=microsoftsqlserver&logoColor=white)

## What It Delivers

- **For customers:** Explore products and shops, filter listings, compare variants, save favourite shops, receive related-product suggestions, and manage a multi-shop cart. Checkout supports delivery quotations, coupons, loyalty rewards, cash on delivery (COD), and VNPay payments, followed by order tracking, reordering, reviews, and refund-information submission when eligible.
- **For sellers:** Apply to open a shop, manage product listings and variants, and handle orders through preparation and handover. Batch-level inventory supports reservations, expiry alerts, and stock adjustments, alongside promotions, coupons, shop subscriptions, and sales reporting.
- **For delivery staff:** Claim available deliveries, confirm pickups, update delivery outcomes, review delivery history, and view COD summaries.
- **For administrators:** Oversee accounts, shops, seller applications, categories, product approvals, reported reviews, and refunds. Manage loyalty rules, subscription plans, and commission-based seller payouts, with scheduled settlement batches and Excel exports.
- **Across the platform:** Role-based access control, email verification and password recovery, Google sign-in, customer–seller chat over WebSocket/STOMP, and in-app notifications.

## Technology Stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Spring Security |
| Frontend | Thymeleaf, HTML, CSS, JavaScript, Bootstrap |
| Database | Microsoft SQL Server, Hibernate |
| Integrations | VNPay, Google OAuth 2.0 / OpenID Connect, Cloudinary, VietMap, SMTP |
| Other | WebSocket/STOMP, Apache POI, Maven Wrapper |

## Getting Started

### Prerequisites

- **JDK 21** and **Microsoft SQL Server** (running locally or reachable from your machine).
- Credentials for the external services you intend to use. The project includes a Maven Wrapper, so a separate Maven installation is unnecessary.

### 1. Initialise the database

Run these scripts **in order** using SQL Server Management Studio or another SQL Server-compatible SQL client:

```text
database/vietfresh_hub_script.sql  # Create/update the VietFresh_Hub schema
database/data.sql                  # Load configuration and demonstration data
```

The application validates the existing schema (`hibernate.hbm2ddl.auto=validate`); it does not create the tables on startup.

Demo inventory lots have an expiry date at least 15 days after their receipt date. For an existing
database, run `database/adjust_inventory_shelf_life.sql` once to apply the same minimum without
reloading data. It extends expiry dates for unexpired lots and backdates receipts for expired lots,
including their matching restock records, to preserve the expiry and disposal scenarios.

### 2. Configure the application

Review `src/main/resources/application.properties`. Set your local SQL Server connection and supply valid credentials for the integrations you plan to enable.

| Service | Relevant configuration |
| --- | --- |
| SQL Server | `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` |
| Cloudinary uploads | `cloudinary.cloud-name`, `cloudinary.api-key`, `cloudinary.api-secret` |
| Email and OTP | `spring.mail.*` |
| Google sign-in | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` |
| Address and routing services | `vietmap.api-key` |
| VNPay | `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_ENABLED` |

Spring Boot environment-variable overrides or private external configuration are preferable to committing credentials. **Do not publish real secrets.** VNPay endpoints are configured for the **sandbox** environment by default.

The current `application.properties` defines a fallback for `VNPAY_HASH_SECRET`, so a missing environment variable alone does not make the application fail at startup. Before deployment, provide the VNPay secret through a secure environment or private configuration and remove reliance on the source configuration fallback. Set `VNPAY_ENABLED=false` when running without VNPay.

### 3. Start the application

```bash
# macOS / Linux
bash ./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Open **http://localhost:8080**. The application uses port `8080` by default.

## Project Structure

```text
VietFreshHub/
├── database/                        # SQL Server schema and demo data
├── src/main/java/VietFreshHub/      # Domain-organised Spring Boot modules
│   ├── auth/                         # Identity and access workflows
│   ├── catalog/                      # Storefront and product discovery
│   ├── checkout/                     # Purchasing and fulfilment preparation
│   ├── inventory/                    # Batch-level stock management
│   ├── delivery/                     # Courier and delivery operations
│   ├── payment/                      # Payment and VNPay integration
│   ├── payout/                       # Seller settlements
│   └── ...                           # Additional marketplace modules
├── src/main/resources/
│   ├── templates/                   # Thymeleaf views for each user role
│   ├── static/                      # Stylesheets, scripts, and images
│   └── application.properties       # Runtime configuration
└── pom.xml                          # Dependencies and build configuration
```

---

*VietFresh Hub — connecting fresh products, independent sellers, and the people who deliver them.*

## Ubuntu deployment and CI/CD

The deployment sources are in `deploy/`. They target the existing Ubuntu VM and
preserve its database. Secrets belong in the root-owned `/opt/vietfresh/.env` and
`.env.app`, outside Git and Docker build context.

The workflow on `Hoang-Van-Son` builds Java 21, creates an isolated SQL Server,
installs the schema, verifies a backup restored into Express, and starts the app
using a database login limited to application read/write/execute permissions.
It checks readiness, public pages, and anonymous admin access. Only a successful
image is published to GHCR with its commit SHA and then the `son-latest` channel.
Pull-request verification has no package-publishing permission.

The Ubuntu update timer runs while the VM is on. It resolves the release channel
to an immutable digest, takes a checksummed SQL backup, and replaces only the app.
Failed readiness restores the previous application image. This does not roll back
database migrations: application startup validates the schema; schema upgrades
must be reviewed and run separately with their own backup/rollback procedure.

`bootstrap.sh` is a one-time installer that must be reviewed and run with sudo.
It requires the old `/home/sonhg84/vietfresh-deploy` stack, the tested image, and a
private `.env.integrations` file in its staging directory. It stops application
writes before its final backup. If the old edition is Developer, the database must
fit Express limits and have no persisted edition-only features. The installer then
restores into a newly created Express volume, retaining the original volume and
configuration for recovery. On initial migration failure, it restores the old stack.
It never loads `database/data.sql` into the existing database.

The runtime stack is Nginx -> Spring Boot -> SQL Server. Database and management
ports are not published. `/healthz` returns a readiness status without diagnostics;
Nginx blocks `/actuator`. The app runs without root, has a 1.5 GiB container limit,
and shuts down gracefully. SQL Server has a 3 GiB container limit and a 2 GiB engine
memory limit. Docker logs are rotated. Access logs have a 14-day history.

Operational commands on Ubuntu:

```bash
sudo /usr/local/sbin/vietfresh-ops status
sudo /usr/local/sbin/vietfresh-ops logs
sudo /usr/local/sbin/vietfresh-ops backup
sudo /usr/local/sbin/vietfresh-ops update
sudo /usr/local/sbin/vietfresh-ops rollback
```

Daily backups also run after a missed schedule when the PC starts again. Each backup
passes `RESTORE VERIFYONLY` and has a SHA-256 checksum. Rolling backups are retained
for 14 days and copied to the private `~/vietfresh-backups` directory for collection
by the Windows owner. Keep an additional copy on independent storage for protection
against physical PC/disk failure. The pre-migration backup is retained separately.

`deploy/windows` contains the source and installers for the Windows VM lifecycle
service. Build it with `Build-VmLifecycle.ps1`, then run `Install-VmLifecycle.ps1` in
an administrator PowerShell. It starts the existing VM on Windows boot and handles
Windows preshutdown by requesting a VMware Tools soft shutdown, with a five-minute
budget. Its fallback is a safe suspend; it never hard-powers-off the VM. Source and
configuration are installed under an administrator-protected ProgramData directory.
Test service stop/start and VM application recovery before relying on a host reboot.
The VM runs in the background before Windows sign-in; manage Ubuntu through SSH.
`Install-And-Test-VmLifecycle.ps1` performs the installation and a VM stop/start
test from an administrator PowerShell. It waits for `/healthz` before and after the
cycle, and saves its result under ProgramData. Windows itself is not restarted.

The initial local address is `http://192.168.250.130:8088`. For a public domain,
complete inbound routing and a valid TLS certificate before switching `PUBLIC_URL`
to HTTPS and enabling secure cookies. Configure Google OAuth's allowed callback
to `https://sonhg84.ap.vn/login/oauth2/code/google`. VNPay stays disabled until live
merchant configuration and its return/IPN URLs are verified. Changing Wi-Fi does
not preserve inbound routing by itself; arbitrary-network public access requires
an outbound tunnel or a fixed external relay.
