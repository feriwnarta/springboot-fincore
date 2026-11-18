# FinCore Platform

Maven multi-module Spring Boot baseline for the FinCore platform.

Panduan use case lengkap, role, kontrak API, request/response, dan contoh `curl` tersedia di
[docs/USE_CASES_AND_API_GUIDE.md](docs/USE_CASES_AND_API_GUIDE.md).

## Panduan operasional lokal

Dokumen ini adalah quickstart untuk menjalankan FinCore, memperoleh token Keycloak, menguji API melalui unified
Swagger/Kong, dan memahami alur bisnis Loan, Ledger, serta Search. Semua credential di bawah hanya untuk local
development dan tidak boleh dipakai di production.

### 1. Prasyarat

- macOS/Linux dengan Docker Desktop atau Docker Engine yang aktif.
- Java 21 dan Maven 3.6.3 atau lebih baru.
- `curl`, `jq`, dan `uuidgen` untuk smoke test/curl cookbook.
- Port default yang digunakan: Kong `8000`, Keycloak `8180`, Loan `8081`, Ledger `8082`, Search `8083`, Redis `6379`,
  dan Kafka `9092`.

Verifikasi instalasi:

```bash
java -version
mvn -version
docker --version
docker compose version
curl --version
jq --version
```

### 2. Clone dan konfigurasi environment

```bash
cd /Users/feriwinarta/Documents/ANOTHER/spring-boot-fincore
cp -n .env.example .env
```

`.env` berisi konfigurasi local-only untuk PostgreSQL, Keycloak, OIDC issuer, dan audience. Jangan commit `.env` atau
credential local ke repository.

Jika PostgreSQL host port `5432` sedang digunakan, jalankan Compose dengan port host alternatif. Container tetap
mengakses PostgreSQL melalui port internal `5432`:

```bash
POSTGRES_HOST_PORT=15432 docker compose up --build -d --wait --wait-timeout 180
```

Jika `5432` tersedia, command normalnya:

```bash
docker compose up --build -d --wait --wait-timeout 180
```

### 3. Nyalakan dan cek Docker Compose

```bash
docker compose ps
docker compose logs --tail=100 keycloak loan-service ledger-service search-service kong
```

Health check service:

```bash
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health
docker compose exec kong kong health
```

Untuk menghentikan container tanpa menghapus database volume:

```bash
docker compose down
```

Jangan memakai `docker compose down -v` kecuali memang ingin menghapus seluruh data local PostgreSQL, Kafka, Redis,
Keycloak, MongoDB, dan Elasticsearch.

### 4. Swagger, OpenAPI, dan Kong

Unified Swagger melalui gateway:

```text
http://localhost:8000/docs
```

Halaman tersebut menyediakan pilihan Loan, Ledger, dan Search. Klik **Authorize**, paste access token tanpa prefix
`Bearer`, lalu gunakan **Try it out**. Semua request dari unified Swagger memakai gateway `http://localhost:8000`.

Direct Swagger per service:

```text
http://localhost:8081/swagger-ui.html
http://localhost:8082/swagger-ui.html
http://localhost:8083/swagger-ui.html
```

OpenAPI JSON melalui gateway:

```text
http://localhost:8000/v3/api-docs
http://localhost:8000/docs/openapi/ledger
http://localhost:8000/docs/openapi/search
```

Kong adalah proxy API, bukan halaman dashboard. API bisnis menggunakan `http://localhost:8000`; request ke `/` atau
Swagger path yang tidak terdaftar akan menghasilkan `no Route matched with those values`.

### 5. Dapatkan access token Keycloak

Realm local adalah `fincore`, issuer-nya `http://localhost:8180/realms/fincore`, dan audience API adalah `fincore-api`.
Gunakan helper project. Token ditangkap ke environment variable agar tidak tercetak ke terminal:

```bash
set +x
export CUSTOMER_TOKEN="$(./scripts/keycloak-token.sh)"
export CUSTOMER2_TOKEN="$(KEYCLOAK_USERNAME=customer2 KEYCLOAK_PASSWORD=customer2 ./scripts/keycloak-token.sh)"
export LOAN_OPERATOR_TOKEN="$(KEYCLOAK_USERNAME=loan-operator KEYCLOAK_PASSWORD=loan-operator ./scripts/keycloak-token.sh)"
export LOAN_APPROVER_TOKEN="$(KEYCLOAK_USERNAME=loan-approver KEYCLOAK_PASSWORD=loan-approver ./scripts/keycloak-token.sh)"
export SAVINGS_OPERATOR_TOKEN="$(KEYCLOAK_USERNAME=savings-operator KEYCLOAK_PASSWORD=savings-operator ./scripts/keycloak-token.sh)"
export PRODUCT_ADMIN_TOKEN="$(KEYCLOAK_USERNAME=product-admin KEYCLOAK_PASSWORD=product-admin ./scripts/keycloak-token.sh)"
```

User demo:

