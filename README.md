# Platform FinCore

Kerangka dasar platform FinCore berbasis Spring Boot multi-modul Maven.

Panduan use case lengkap, peran, kontrak API, permintaan/respons, dan contoh `curl` tersedia di
[docs/USE_CASES_AND_API_GUIDE.md](docs/USE_CASES_AND_API_GUIDE.md).

## Panduan operasional lokal

Dokumen ini adalah panduan cepat untuk menjalankan FinCore, memperoleh token Keycloak, menguji API melalui Swagger
terpadu/Kong, dan memahami alur bisnis Loan, Ledger, serta Search. Semua kredensial di bawah hanya untuk pengembangan
lokal dan tidak boleh dipakai di produksi.

### 1. Prasyarat

- macOS/Linux dengan Docker Desktop atau Docker Engine yang aktif.
- Java 21 dan Maven 3.6.3 atau lebih baru.
- `curl`, `jq`, dan `uuidgen` untuk smoke test/kumpulan contoh curl.
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

### 2. Clone dan konfigurasi lingkungan

```bash
cd /Users/feriwinarta/Documents/ANOTHER/spring-boot-fincore
cp -n .env.example .env
```

`.env` berisi konfigurasi khusus pengembangan lokal untuk PostgreSQL, Keycloak, penerbit OIDC, dan audience. Jangan
commit `.env` atau kredensial lokal ke repository.

Jika PostgreSQL host port `5432` sedang digunakan, jalankan Compose dengan port host alternatif. Container tetap
mengakses PostgreSQL melalui port internal `5432`:

```bash
POSTGRES_HOST_PORT=15432 docker compose up --build -d --wait --wait-timeout 180
```

Jika `5432` tersedia, command normalnya:

```bash
docker compose up --build -d --wait --wait-timeout 180
```

### 3. Menyalakan dan memeriksa Docker Compose

```bash
docker compose ps
docker compose logs --tail=100 keycloak loan-service ledger-service search-service kong
```

Pemeriksaan kesehatan service:

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

Jangan memakai `docker compose down -v` kecuali memang ingin menghapus seluruh data lokal PostgreSQL, Kafka, Redis,
Keycloak, MongoDB, dan Elasticsearch.

### 4. Swagger, OpenAPI, dan Kong

Unified Swagger melalui gateway:

```text
http://localhost:8000/docs
```

Halaman tersebut menyediakan pilihan Loan, Ledger, dan Search. Klik **Authorize**, tempel token akses tanpa awalan
`Bearer`, lalu gunakan **Try it out**. Semua permintaan dari Swagger terpadu memakai gateway `http://localhost:8000`.

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

Kong adalah proxy API, bukan halaman dashboard. API bisnis menggunakan `http://localhost:8000`; permintaan ke `/` atau
Swagger path yang tidak terdaftar akan menghasilkan `no Route matched with those values`.

### 5. Mendapatkan token akses Keycloak

Realm lokal adalah `fincore`, issuer-nya `http://localhost:8180/realms/fincore`, dan audience API adalah `fincore-api`.
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

Token berlaku terbatas. Jika permintaan mendapat `401`, ambil token baru dan pastikan Keycloak sudah siap.

Login juga tersedia sebagai operasi publik di Unified Swagger melalui `POST /api/v1/auth/login`. Masukkan body berikut,
jalankan **Execute**, lalu salin `data.accessToken` ke tombol **Authorize** tanpa menambahkan awalan `Bearer`:

```json
{
  "username": "product-admin",
  "password": "product-admin"
}
```

### 6. Kontrak respons dan header standar

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

Gunakan header berikut pada permintaan penting:

| Header | Kapan digunakan |
|---|---|
| `Authorization: Bearer <token>` | Semua endpoint yang dilindungi |
| `Idempotency-Key: <unique-key>` | Semua command finansial/perubahan data yang mensyaratkannya |
| `X-Correlation-Id: <uuid>` | Menyatukan jejak permintaan lintas service/Kafka |
| `X-Request-Id: <uuid>` | Identitas permintaan individual; dibuat otomatis jika tidak dikirim |
| `Content-Type: application/json` | Permintaan dengan body JSON |

Jangan mengirim token atau body finansial mentah ke log. SLF4J/Logback menyimpan correlation ID, request ID, actor,
peran, operasi, hasil, dan durasi secara terstruktur.

### 7. Alur bisnis utama

1. **Loan quote** — klien membaca produk aktif, kemudian meminta simulasi harga dan jadwal angsuran. Quote bersifat
   publik dan tidak membuat transaksi.
2. **Loan application** — customer mengirim aplikasi untuk `customer_id` miliknya. Loan service menyimpan aplikasi,
   audit trail, dan event outbox dalam satu transaksi lokal. `LOAN_OPERATOR`/`LOAN_APPROVER` dapat submit lintas customer.
3. **Persetujuan/penolakan** — hanya `LOAN_APPROVER` yang boleh mengubah aplikasi `SUBMITTED` menjadi `APPROVED` atau
   `REJECTED`. Optimistic locking dan idempotency mencegah keputusan ganda atau kedaluwarsa.
4. **Funding Saga** — Loan menyimpan `FUNDING_PENDING`, menjalankan simulator, lalu menyimpan loan `FUNDED` dan
   `LoanFunded` outbox. Kafka mengirim event ke Ledger. Ledger membuat jurnal seimbang dan mengirim acknowledgement.
   Loan baru mengubah `ledgerPostingStatus` menjadi `POSTED` setelah acknowledgement tersimpan secara permanen.
5. **Repayment Saga** — operator/approver mengirim repayment dan menerima `202 Accepted` dengan status
   `CONFIRMED_AWAITING_LEDGER`. Loan menerbitkan `RepaymentConfirmed`; Ledger membuat jurnal dan mengirim
   `RepaymentJournalPosted`; setelah itu repayment menjadi `POSTED` dan jadwal diperbarui satu kali.
6. **Savings/Ledger** — customer membuka rekening, deposit, withdrawal, dan membaca statement miliknya. Ledger memakai
   row lock `SELECT ... FOR UPDATE`, double-entry journal, audit, outbox, dan idempotency dalam transaksi lokal.
7. **Search projection** — Search service mengonsumsi event Kafka ke Elasticsearch untuk query loan dan MongoDB untuk
   activity. Projection menggunakan konsistensi eventual; `404 PROJECTION_NOT_READY` berarti event belum selesai diproses.
8. **Keamanan/kepemilikan** — token valid tetapi peran tidak cukup menghasilkan `403`. Customer yang mencoba data customer
   lain ditolak berdasarkan kecocokan tepat `customer_id` pada JWT.

### 8. Kumpulan contoh curl

Semua contoh berikut memakai gateway:

```bash
export BASE_URL=http://localhost:8000
export CORRELATION_ID="$(uuidgen)"
```

#### 8.1 Membaca produk aktif dari Search Service

```bash
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/loan-products?limit=10" | jq
```

Pembacaan daftar ini dilayani Search Service melalui Elasticsearch. Setelah produk dibuat, tunggu beberapa detik karena
projection Kafka menggunakan konsistensi eventual. Simpan `data.items[0].code` sebagai `PRODUCT_CODE`.

#### 8.2 Membuat loan quote publik

```bash
export PRODUCT_CODE="<code-dari-data.items[0].code>"

curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loan-quotes" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d "{\"productCode\":\"$PRODUCT_CODE\",\"principalAmount\":1000000,\"tenorMonths\":12}" | jq
```

#### 8.3 Membaca daftar loan applications dari Search Service

Gunakan token customer untuk melihat aplikasi milik sendiri, atau token `LOAN_OPERATOR`/`LOAN_APPROVER` untuk melihat
aplikasi lintas customer. Filter `status`, `customerId`, dan `productCode` bersifat opsional; `cursor` berasal dari
respons halaman sebelumnya.

```bash
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/loan-applications?limit=20&status=SUBMITTED" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

Respons menggunakan `data.items` dan `data.page`. Customer selalu dibatasi ke `customer_id` pada JWT, walaupun
permintaan mencoba mengirim `customerId` milik customer lain. Data berasal dari Elasticsearch dan dapat terlambat beberapa
detik dibandingkan PostgreSQL.

#### 8.4 Mengirim loan application sebagai customer

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

#### 8.5 Menyetujui atau menolak sebagai loan approver

Setujui:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loan-applications/$APPLICATION_ID/approve" \
  -H "Authorization: Bearer $LOAN_APPROVER_TOKEN" \
  -H "Idempotency-Key: approve-$(uuidgen)" \
  -H "X-Correlation-Id: $CORRELATION_ID" | jq
```

Tolak menggunakan aplikasi baru yang masih `SUBMITTED`:

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

Untuk menguji jalur simulator gagal di lokal:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/loans/$APPLICATION_ID/fund" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" \
  -H "Idempotency-Key: fund-failure-$(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"simulateFailure":true}' | jq
```

#### 8.7 Repayment

Gunakan `loanId` dari respons funding:

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

Respons awal dapat berstatus `CONFIRMED_AWAITING_LEDGER`; tunggu consumer Kafka memproses acknowledgement hingga
status berubah menjadi `POSTED`.

#### 8.8 Rekening savings, deposit, withdrawal, dan statement

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

# Daftar rekening dari Search Service/Elasticsearch; customer hanya melihat rekening miliknya
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

Status rekening hanya dapat diubah oleh staf:

```bash
curl --fail-with-body --silent --show-error \
  -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/status" \
  -H "Authorization: Bearer $SAVINGS_OPERATOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"FROZEN"}' | jq
```

#### 8.9 Search dan laporan

Tiga endpoint daftar berikut semuanya lewat Kong ke Search Service:

- `GET /api/v1/loan-products` — publik, produk aktif.
- `GET /api/v1/loan-applications` — token customer/staf, kepemilikan customer diterapkan dari JWT.
- `GET /api/v1/savings/accounts` — token customer/staf, kepemilikan customer diterapkan dari JWT.

Perintah tulis tetap menuju service pemilik data: POST produk/aplikasi loan ke Loan Service dan POST
savings/deposit/withdrawal/status ke Ledger Service. Search Service tidak melakukan fallback ke database transaksi.

```bash
curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/search/loans?limit=20" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq

curl --fail-with-body --silent --show-error \
  "$BASE_URL/api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31&limit=1000" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq
```

#### 8.10 Menguji `401`, `403`, dan kepemilikan data

```bash
# 401: endpoint yang dilindungi tanpa token
curl -i "$BASE_URL/api/v1/search/loans?limit=10"

# 403: customer tidak boleh memakai endpoint staf
curl -i "$BASE_URL/api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

# 403: customer2 tidak boleh membaca account milik customer1
curl -i "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $CUSTOMER2_TOKEN"
```

### 9. Smoke test, contract test, dan pemecahan masalah

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
| `401 UNAUTHORIZED` | Token tidak ada, kedaluwarsa, issuer/audience salah, atau signature tidak valid. Ambil token baru. |
| `403 FORBIDDEN` | Token valid tetapi peran atau kepemilikan data tidak cukup. |
| `409 CONFLICT` | Idempotency key pernah dipakai dengan payload berbeda, permintaan masih diproses, atau terjadi konflik state/version. |
| `404 PROJECTION_NOT_READY` | Data activity belum selesai diproyeksikan oleh Kafka ke Search. |
| `no Route matched with those values` | Path belum terdaftar di Kong; gunakan `/docs` untuk Swagger terpadu atau route API `/api/v1/...`. |
| `502 Bad Gateway` setelah rebuild | IP upstream berubah; jalankan `docker compose restart kong`. |

Log correlation lintas service:

```bash
docker compose logs -f --tail=100 loan-service ledger-service search-service kong
```

Pengujian sumber dan container pengujian:

```bash
mvn -q -am verify
mvn -q -am -DskipITs verify
```

`verify` penuh membutuhkan Docker/Testcontainers untuk pengujian integrasi. Jangan menyimpulkan Saga/Kafka/ES/Mongo
terverifikasi hanya dari unit test; gunakan alur smoke Compose dan periksa correlation ID pada log serta respons.

## Prasyarat teknis

- Java 21
- Maven 3.6.3 atau lebih baru

Project saat ini menggunakan Spring Boot `4.1.2` dan Java release `21`.

## Modul

| Modul | Tanggung jawab | Port lokal |
|---|---|---:|
| `loan-service` | Pengajuan loan, pricing, funding, dan repayment | 8081 |
| `ledger-service` | Ledger double-entry dan savings sintetis | 8082 |
| `search-service` | Projection pencarian dan analitik stream | 8083 |

## Build dan pengujian

```bash
mvn clean verify
```

Pengujian integrasi laporan PostgreSQL yang terisolasi menggunakan Docker/Testcontainers dan dapat dijalankan melalui
profile integration-test Maven:

```bash
mvn -pl services/loan-service -am -Pintegration-tests verify
```

## Menjalankan service

```bash
mvn -pl services/loan-service spring-boot:run
mvn -pl services/ledger-service spring-boot:run
mvn -pl services/search-service spring-boot:run
```

Endpoint kesehatan:

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

OpenAPI/Swagger UI tersedia untuk setiap service di:

```text
http://localhost:8081/swagger-ui.html
http://localhost:8082/swagger-ui.html
http://localhost:8083/swagger-ui.html
```

Unified Swagger UI melalui Kong tersedia di:

```text
http://localhost:8000/docs
```

Gunakan pemilih service pada halaman tersebut untuk berpindah antara kontrak OpenAPI Loan, Ledger, dan Search. Kong
menyediakan dokumen sumber pada `/v3/api-docs`, `/docs/openapi/ledger`, dan `/docs/openapi/search` untuk UI terpadu.

Swagger UI mendokumentasikan skema JWT Bearer Keycloak. Buka salah satu URL di atas, klik **Authorize**, lalu tempel
token akses tanpa mengubah skema `Bearer` yang dipilih Swagger UI. Operasi yang dilindungi menampilkan ikon kunci; daftar
produk loan dan operasi quote bersifat publik, sedangkan pembuatan produk membutuhkan `LOAN_PRODUCT_ADMIN`.

Dapatkan token demo lokal tanpa mencetaknya ke terminal:

```bash
export CUSTOMER_TOKEN="$(./scripts/keycloak-token.sh)"
export OPERATOR_TOKEN="$(KEYCLOAK_USERNAME=loan-operator KEYCLOAK_PASSWORD=loan-operator ./scripts/keycloak-token.sh)"
export PRODUCT_ADMIN_TOKEN="$(KEYCLOAK_USERNAME=product-admin KEYCLOAK_PASSWORD=product-admin ./scripts/keycloak-token.sh)"
```

Helper token ditujukan untuk curl atau klien API. Swagger UI menggunakan nilai token yang sama pada dialog
**Authorize**. Dokumen OpenAPI dapat diperiksa dari command line dengan:

```bash
bash scripts/test-openapi-security.sh
```

Kong menyediakan API bisnis pada `http://localhost:8000`; Kong tidak mem-proxy Swagger UI service secara langsung.

Response API bisnis menggunakan envelope bersama dari `platform-support`:

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