| Username | Password | Role | `customer_id` |
|---|---|---|---|
| `customer1` | `customer1` | `CUSTOMER` | `CUST-001` |
| `customer2` | `customer2` | `CUSTOMER` | `CUST-002` |
| `loan-operator` | `loan-operator` | `LOAN_OPERATOR` | - |
| `loan-approver` | `loan-approver` | `LOAN_APPROVER` | - |
| `savings-operator` | `savings-operator` | `SAVINGS_OPERATOR` | - |
| `product-admin` | `product-admin` | `LOAN_PRODUCT_ADMIN` | - |

Untuk langsung menyalin token customer ke clipboard macOS tanpa menampilkannya:

```bash
./scripts/keycloak-token.sh | pbcopy
```

Token berlaku terbatas. Jika request mendapat `401`, ambil token baru dan pastikan Keycloak sudah healthy.

Login juga tersedia sebagai operasi public di Unified Swagger melalui `POST /api/v1/auth/login`. Masukkan body berikut,
jalankan **Execute**, lalu salin `data.accessToken` ke tombol **Authorize** tanpa menambahkan prefix `Bearer`:

```json
{
  "username": "product-admin",
  "password": "product-admin"
}
```

### 6. Kontrak response dan header standar

Response bisnis memakai envelope yang sama:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "meta": {
    "apiVersion": "v1",
    "correlationId": "uuid",
    "requestId": "uuid",
    "timestamp": "2026-11-18T00:00:00Z",
    "path": "/api/v1/..."
  }
}
```

Gunakan header berikut pada request penting:

| Header | Kapan digunakan |
|---|---|
| `Authorization: Bearer <token>` | Semua endpoint protected |
| `Idempotency-Key: <unique-key>` | Semua command finansial/write yang mensyaratkannya |
| `X-Correlation-Id: <uuid>` | Menyatukan trace request lintas service/Kafka |
| `X-Request-Id: <uuid>` | Identitas request individual; dibuat otomatis jika tidak dikirim |
| `Content-Type: application/json` | Request dengan body JSON |

Jangan mengirim token atau raw financial body ke log. SLF4J/Logback menyimpan correlation ID, request ID, actor, role,
operation, outcome, dan durasi secara terstruktur.

### 7. Flow bisnis utama

1. **Loan quote** — client membaca product aktif, kemudian meminta simulasi harga dan jadwal angsuran. Quote bersifat
   public dan tidak membuat transaksi.
2. **Loan application** — customer mengirim aplikasi untuk `customer_id` miliknya. Loan service menyimpan aplikasi,
   audit trail, dan outbox event dalam satu transaksi lokal. `LOAN_OPERATOR`/`LOAN_APPROVER` dapat submit lintas customer.
3. **Approval/rejection** — hanya `LOAN_APPROVER` yang boleh mengubah aplikasi `SUBMITTED` menjadi `APPROVED` atau
   `REJECTED`. Optimistic locking dan idempotency mencegah keputusan ganda/stale.
4. **Funding Saga** — Loan menyimpan `FUNDING_PENDING`, menjalankan simulator, lalu menyimpan loan `FUNDED` dan
   `LoanFunded` outbox. Kafka mengirim event ke Ledger. Ledger membuat balanced journal dan mengirim acknowledgement.
   Loan baru mengubah `ledgerPostingStatus` menjadi `POSTED` setelah acknowledgement durable diterima.
5. **Repayment Saga** — operator/approver mengirim repayment dan menerima `202 Accepted` dengan status
   `CONFIRMED_AWAITING_LEDGER`. Loan menerbitkan `RepaymentConfirmed`; Ledger membuat journal dan mengirim
   `RepaymentJournalPosted`; setelah itu repayment menjadi `POSTED` dan schedule diperbarui sekali.
6. **Savings/Ledger** — customer membuka rekening, deposit, withdrawal, dan membaca statement miliknya. Ledger memakai
   row lock `SELECT ... FOR UPDATE`, double-entry journal, audit, outbox, dan idempotency dalam transaksi lokal.
7. **Search projection** — Search service mengonsumsi event Kafka ke Elasticsearch untuk query loan dan MongoDB untuk
   activity. Projection bersifat eventually consistent; `404 PROJECTION_NOT_READY` berarti event belum selesai diproses.
8. **Security/ownership** — token valid tetapi role tidak cukup menghasilkan `403`. Customer yang mencoba data customer
   lain ditolak berdasarkan exact match JWT `customer_id`.

### 8. Curl cookbook

Semua contoh berikut memakai gateway:

```bash
export BASE_URL=http://localhost:8000
export CORRELATION_ID="$(uuidgen)"
```

#### 8.1 Baca product aktif dari Search Service

```bash
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/loan-products?limit=10" | jq
```

Read list ini dilayani Search Service melalui Elasticsearch. Setelah product dibuat, tunggu beberapa detik karena
projection Kafka bersifat eventually consistent. Simpan `data.items[0].code` sebagai `PRODUCT_CODE`.

#### 8.2 Loan quote public

```bash
export PRODUCT_CODE="<code-dari-data.items[0].code>"

curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loan-quotes" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d "{\"productCode\":\"$PRODUCT_CODE\",\"principalAmount\":1000000,\"tenorMonths\":12}" | jq
```

#### 8.3 List loan applications dari Search Service

Gunakan token customer untuk melihat aplikasi milik sendiri, atau token `LOAN_OPERATOR`/`LOAN_APPROVER` untuk melihat
aplikasi lintas customer. Filter `status`, `customerId`, dan `productCode` bersifat opsional; `cursor` berasal dari
response halaman sebelumnya.

```bash
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/loan-applications?limit=20&status=SUBMITTED" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

Response menggunakan `data.items` dan `data.page`. Customer selalu dibatasi ke `customer_id` pada JWT, walaupun
request mencoba mengirim `customerId` milik customer lain. Data berasal dari Elasticsearch dan dapat terlambat beberapa
detik dari PostgreSQL.

#### 8.4 Submit loan application sebagai customer

```bash
export APPLICATION_KEY="application-$(uuidgen)"

curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loan-applications" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: $APPLICATION_KEY" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"CUST-001\",\"productCode\":\"$PRODUCT_CODE\",\"principalAmount\":1000000,\"tenorMonths\":12}" | tee /tmp/fincore-application.json | jq

export APPLICATION_ID="$(jq -r '.data.applicationId' /tmp/fincore-application.json)"
```

#### 8.5 Approve atau reject sebagai loan approver

Approve:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loan-applications/$APPLICATION_ID/approve" \
  -H "Authorization: Bearer $LOAN_APPROVER_TOKEN" \
  -H "Idempotency-Key: approve-$(uuidgen)" \
  -H "X-Correlation-Id: $CORRELATION_ID" | jq
```

Reject menggunakan aplikasi baru yang masih `SUBMITTED`:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loan-applications/$APPLICATION_ID/reject" \
  -H "Authorization: Bearer $LOAN_APPROVER_TOKEN" \
  -H "Idempotency-Key: reject-$(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"reasonCode":"CREDIT_POLICY","reason":"Debt service ratio is too high"}' | jq
```

#### 8.6 Funding dan Saga ke Ledger

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loans/$APPLICATION_ID/fund" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" \
  -H "Idempotency-Key: fund-$(uuidgen)" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H 'Content-Type: application/json' \
  -d '{}' | jq
```

Untuk menguji jalur simulator gagal di local:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loans/$APPLICATION_ID/fund" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" \
  -H "Idempotency-Key: fund-failure-$(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"simulateFailure":true}' | jq
```

#### 8.7 Repayment

Gunakan `loanId` dari response funding:

```bash
export LOAN_ID="<data.loanId-dari-response-funding>"

curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loans/$LOAN_ID/repayments" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" \
  -H "Idempotency-Key: repayment-$(uuidgen)" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H 'Content-Type: application/json' \
  -d '{"amount":18000}' | jq
```

Response awal dapat berstatus `CONFIRMED_AWAITING_LEDGER`; tunggu consumer Kafka memproses acknowledgement hingga
status menjadi `POSTED`.

#### 8.8 Savings account, deposit, withdrawal, dan statement

```bash
export SAVINGS_KEY="savings-$(uuidgen)"
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/savings/accounts" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: $SAVINGS_KEY" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-001"}' | tee /tmp/fincore-savings.json | jq

export ACCOUNT_ID="$(jq -r '.data.accountId' /tmp/fincore-savings.json)"

# List account dari Search Service/Elasticsearch; customer hanya melihat account miliknya
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/savings/accounts?limit=20&status=ACTIVE" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq

curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/deposits" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: deposit-$(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"amount":500000}' | jq

curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq

curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/statement?limit=50" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq

curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/withdrawals" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: withdrawal-$(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"amount":100000}' | jq
```

Status rekening hanya dapat diubah staff:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/status" \
  -H "Authorization: Bearer $SAVINGS_OPERATOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"FROZEN"}' | jq
```

#### 8.9 Search dan report

Tiga endpoint list berikut semuanya lewat Kong ke Search Service:

- `GET /api/v1/loan-products` — public, product aktif.
- `GET /api/v1/loan-applications` — token customer/staff, ownership customer diterapkan dari JWT.
- `GET /api/v1/savings/accounts` — token customer/staff, ownership customer diterapkan dari JWT.

Write tetap menuju owner service: POST loan product/application ke Loan Service dan POST savings/deposit/withdrawal/status
ke Ledger Service. Search Service tidak melakukan fallback ke database transaksi.

```bash
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/search/loans?limit=20" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq

curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31&limit=1000" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq
```

#### 8.10 Uji `401`, `403`, dan ownership

```bash
# 401: protected endpoint tanpa token
curl -i "$BASE_URL/api/v1/search/loans?limit=10"

# 403: customer tidak boleh memakai endpoint staff
curl -i "$BASE_URL/api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