Setiap respons service mengembalikan correlation ID dan ID permintaan. Kirim UUID sebagai `X-Correlation-Id`. Nilai yang
tidak ada atau tidak valid akan diganti UUID baru. `X-Request-Id` dibuat otomatis jika tidak dikirim. Log menyertakan
kedua ID melalui MDC. Header Authorization dan body permintaan finansial mentah tidak ditulis ke log.

Kode aplikasi dan library menggunakan SLF4J (`org.slf4j`) sebagai facade logging. Logback adalah backend runtime Spring
Boot yang dikonfigurasi per service melalui `logback-spring.xml`; kode bisnis tidak boleh bergantung langsung pada class
Logback.

Loan service dan Ledger service menggunakan Flyway starter Spring Boot dengan adapter database PostgreSQL. Migration
berada di `src/main/resources/db/migration` dan diterapkan otomatis saat service startup:

- `loan-service`: `V1__audit_trail_foundation.sql`, `V2__loan_core_schema.sql`, `V3__loan_reporting_indexes.sql`, `V4__loan_outbox_relay_claims.sql`, `V5__repayment_state_model.sql`, dan `V6__loan_paid_off_state.sql`.
- `ledger-service`: `V1__audit_trail_foundation.sql`, `V2__ledger_core_schema.sql`, lalu `V3__ledger_outbox_relay_claims.sql`.

Tabel audit hanya dapat ditambahkan dan menyimpan actor, action, aggregate version, business reference, correlation/causation
ID, serta metadata yang sudah disanitasi. Setiap service memiliki database PostgreSQL terpisah. Default lokal adalah
`fincore_loan` pada port `5432` dan `fincore_ledger` pada port `5432`; override `LOAN_DB_URL`, `LOAN_DB_USERNAME`,
`LOAN_DB_PASSWORD`, `LEDGER_DB_URL`, `LEDGER_DB_USERNAME`, dan `LEDGER_DB_PASSWORD` per environment. Jangan gunakan
`spring.sql.init` bersamaan dengan Flyway untuk service ini.

Semua service mengikuti batas package yang didokumentasikan di [ARCHITECTURE_SERVICE_STRUCTURE.md](docs/ARCHITECTURE_SERVICE_STRUCTURE.md).
Aturan clean code dan kualitas implementasi didokumentasikan di [CLEAN_CODE_AND_BEST_PRACTICES.md](docs/CLEAN_CODE_AND_BEST_PRACTICES.md).
`loan-service` saat ini menjadi template lengkap: REST controller dan DTO berada di `api`, use case di `application`,
business type murni dan port di `domain`, serta adapter JDBC/messaging/search di `infrastructure`. Ledger dan Search
memiliki batas package yang sama, sementara modul bisnisnya diimplementasikan bertahap.

Endpoint laporan Loan memiliki batas dataset dan menggunakan rentang tanggal inklusif sederhana `YYYY-MM-DD`. Secara
internal, service mengubah `from` menjadi awal hari dan `to` menjadi awal hari berikutnya yang bersifat eksklusif:

```text
GET /api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31&limit=1000
GET /api/v1/reports/loans/top-three?from=2026-01-01&to=2026-12-31
GET /api/v1/reports/loans/running-total?from=2026-01-01&to=2026-12-31
```

Laporan top-customer dan top-repaid-loan menggunakan Java Stream pada dataset maksimal 1.000 baris. Laporan top-3,
running total, dan aggregate repayment yang sudah posted menggunakan query native SQL window/CTE; alasan SQL/index
didokumentasikan di [sql-performance.md](docs/sql-performance.md).

### Relay outbox Loan dan Kafka

Loan service menggunakan relay outbox transaksional dengan lease claim PostgreSQL dan publikasi Kafka at-least-once.
Claim relay ditandai `IN_FLIGHT` sebelum publikasi; pengiriman berhasil menjadi `PUBLISHED`, sedangkan kegagalan menjadi
`FAILED` dengan metadata retry yang dibatasi. Lease yang kedaluwarsa memungkinkan instance relay lain mengambil alih
percobaan yang gagal. Consumer wajib melakukan deduplikasi berdasarkan `eventId`.

Konfigurasi relay Kafka secara default dinonaktifkan:

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

Aktifkan hanya ketika Kafka tersedia. Kedua consumer melakukan retry terhadap record bermasalah dengan backoff
terbatas dan mengirim record yang sudah menghabiskan retry ke DLQ diagnostik dengan menambahkan `.DLQ` pada topic sumber.
Event asli tetap dipertahankan; replay harus menjadi tindakan operasional eksplisit berdasarkan topic, partition, dan
offset agar operator dapat meninjau record terlebih dahulu. Deduplikasi inbox dan business reference unik membuat replay
event aman dari dampak finansial ganda.

Profile integration menjalankan PostgreSQL dan Kafka melalui Testcontainers:

```bash
mvn -pl services/loan-service -am -Pintegration-tests verify
```

Ledger service juga menyediakan kontrak savings sintetis. Pembukaan account, deposit, withdrawal, perubahan status,
dan statement dengan pagination keyset semuanya merupakan kontrak DTO dalam envelope respons yang sama:

```text
POST /api/v1/savings/accounts
POST /api/v1/savings/accounts/{accountId}/deposits
POST /api/v1/savings/accounts/{accountId}/withdrawals
POST /api/v1/savings/accounts/{accountId}/status
GET  /api/v1/savings/accounts/{accountId}
GET  /api/v1/savings/accounts/{accountId}/statement?limit=50&cursor=...
```

Setiap command finansial savings membutuhkan `Idempotency-Key`. Deposit dan withdrawal memperbarui saldo terwujud,
baris statement yang tidak dapat diubah, jurnal double-entry seimbang, event audit, dan ledger outbox dalam satu transaksi PostgreSQL.
Baris account dikunci dengan `SELECT ... FOR UPDATE`; withdrawal bersamaan tidak dapat menghasilkan saldo negatif.
Account `FROZEN` menolak operasi finansial, sedangkan account `CLOSED` tidak dapat dibuka kembali.

### Search, Redis, dan Kafka Streams

`search-service` mengonsumsi event loan dan repayment ke index loan Elasticsearch serta tampilan activity MongoDB yang
dibatasi. Dokumen Elasticsearch menggunakan nama index berversi eksplisit (`fincore-loans-v1`) dengan mapping
keyword/text/numeric/date. Search menggabungkan filter tepat `status` dan `productCode` dengan teks `query`, yang
mencocokkan deskripsi atau product code. Projection activity membaca `RepaymentConfirmed` dari topic loan dan
`RepaymentJournalPosted` dari topic ledger. Projection lifecycle loan membandingkan version dan menggunakan prioritas
lifecycle untuk menyelesaikan konflik, sehingga funding mengungguli approval dan rejection pada version yang sama.
Riwayat activity melakukan deduplikasi event ID yang dipertahankan, mengurutkan tahap lifecycle sebelum repayment,
serta mengurutkan repayment berdasarkan waktu event. Untuk payload lama tanpa timestamp event, projection menggunakan
timestamp record Kafka. Replay event lifecycle saat ini dapat membangun ulang dokumen search yang hilang dengan tetap
mempertahankan riwayat activity yang ada. Hasil customer memerlukan customer ID hasil projection yang sama persis.
Event tanpa ownership dibiarkan kosong sampai event yang memenuhi syarat mengisinya. Projection dengan customer ID
yang hilang/kosong atau placeholder historis `unknown` hanya terlihat oleh staf loan.

Service juga menjalankan topology Kafka Streams dengan state store deduplikasi event ID persisten selama satu jam dan
tumbling window berbasis event-time selama lima menit dengan grace satu menit. Aggregate dipublikasikan ke
`fincore.loan.analytics.5m.v1`.

Produk loan menggunakan key Redis cache-aside dalam bentuk `fincore:loan:product:{code}:v1`, TTL jitter, timeout
fallback Redis dan PostgreSQL yang dibatasi, serta single-flight yang dibatasi saat cache miss. Redis adalah state cache
bersama yang bukan sumber otoritatif; PostgreSQL tetap menjadi sumber data utama. `RedisAtomicCounter` terpisah hanya
mendemonstrasikan atomic increment untuk metric non-finansial.

### Compose dan Kong

Stack lokal lengkap ditetapkan di `docker-compose.yml`. Stack ini mencakup satu container PostgreSQL dengan tiga
database logis, Keycloak, Kafka KRaft, Redis, Elasticsearch, MongoDB, tiga image Spring Boot non-root, dan Kong DB-less
`3.9.0`. Elasticsearch dan MongoDB berada di jaringan internal Compose dan tidak membuka port host. Baca projection
customer melalui API search yang terautentikasi. Service Compose menggunakan `kafka:19092`. Developer yang menjalankan
aplikasi dari host dapat menggunakan listener plaintext Kafka pada loopback `127.0.0.1:9092`. PostgreSQL dan Keycloak
juga dipublikasikan pada loopback.

```bash
cp -n .env.example .env
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
./scripts/smoke.sh
```

Kong mem-proxy route API bisnis pada port `8000`. Admin API-nya mendengarkan pada `127.0.0.1:8001` di dalam container dan
tidak membuka port host. Gunakan `docker compose exec kong kong health` untuk memeriksanya. Spring service memvalidasi
bearer token dan menerapkan aturan peran serta kepemilikan yang sama melalui Kong dan port langsung `8081` (loan), `8082`
(ledger), serta `8083` (search). Gunakan port langsung tersebut untuk actuator health, readiness, dan OpenAPI. Stack
lokal mempublikasikan proxy Kong, port langsung Spring, dan Redis untuk pengembangan. Batasi akses backend dan Redis
pada deployment bersama.