# 403: customer2 tidak boleh membaca account milik customer1
curl -i "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $CUSTOMER2_TOKEN"
```

### 9. Smoke test, contract test, dan troubleshooting

```bash
./scripts/smoke.sh
python3 scripts/test-keycloak-token.py
bash scripts/test-openapi-security.sh
bash scripts/test-unified-swagger.sh
```

Jika script token gagal, cek:

```bash
docker compose ps keycloak
docker compose logs --tail=100 keycloak
curl --fail http://localhost:8180/realms/fincore/.well-known/openid-configuration | jq '.issuer'
```

Interpretasi error umum:

| Status/error | Arti dan tindakan |
|---|---|
| `401 UNAUTHORIZED` | Token tidak ada, expired, issuer/audience salah, atau signature tidak valid. Ambil token baru. |
| `403 FORBIDDEN` | Token valid tetapi role atau ownership tidak cukup. |
| `409 CONFLICT` | Idempotency key pernah dipakai dengan payload berbeda, request masih diproses, atau state/version conflict. |
| `404 PROJECTION_NOT_READY` | Data activity belum selesai diproyeksikan oleh Kafka ke Search. |
| `no Route matched with those values` | Path belum terdaftar di Kong; gunakan `/docs` untuk unified Swagger atau route API `/api/v1/...`. |
| `502 Bad Gateway` setelah rebuild | Upstream IP berubah; jalankan `docker compose restart kong`. |

Log correlation lintas service:

```bash
docker compose logs -f --tail=100 loan-service ledger-service search-service kong
```

Test source dan test container:

```bash
mvn -q -am verify
mvn -q -am -DskipITs verify
```

`verify` penuh membutuhkan Docker/Testcontainers untuk integration test. Jangan menyimpulkan Saga/Kafka/ES/Mongo
terverifikasi hanya dari unit test; gunakan Compose smoke flow dan periksa correlation ID pada log serta response.

## Prerequisites

- Java 21
- Maven 3.6.3 or newer

The project currently pins Spring Boot `4.1.2` and Java release `21`.

## Modules

| Module | Responsibility | Local port |
|---|---|---:|
| `loan-service` | Loan applications, pricing, funding, and repayment | 8081 |
| `ledger-service` | Double-entry ledger and synthetic savings | 8082 |
| `search-service` | Search projections and stream analytics | 8083 |

## Build and test

```bash
mvn clean verify
```

The isolated PostgreSQL report integration test uses Docker/Testcontainers and can be run through the Maven integration-test profile with:

```bash
mvn -pl services/loan-service -am -Pintegration-tests verify
```

## Run a service

```bash
mvn -pl services/loan-service spring-boot:run
mvn -pl services/ledger-service spring-boot:run
mvn -pl services/search-service spring-boot:run
```

Health endpoints:

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

OpenAPI/Swagger UI is available per service at:

```text
http://localhost:8081/swagger-ui.html
http://localhost:8082/swagger-ui.html
http://localhost:8083/swagger-ui.html
```

Unified Swagger UI through Kong is available at:

```text
http://localhost:8000/docs
```

Use the service selector in that page to switch between Loan, Ledger, and Search OpenAPI contracts. Kong exposes the
underlying documents at `/v3/api-docs`, `/docs/openapi/ledger`, and `/docs/openapi/search` for the unified UI.

The Swagger UI documents the Keycloak Bearer JWT scheme. Open one of the URLs above, click **Authorize**, and paste
the access token without changing the `Bearer` scheme selected by Swagger UI. Protected operations show a lock icon;
loan product listing and quote operations are public, while product creation requires `LOAN_PRODUCT_ADMIN`.

Get a local demo token without printing it to the terminal:

```bash
export CUSTOMER_TOKEN="$(./scripts/keycloak-token.sh)"
export OPERATOR_TOKEN="$(KEYCLOAK_USERNAME=loan-operator KEYCLOAK_PASSWORD=loan-operator ./scripts/keycloak-token.sh)"
export PRODUCT_ADMIN_TOKEN="$(KEYCLOAK_USERNAME=product-admin KEYCLOAK_PASSWORD=product-admin ./scripts/keycloak-token.sh)"
```

The token helper is intended for curl or an API client. Swagger UI uses the same token value in its **Authorize**
dialog. The OpenAPI documents can be checked from the command line with:

```bash
bash scripts/test-openapi-security.sh
```

Kong exposes the business APIs at `http://localhost:8000`; it does not proxy the direct service Swagger UIs.

Business API responses use the shared envelope from `platform-support`:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "meta": {
    "apiVersion": "v1",
    "correlationId": "...",
    "requestId": "...",
    "timestamp": "2026-11-17T03:00:00Z",
    "path": "/api/v1/..."
  }
}
```

Every service response returns a correlation ID and a request ID. Send a UUID as `X-Correlation-Id`. An absent or invalid value is replaced with a generated UUID. `X-Request-Id` is generated when absent. Logs include both IDs through MDC. Authorization headers and raw financial request bodies are excluded from logs.

Application and library code use SLF4J (`org.slf4j`) as the logging facade. Logback is the Spring Boot runtime backend, configured per service through `logback-spring.xml`; business code must not depend directly on Logback classes.

The loan and ledger services use Spring Boot's Flyway starter with the PostgreSQL database adapter. Migrations live under `src/main/resources/db/migration` and are applied automatically at service startup:

- `loan-service`: `V1__audit_trail_foundation.sql`, `V2__loan_core_schema.sql`, `V3__loan_reporting_indexes.sql`, `V4__loan_outbox_relay_claims.sql`, `V5__repayment_state_model.sql`, and `V6__loan_paid_off_state.sql`.
- `ledger-service`: `V1__audit_trail_foundation.sql`, `V2__ledger_core_schema.sql`, then `V3__ledger_outbox_relay_claims.sql`.

The audit tables are append-only and carry actor, action, aggregate version, business reference, correlation/causation IDs, and sanitized metadata. Each service owns a separate PostgreSQL database. Local defaults are `fincore_loan` on port `5432` and `fincore_ledger` on port `5432`; override `LOAN_DB_URL`, `LOAN_DB_USERNAME`, `LOAN_DB_PASSWORD`, `LEDGER_DB_URL`, `LEDGER_DB_USERNAME`, and `LEDGER_DB_PASSWORD` per environment. Do not use `spring.sql.init` alongside Flyway for these services.

All services follow the package boundary documented in [ARCHITECTURE_SERVICE_STRUCTURE.md](docs/ARCHITECTURE_SERVICE_STRUCTURE.md). Clean code and implementation quality rules are documented in [CLEAN_CODE_AND_BEST_PRACTICES.md](docs/CLEAN_CODE_AND_BEST_PRACTICES.md). The current `loan-service` is the complete template: REST controllers and DTOs are under `api`, use cases under `application`, pure business types and ports under `domain`, and JDBC/messaging/search adapters under `infrastructure`. Ledger and search currently contain the same boundary markers while their business modules are implemented incrementally.

Loan reporting endpoints are bounded and parameterized by a simple inclusive `YYYY-MM-DD` date range. Internally,
the service converts `from` to the start of the day and `to` to the exclusive start of the following day:

```text
GET /api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31&limit=1000
GET /api/v1/reports/loans/top-three?from=2026-01-01&to=2026-12-31
GET /api/v1/reports/loans/running-total?from=2026-01-01&to=2026-12-31
```

The top-customer and top-repaid-loan reports use Java Stream over a maximum 1,000-row dataset. Top-3, running total, and posted repayment aggregates use native SQL window/CTE queries; SQL/index rationale is documented in [sql-performance.md](docs/sql-performance.md).

### Loan outbox relay and Kafka

The loan service uses a transactional outbox relay with PostgreSQL lease claims and at-least-once Kafka publishing. A relay claim is marked `IN_FLIGHT` before publishing; successful sends become `PUBLISHED`, while failures become `FAILED` with bounded retry metadata. Lease expiry allows another relay instance to reclaim a crashed attempt. Consumers must deduplicate by `eventId`.

Kafka relay configuration is disabled by default:

```text
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
FINCORE_LOAN_EVENTS_TOPIC=fincore.loan.events.v1
FINCORE_LEDGER_EVENTS_TOPIC=fincore.ledger.events.v1
FINCORE_OUTBOX_RELAY_ENABLED=false
FINCORE_OUTBOX_RELAY_FIXED_DELAY=1000ms
FINCORE_OUTBOX_RELAY_BATCH_SIZE=50
FINCORE_OUTBOX_RELAY_CLAIM_LEASE=30s
FINCORE_OUTBOX_RELAY_SEND_TIMEOUT=5s
FINCORE_LEDGER_OUTBOX_RELAY_ENABLED=false
LOAN_KAFKA_CONSUMER_GROUP=fincore-loan-service
LEDGER_KAFKA_CONSUMER_GROUP=fincore-ledger-service
FINCORE_LEDGER_CONSUMER_AUTO_STARTUP=false
FINCORE_KAFKA_CONSUMER_RETRY_INTERVAL=1000ms
FINCORE_KAFKA_CONSUMER_MAX_ATTEMPTS=3
FINCORE_KAFKA_DLQ_SUFFIX=.DLQ
```

Enable it only when Kafka is available. Both consumers retry poison records with a
bounded backoff and publish exhausted records to a diagnostic DLQ named by appending
`.DLQ` to the source topic. The original event is retained; replay must be an
explicit operational action by topic, partition, and offset so an operator can
review the record first. Inbox deduplication and unique business references make a
replayed event safe from duplicate financial effects.

The integration profile starts PostgreSQL and Kafka through Testcontainers:

```bash
mvn -pl services/loan-service -am -Pintegration-tests verify
```

The ledger service also exposes the synthetic savings contract. Account opening, deposits, withdrawals, status changes,
and keyset-paginated statements are all DTO contracts under the same response envelope:

```text
POST /api/v1/savings/accounts
POST /api/v1/savings/accounts/{accountId}/deposits
POST /api/v1/savings/accounts/{accountId}/withdrawals
POST /api/v1/savings/accounts/{accountId}/status
GET  /api/v1/savings/accounts/{accountId}
GET  /api/v1/savings/accounts/{accountId}/statement?limit=50&cursor=...
```

Each financial savings command requires `Idempotency-Key`. Deposit and withdrawal update the materialized balance,
immutable statement row, balanced double-entry journal, audit event, and ledger outbox in one PostgreSQL transaction.
The account row is locked with `SELECT ... FOR UPDATE`; a concurrent withdrawal therefore cannot produce a negative
balance. `FROZEN` accounts reject financial operations, while `CLOSED` accounts cannot be reopened.

### Search, Redis, and Kafka Streams

`search-service` consumes loan and repayment events into an Elasticsearch loan index and a bounded MongoDB activity
view. Elasticsearch documents use an explicit versioned index name (`fincore-loans-v1`) with keyword/text/numeric/date
mapping. Search combines exact `status` and `productCode` filters with the text `query`, which matches the description
or product code. The activity projection reads `RepaymentConfirmed` from the loan topic and `RepaymentJournalPosted` from
the ledger topic. Loan lifecycle projections compare versions and use lifecycle precedence to break ties, so funding
outranks approval and rejection at the same version. The activity history deduplicates retained event IDs, orders
lifecycle stages before repayments, and orders repayments by event time. For legacy payloads without an event timestamp,
the projection uses the Kafka record timestamp. Replaying the current lifecycle event can rebuild a missing search
document while preserving the existing activity history. Customer results require an exact projected customer ID.
Events that omit ownership leave it unset until an eligible event supplies it. Projections with a missing or blank
customer ID or the historical `unknown` placeholder are visible only to loan staff.

The service also runs a Kafka Streams topology with a persistent, one-hour event-ID deduplication state store and a
five-minute event-time tumbling window with one-minute grace. Aggregates are published to
`fincore.loan.analytics.5m.v1`.

Loan products use Redis cache-aside keys in the form `fincore:loan:product:{code}:v1`, TTL jitter, bounded Redis and
PostgreSQL fallback timeouts, and bounded single-flight on cache misses. Redis is shared, non-authoritative cache
state: PostgreSQL remains the source of truth. A separate `RedisAtomicCounter` demonstrates atomic increments only
for non-financial metrics.

### Compose and Kong

The complete local stack is pinned in `docker-compose.yml`. It includes one PostgreSQL container with three logical
databases, Keycloak, Kafka KRaft, Redis, Elasticsearch, MongoDB, three non-root Spring Boot images, and Kong DB-less `3.9.0`.
Elasticsearch and MongoDB are internal to the Compose network and publish no host ports. Read customer projections
through the authenticated search API. Compose services use `kafka:19092`. Host-run developers can use Kafka's plaintext
listener on loopback at `127.0.0.1:9092`. PostgreSQL and Keycloak also publish on loopback.

```bash
cp -n .env.example .env
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
./scripts/smoke.sh
```

Kong proxies business API routes on port `8000`. Its Admin API listens on `127.0.0.1:8001` inside the container and
has no host port. Use `docker compose exec kong kong health` to check it. The Spring services validate the bearer token
and enforce the same role and ownership rules through Kong and on their direct ports `8081` (loan), `8082` (ledger)
and `8083` (search). Use those direct ports for actuator health, readiness and OpenAPI. The local stack publishes the
Kong proxy, direct Spring ports and Redis for development. Restrict backend and Redis access in shared deployments.

| Operation | Access |
|---|---|
| Actuator health and readiness, OpenAPI, loan product listing and loan quotes | Public |
| Loan application submission | `CUSTOMER` for its own `customerId`, or `LOAN_OPERATOR` and `LOAN_APPROVER` across customers |
| Loan approval and rejection | `LOAN_APPROVER` |
| Loan funding, repayment and reports | `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Savings account opening, deposit and withdrawal | `CUSTOMER` for its own customer or account, plus `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Savings account and statement reads | `CUSTOMER` for its own account, or `SAVINGS_OPERATOR` across customers |
| Savings status changes, journal reads | `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Loan search and activity | `CUSTOMER` for its own projections, or `LOAN_OPERATOR` and `LOAN_APPROVER` across customers |