| Operasi | Akses |
|---|---|
| Health dan readiness actuator, OpenAPI, daftar produk loan, dan loan quote | Publik |
| Pengiriman loan application | `CUSTOMER` untuk `customerId` sendiri, atau `LOAN_OPERATOR` dan `LOAN_APPROVER` lintas customer |
| Approval dan rejection loan | `LOAN_APPROVER` |
| Funding, repayment, dan laporan loan | `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Pembukaan rekening, deposit, dan withdrawal savings | `CUSTOMER` untuk customer/rekening sendiri, serta `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Pembacaan rekening dan statement savings | `CUSTOMER` untuk rekening sendiri, atau `SAVINGS_OPERATOR` lintas customer |
| Perubahan status savings dan pembacaan journal | `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Search dan activity loan | `CUSTOMER` untuk projection sendiri, atau `LOAN_OPERATOR` dan `LOAN_APPROVER` lintas customer |

Akses customer membutuhkan kecocokan tepat dengan claim `customer_id` pada JWT. Account savings milik customer lain
menghasilkan `403`. Search customer tidak menampilkan projection milik customer lain atau projection tanpa kepemilikan.
Activity di luar scope caller menghasilkan `404`. Bearer token yang tidak ada atau tidak valid pada route yang dilindungi
menghasilkan `401`. Token yang valid tetapi perannya tidak cukup menghasilkan `403`.

#### Keycloak lokal

**Khusus pengembangan lokal.** Startup Compose di atas mengimpor realm demo dan menunggu Keycloak siap sebelum
menjalankan Spring service.

PostgreSQL dipublikasikan pada `127.0.0.1:5432` secara default. Jika port tersebut digunakan, set `POSTGRES_HOST_PORT`
di `.env` ke port yang tersedia. Init script membuat database `fincore_keycloak` dan owner non-superuser khusus
`fincore_keycloak` dengan password lokal `fincore_keycloak_local`. Jika Anda melakukan override
`KEYCLOAK_DB_USERNAME` atau `KEYCLOAK_DB_PASSWORD` di `.env`, siapkan login, password, dan kepemilikan yang sesuai di
PostgreSQL terlebih dahulu.

Untuk volume PostgreSQL yang sudah ada dan diinisialisasi sebelum role khusus ditambahkan, hentikan Keycloak lalu
jalankan init script idempotent. Script tersebut menyiapkan database Keycloak jika diperlukan dan memindahkan ownership
tabel serta sequence Keycloak yang sudah ada tanpa menghapus datanya.

```bash
docker compose stop keycloak
docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U fincore -d postgres \
  < docker/postgres/init/01-create-databases.sql
docker compose up -d --wait --wait-timeout 180 keycloak
docker compose up -d --wait --wait-timeout 180
```

[Konsol admin Keycloak](http://localhost:8180/admin/) dimulai dengan `admin` / `change_me_locally` kecuali diubah
di `.env` sebelum startup pertama. Startup `--import-realm` hanya mengimpor fixture ketika realm `fincore` belum ada.
Terapkan perubahan fixture langsung melalui konsol admin atau `kcadm` agar user dan pengaturan yang ada tetap terjaga.
Untuk memperbarui claim `sub`, buka **Clients → fincore-cli → Client scopes → Add client scope** lalu tambahkan `basic`
sebagai **Default**. Untuk mengganti realm demo yang boleh dibuang, hapus hanya `fincore` di konsol admin lalu gunakan
**Create realm** dengan `docker/keycloak/realm-fincore.json`. Ini mereset data realm demo tanpa menghapus shared volume
PostgreSQL.

Issuer kanonis (`OIDC_ISSUER_URI`) adalah `http://localhost:8180/realms/fincore` dan audience API
(`OIDC_AUDIENCE`) adalah `fincore-api`. Loan, ledger, dan search memvalidasi kedua nilai tersebut. Compose mengambil
signing key menggunakan `OIDC_JWK_SET_URI=http://keycloak:8080/realms/fincore/protocol/openid-connect/certs` pada
jaringan internal. Service yang dijalankan dari host secara default menggunakan endpoint certs Keycloak dari issuer.
Anda juga dapat mengatur `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` dan
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` pada Spring service. Jalankan Keycloak sebelum mengirim
permintaan bearer. Autentikasi sintetis `X-Actor-Id` dan `X-Actor-Role` tersedia melalui fixture test yang diimpor secara
eksplisit. Loan dan ledger service yang dipaketkan hanya menerima `FINCORE_SECURITY_MODE=oauth2`. Service memetakan
role dari `resource_access.fincore-api.roles` dan menggunakan `sub` sebagai audit actor ID.

| Nama pengguna demo | Kata sandi demo | Peran client | `customer_id` |
|---|---|---|---|
| `customer1` | `customer1` | `CUSTOMER` | `CUST-001` |
| `customer2` | `customer2` | `CUSTOMER` | `CUST-002` |
| `loan-operator` | `loan-operator` | `LOAN_OPERATOR` | |
| `loan-approver` | `loan-approver` | `LOAN_APPROVER` | |
| `savings-operator` | `savings-operator` | `SAVINGS_OPERATOR` | |

Helper token membutuhkan `curl` dan `jq`. Secara default helper menggunakan `customer1` pada `fincore-cli`. Tangkap
stdout yang hanya berisi token ke variabel shell dengan tracing dinonaktifkan.

```bash
set +x
operator_token=$(KEYCLOAK_USERNAME=loan-operator KEYCLOAK_PASSWORD=loan-operator ./scripts/keycloak-token.sh)
printf 'Authorization: Bearer %s\n' "$operator_token" |
  curl --disable --fail-with-body --silent --show-error --max-time 30 --header @- \
  'http://localhost:8000/api/v1/search/loans?limit=10' >/dev/null