Customer access requires an exact match with the JWT's `customer_id` claim. A different customer's savings account
returns `403`. Customer search omits other customers' and unowned projections. Activity outside the caller's scope
returns `404`. A missing or invalid bearer token on a protected route returns `401`. A verified token with an
insufficient role returns `403`.

#### Local Keycloak

**Local development only.** The Compose startup above imports the demo realm and waits for Keycloak readiness before
starting the Spring services.

PostgreSQL is published on `127.0.0.1:5432` by default. If the port is occupied, set `POSTGRES_HOST_PORT`
in `.env` to a free port. The init script creates the `fincore_keycloak` database
and its dedicated non-superuser owner `fincore_keycloak` with the local password `fincore_keycloak_local`.
If you override `KEYCLOAK_DB_USERNAME` or `KEYCLOAK_DB_PASSWORD` in `.env`, provision the matching
login, password, and ownership in PostgreSQL first.

For an existing PostgreSQL volume initialized before the dedicated role was added, stop Keycloak and run
the idempotent init script. It provisions the Keycloak database if needed and transfers ownership of
existing Keycloak tables and sequences while preserving their data.

```bash
docker compose stop keycloak
docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U fincore -d postgres \
  < docker/postgres/init/01-create-databases.sql
docker compose up -d --wait --wait-timeout 180 keycloak
docker compose up -d --wait --wait-timeout 180
```