unset operator_token
```

Override `KEYCLOAK_URL`, `KEYCLOAK_REALM`, dan `KEYCLOAK_CLIENT_ID` untuk endpoint token. Variabel username dan
password helper adalah `KEYCLOAK_USERNAME` dan `KEYCLOAK_PASSWORD`.

#### Smoke test autentikasi

Jalankan `./scripts/smoke.sh` setelah `curl`, `jq`, dan `uuidgen` tersedia. Secara default script menggunakan Kong pada
`BASE_URL=http://localhost:8000`, memperoleh tiga token melalui helper, lalu memverifikasi akses publik, respons tanpa
token, respons dengan peran yang salah, pencarian operator, dan kepemilikan savings. Script memeriksa status HTTP, envelope
API, serta kecocokan ID respons. Script mencetak status dan identifier trace untuk pemecahan masalah.

Untuk realm demo alternatif, atur variabel endpoint helper di atas dan, bila diperlukan, variabel khusus smoke
`KEYCLOAK_CUSTOMER1_USERNAME`, `KEYCLOAK_CUSTOMER2_USERNAME`, serta `KEYCLOAK_OPERATOR_USERNAME` beserta variabel
`_PASSWORD` masing-masing. Alternatifnya, berikan `CUSTOMER1_ACCESS_TOKEN`, `CUSTOMER2_ACCESS_TOKEN`, atau
`OPERATOR_ACCESS_TOKEN` (`SEARCH_ACCESS_TOKEN` adalah alias untuk token operator). Token customer harus memetakan ke
`CUST-001` dan `CUST-002`, sedangkan operator harus dapat mencari loan lintas customer.

Setiap eksekusi baru membuka satu rekening demo bersaldo nol untuk `CUST-001` dan membiarkannya tersedia untuk inspeksi
audit. Untuk replay tanpa membuat account baru, set `SMOKE_IDEMPOTENCY_KEY` ke nilai awal. Nilai default yang dibuat
adalah `smoke-<correlation-id>` menggunakan correlation ID yang dicetak pada eksekusi awal.

Untuk kegagalan startup atau token, gunakan `docker compose ps` dan `docker compose logs --tail=100 keycloak` untuk
memeriksa readiness dan import realm. Realm yang kedaluwarsa atau tidak sesuai dapat diperbaiki menggunakan langkah
konsol di atas. Setelah mengubah pengaturan OIDC service, jalankan `docker compose up -d --wait --wait-timeout 180`
untuk menerapkannya sebelum mengulang smoke script. Setelah rebuild service, Kong dapat mengalami `502` singkat saat
IP upstream berubah. Jika permintaan langsung berhasil tetapi error gateway tetap ada, jalankan `docker compose restart kong`
dan `docker compose exec kong kong health`.

#### OIDC produksi

Gunakan issuer HTTPS produksi dan endpoint JWKS yang dapat dijangkau setiap service. Ganti `OIDC_ISSUER_URI` dengan
issuer token yang tepat, `OIDC_JWK_SET_URI` dengan URL JWKS provider, dan `OIDC_AUDIENCE` dengan audience API.
Konfigurasikan provider agar menerbitkan claim `sub`, `customer_id`, dan `resource_access.fincore-api.roles` yang
diharapkan. Untuk klien browser dan mobile, gunakan Authorization Code dengan PKCE dan public client; jangan tanam
client secret di aplikasi tersebut. Untuk komunikasi antar-service, gunakan confidential client dengan Client
Credentials dan lakukan rotasi secret melalui secret store deployment. Nonaktifkan password grant demo lokal di
produksi. Sediakan secret database, bootstrap, dan client melalui secret store deployment. Batasi projection store,
endpoint administratif, dan port backend pada jaringan terbatas.

#### Contoh API

Gunakan `Authorization: Bearer <access-token>` pada endpoint yang dilindungi. Identitas audit berasal dari subject token dan
peran client yang dipetakan.

API Loan pertama tersedia pada `GET /api/v1/loan-products`. Endpoint ini hanya mengembalikan produk aktif, menggunakan
cursor pagination (`limit` 1–100), dan menserialisasi nominal uang sebagai string desimal. Contoh:

```text
GET http://localhost:8081/api/v1/loan-products?limit=50
```

Autentikasi disediakan sebagai facade tipis untuk pengembangan lokal di atas Keycloak. Endpoint ini mengembalikan
envelope respons standar dan tidak pernah mencatat password atau token ke log:

```http
POST http://localhost:8000/api/v1/auth/login
Content-Type: application/json

{"username":"product-admin","password":"product-admin"}
```

Produk loan dapat dibuat dari Swagger terpadu dengan token `LOAN_PRODUCT_ADMIN`, atau melalui gateway:

```http
POST http://localhost:8000/api/v1/loan-products
Authorization: Bearer <product-admin-access-token>
Idempotency-Key: product-demo-001
Content-Type: application/json

{"code":"KTA_ANNUITY","interestType":"ANNUITY","annualRate":0.12,
 "minAmount":1000000,"maxAmount":50000000,"active":true}
```

Endpoint ini mengembalikan `201 Created`, menulis produk dan baris audit dalam transaksi lokal yang sama, serta
menghapus cache produk Redis. Product code duplikat mengembalikan `409 CONFLICT`; penggunaan kembali idempotency key
dengan payload berbeda juga mengembalikan `409`.

Untuk mendemonstrasikan instance loan kedua yang berbagi PostgreSQL, Kafka, dan Redis, build stack dasar terlebih dahulu
lalu jalankan profil scale:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale up -d loan-service-2
curl http://localhost:8084/actuator/health
```

Setelah acknowledgement jurnal funding diterima, `loan-service` membuat jadwal flat atau annuity dan menyediakan
command repayment simulasi untuk `LOAN_OPERATOR` dan `LOAN_APPROVER`.

```text
POST /api/v1/loans/{loanId}/repayments
Idempotency-Key: repayment-demo-001
Content-Type: application/json

{"amount": "18.0000"}
```

Respons adalah `202 Accepted` dengan `CONFIRMED_AWAITING_LEDGER`. Alokasi dilakukan ke bunga terlebih dahulu lalu
principal; event outbox `RepaymentConfirmed` dikirim ke ledger, yang menerbitkan `RepaymentJournalPosted`. Hanya
acknowledgement yang tersimpan permanen tersebut yang menandai repayment sebagai `POSTED` dan menerapkan alokasi ke jadwal. Retry
repayment yang berhasil dengan idempotency key dan nominal yang sama mengembalikan repayment awal, termasuk setelah
pelunasan.

Loan quote bersifat publik pada `POST /api/v1/loan-quotes`. Endpoint ini mendukung produk `FLAT` dan `ANNUITY`,
memvalidasi rentang nominal produk, mengembalikan jadwal angsuran, dan menggunakan `404 RESOURCE_NOT_FOUND` ketika
produk aktif tidak ditemukan.

Pengiriman loan application pada `POST /api/v1/loan-applications` membutuhkan bearer token dan header `Idempotency-Key`.
Header `X-Correlation-Id` dan `X-Request-Id` bersifat opsional. `CUSTOMER` harus mengirim `customerId` yang sama
persis dengan claim `customer_id` pada token. Ketidaksesuaian mengembalikan `403 FORBIDDEN`. `LOAN_OPERATOR` dan
`LOAN_APPROVER` dapat submit untuk customer mana pun.

```http
POST http://localhost:8081/api/v1/loan-applications
Idempotency-Key: app-001
Content-Type: application/json

{"customerId":"CUST-001","productCode":"PERSONAL_FLAT","principalAmount":"1000000.0000","tenorMonths":12}
```

Pengiriman membuat application berstatus `SUBMITTED`, event audit yang hanya dapat ditambahkan, dan event outbox `LoanSubmitted` dalam
transaksi lokal yang sama. Pengulangan key dan payload yang sama me-replay application awal (`201`), termasuk setelah
produk dinonaktifkan. Penggunaan kembali key dengan payload berbeda mengembalikan `409 CONFLICT`.

Hanya `LOAN_APPROVER` yang dapat menyetujui atau menolak application.

```http
POST http://localhost:8081/api/v1/loan-applications/{applicationId}/approve
Idempotency-Key: approve-001

POST http://localhost:8081/api/v1/loan-applications/{applicationId}/reject
Idempotency-Key: reject-001
Content-Type: application/json

{"reasonCode":"CREDIT_POLICY","reason":"Debt service ratio is too high"}
```

Hanya application berstatus `SUBMITTED` yang dapat berpindah status. Approval menghasilkan `APPROVED` dan `LoanApproved`;
penolakan menghasilkan `REJECTED` dan `LoanRejected`. Kedua transisi menaikkan application version, menggunakan
optimistic locking, menulis record audit dan outbox dalam transaksi yang sama, serta mengembalikan `409 CONFLICT` untuk
command yang kedaluwarsa atau status terminal.

Funding adalah command idempotent terpisah yang tersedia untuk `LOAN_OPERATOR` dan `LOAN_APPROVER`.

```http
POST http://localhost:8081/api/v1/loans/{applicationId}/fund
Idempotency-Key: fund-001
Content-Type: application/json

{}
```

Saklar kegagalan sandbox opsional adalah `{"simulateFailure":true}`. Permintaan normal menyimpan `FUNDING_PENDING`,
menjalankan simulator deterministik, lalu commit `FUNDED` dan event outbox `LoanFunded` berversi dalam transaksi loan
yang sama. Consumer ledger melakukan deduplikasi event, menulis jurnal seimbang `LOAN_RECEIVABLE` /
`SIMULATED_SETTLEMENT_CLEARING`, dan menerbitkan acknowledgement `FundingJournalPosted` melalui ledger outbox.
Consumer loan menerapkan acknowledgement tersebut satu kali dan hanya mengubah `ledgerPostingStatus` menjadi `POSTED`;
funding yang berhasil saja tidak berarti ledger sudah posted.