The [Keycloak admin console](http://localhost:8180/admin/) starts with `admin` / `change_me_locally` unless overridden
in `.env` before the first startup. Startup `--import-realm` imports the fixture only when `fincore` is absent.
Apply fixture changes in place through the admin console or `kcadm` to keep existing users and settings. For the
`sub` claim update, open **Clients → fincore-cli → Client scopes → Add client scope** and add `basic` as **Default**.
To replace a disposable demo realm, delete only `fincore` in the admin console and use **Create realm** with
`docker/keycloak/realm-fincore.json`. This resets the demo realm's data while preserving the shared PostgreSQL volume.

The canonical issuer (`OIDC_ISSUER_URI`) is `http://localhost:8180/realms/fincore` and the API audience
(`OIDC_AUDIENCE`) is `fincore-api`. Loan, ledger, and search validate both values. Compose fetches signing keys using
`OIDC_JWK_SET_URI=http://keycloak:8080/realms/fincore/protocol/openid-connect/certs` on the internal network.
A service running on the host defaults to the issuer's Keycloak certs endpoint. You can also set
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` and `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`
on the Spring service. Start Keycloak before sending bearer requests. Synthetic `X-Actor-Id` and `X-Actor-Role`
authentication is available through explicitly imported test fixtures. Packaged loan and ledger services accept only
`FINCORE_SECURITY_MODE=oauth2`. The services map roles from `resource_access.fincore-api.roles` and use `sub` as
the audit actor ID.

| Demo username | Demo password | Client role | `customer_id` |
|---|---|---|---|
| `customer1` | `customer1` | `CUSTOMER` | `CUST-001` |
| `customer2` | `customer2` | `CUSTOMER` | `CUST-002` |
| `loan-operator` | `loan-operator` | `LOAN_OPERATOR` | |
| `loan-approver` | `loan-approver` | `LOAN_APPROVER` | |
| `savings-operator` | `savings-operator` | `SAVINGS_OPERATOR` | |

The token helper requires `curl` and `jq`. It defaults to `customer1` on `fincore-cli`. Capture its token-only stdout
in a shell variable with tracing disabled.

```bash
set +x
operator_token=$(KEYCLOAK_USERNAME=loan-operator KEYCLOAK_PASSWORD=loan-operator ./scripts/keycloak-token.sh)
printf 'Authorization: Bearer %s\n' "$operator_token" |
  curl --disable --fail-with-body --silent --show-error --max-time 30 --header @- \
  'http://localhost:8000/api/v1/search/loans?limit=10' >/dev/null
unset operator_token
```

Override `KEYCLOAK_URL`, `KEYCLOAK_REALM`, and `KEYCLOAK_CLIENT_ID` for the token endpoint. The helper's username
and password variables are `KEYCLOAK_USERNAME` and `KEYCLOAK_PASSWORD`.

#### Authentication smoke

Run `./scripts/smoke.sh` with `curl`, `jq` and `uuidgen` installed. It defaults to Kong at
`BASE_URL=http://localhost:8000`, obtains three tokens through the helper, and verifies public access, missing-token
and wrong-role responses, operator search, and savings ownership. It checks HTTP statuses, the API envelope and
matching response IDs. The script prints status and trace identifiers for troubleshooting.

For an alternate demo realm, set the helper endpoint variables above and, as needed, the smoke-specific
`KEYCLOAK_CUSTOMER1_USERNAME`, `KEYCLOAK_CUSTOMER2_USERNAME` and `KEYCLOAK_OPERATOR_USERNAME` variables and their
respective `_PASSWORD` variables. Alternatively, supply `CUSTOMER1_ACCESS_TOKEN`, `CUSTOMER2_ACCESS_TOKEN` or
`OPERATOR_ACCESS_TOKEN` (`SEARCH_ACCESS_TOKEN` is an alias for the operator token). Customer tokens must map to
`CUST-001` and `CUST-002` respectively, and the operator must be able to search loans across customers.

Each fresh run opens one zero-balance demo account for `CUST-001` and leaves it available for audit inspection. To
replay a run without creating another account, set `SMOKE_IDEMPOTENCY_KEY` to the original value. The generated
default is `smoke-<correlation-id>` using the correlation ID printed by the original run.

For startup or token failures, use `docker compose ps` and `docker compose logs --tail=100 keycloak` to check
readiness and realm import. A stale realm can be repaired using the console steps above. After changing service
OIDC settings, run `docker compose up -d --wait --wait-timeout 180` to apply them before retrying the smoke script.
After rebuilding a service, a brief Kong `502` can occur while its upstream IP changes. If direct requests work
and the gateway error persists, run `docker compose restart kong` and `docker compose exec kong kong health`.

#### Production OIDC

Use a production HTTPS issuer and a JWKS endpoint reachable from each service. Replace `OIDC_ISSUER_URI` with the
exact token issuer, `OIDC_JWK_SET_URI` with the provider's JWKS URL, and `OIDC_AUDIENCE` with the API audience.
Configure the provider to emit the expected `sub`, `customer_id` and `resource_access.fincore-api.roles` claims.
For browser and mobile clients, use Authorization Code with PKCE and public clients; do not embed client secrets in
those applications. For service-to-service calls, use confidential clients with Client Credentials and rotate their
secrets through the deployment's secret store. Disable the local demo password grant in production. Provision
database, bootstrap and client secrets through the deployment's secret store. Keep projection stores, administrative
endpoints and backend ports on restricted networks.

#### API examples

Use `Authorization: Bearer <access-token>` on protected endpoints. Audit identity comes from the token subject and mapped client role.

The first Loan API is available at `GET /api/v1/loan-products`. It returns only active products, uses cursor pagination (`limit` 1–100), and serializes money as decimal strings. Example:

```text
GET http://localhost:8081/api/v1/loan-products?limit=50
```

Authentication is exposed as a thin local-development facade over Keycloak. It returns the standard response envelope and
never logs passwords or tokens:

```http
POST http://localhost:8000/api/v1/auth/login
Content-Type: application/json

{"username":"product-admin","password":"product-admin"}
```

Loan products can be created from Unified Swagger with the `LOAN_PRODUCT_ADMIN` token, or through the gateway:

```http
POST http://localhost:8000/api/v1/loan-products
Authorization: Bearer <product-admin-access-token>
Idempotency-Key: product-demo-001
Content-Type: application/json

{"code":"KTA_ANNUITY","interestType":"ANNUITY","annualRate":0.12,
 "minAmount":1000000,"maxAmount":50000000,"active":true}
```

The endpoint returns `201 Created`, writes the product and audit row in the same local transaction, and evicts the Redis
product cache. Duplicate product codes return `409 CONFLICT`; reusing the same idempotency key with a different payload
also returns `409`.

To demonstrate a second loan instance sharing PostgreSQL, Kafka, and Redis, build the base stack first and start the
scale profile:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale up -d loan-service-2
curl http://localhost:8084/actuator/health
```

After the funding journal acknowledgement, `loan-service` generates the flat or
annuity schedule and exposes the simulated repayment command to `LOAN_OPERATOR` and `LOAN_APPROVER`.

```text
POST /api/v1/loans/{loanId}/repayments
Idempotency-Key: repayment-demo-001
Content-Type: application/json

{"amount": "18.0000"}
```

The response is `202 Accepted` with `CONFIRMED_AWAITING_LEDGER`. Allocation is
interest-first and then principal; the `RepaymentConfirmed` outbox event is posted
to ledger, which emits `RepaymentJournalPosted`. Only that durable acknowledgement
marks the repayment `POSTED` and applies the allocation to the schedules. Retrying a successful repayment with the same idempotency key and amount returns the original repayment, including after payoff.

Loan quotes are public at `POST /api/v1/loan-quotes`. The endpoint supports `FLAT` and `ANNUITY` products, validates product amount ranges, returns an installment schedule, and uses `404 RESOURCE_NOT_FOUND` when the active product does not exist.

Loan application submission at `POST /api/v1/loan-applications` requires a bearer token and an `Idempotency-Key` header. The `X-Correlation-Id` and `X-Request-Id` headers are optional. A `CUSTOMER` must provide a `customerId` that exactly matches the token's `customer_id` claim. A mismatch returns `403 FORBIDDEN`. `LOAN_OPERATOR` and `LOAN_APPROVER` can submit for any customer.

```http
POST http://localhost:8081/api/v1/loan-applications
Idempotency-Key: app-001
Content-Type: application/json

{"customerId":"CUST-001","productCode":"PERSONAL_FLAT","principalAmount":"1000000.0000","tenorMonths":12}
```

Submission creates the `SUBMITTED` application, append-only audit event, and `LoanSubmitted` outbox event in the same local transaction. Repeating the same key and payload replays the original application (`201`), including after product deactivation. Reusing the key with a different payload returns `409 CONFLICT`.

Only `LOAN_APPROVER` can approve or reject an application.

```http
POST http://localhost:8081/api/v1/loan-applications/{applicationId}/approve
Idempotency-Key: approve-001

POST http://localhost:8081/api/v1/loan-applications/{applicationId}/reject
Idempotency-Key: reject-001
Content-Type: application/json

{"reasonCode":"CREDIT_POLICY","reason":"Debt service ratio is too high"}
```

Only `SUBMITTED` applications can transition. Approval produces `APPROVED` and `LoanApproved`; rejection produces `REJECTED` and `LoanRejected`. Both transitions increment the application version, use optimistic locking, write audit and outbox records in the same transaction, and return `409 CONFLICT` for stale or terminal-state commands.

Funding is a separate idempotent command available to `LOAN_OPERATOR` and `LOAN_APPROVER`.

```http
POST http://localhost:8081/api/v1/loans/{applicationId}/fund
Idempotency-Key: fund-001
Content-Type: application/json

{}
```

The optional sandbox failure switch is `{"simulateFailure":true}`. A normal
request persists `FUNDING_PENDING`, runs the deterministic simulator, then
commits `FUNDED` and a versioned `LoanFunded` outbox event in the same loan
transaction. The ledger consumer deduplicates the event, writes a balanced
`LOAN_RECEIVABLE` / `SIMULATED_SETTLEMENT_CLEARING` journal, and emits a
`FundingJournalPosted` acknowledgement through the ledger outbox. The loan
consumer applies that acknowledgement once and changes only
`ledgerPostingStatus` to `POSTED`; funding success alone never claims that the
ledger is posted.
