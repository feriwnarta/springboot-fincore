# FinCore Use Cases & API Guide

Panduan ini menjelaskan tujuan fitur, aktor, alur bisnis, kontrak request/response, dan contoh `curl` untuk
pengujian FinCore melalui Kong Gateway. Semua endpoint pada dokumen ini menggunakan base URL berikut:

```bash
export BASE_URL=http://localhost:8000
```

> Data akun, password, dan URL di dokumen ini hanya untuk local development. Jangan gunakan credential ini di
> production.

## 1. Gambaran layanan dan tanggung jawab

| Layanan | Tanggung jawab | Database/read model |
|---|---|---|
| Loan Service | Produk pinjaman, quotation, pengajuan, keputusan, funding, repayment | PostgreSQL Loan |
| Ledger Service | Savings account, saldo, transaksi uang, jurnal double-entry | PostgreSQL Ledger |
| Search Service | Read model untuk daftar produk, aplikasi, account, dan pencarian loan | Elasticsearch |
| Kong Gateway | Satu pintu akses HTTP, routing, CORS, rate limit, dan security boundary | Kong |
| Keycloak | Login OAuth2/OIDC dan penerbitan JWT | Keycloak |
| Kafka | Event antar-service dan proses Saga/outbox | Kafka |
| Redis | Cache/idempotency/supporting data sesuai konfigurasi | Redis |

Prinsip penting:

- Perintah yang mengubah data dikirim ke service pemilik data.
- Query daftar/read dikirim ke Search Service agar frontend memakai read model Elasticsearch.
- Elasticsearch bersifat eventually consistent. Setelah command berhasil, data dapat memerlukan beberapa saat untuk
  muncul di endpoint Search.
- Setiap command sebaiknya mengirim `Idempotency-Key` unik agar retry tidak menggandakan transaksi.

## 2. Aktor dan hak akses

| Aktor | Role Keycloak | Fungsi utama |
|---|---|---|
| Customer | `CUSTOMER` | Melihat produk, membuat quote, mengajukan pinjaman, melihat aplikasi sendiri, membuka account, deposit, withdrawal, dan membayar pinjaman |
| Loan operator | `LOAN_OPERATOR` | Menjalankan/menindaklanjuti proses funding dan operasional loan |
| Loan approver | `LOAN_APPROVER` | Menyetujui atau menolak pengajuan loan |
| Savings operator | `SAVINGS_OPERATOR` | Operasional savings account, saldo, deposit, withdrawal, dan status account |
| Product admin | `LOAN_PRODUCT_ADMIN` | Membuat dan mengelola produk loan |
| Auditor/support | role read-only yang dikonfigurasi | Menelusuri jurnal, statement, audit log, dan status proses |

Akun demo local:

| Username | Password | Role | Customer ID |
|---|---|---|---|
| `customer1` | `customer1` | `CUSTOMER` | `CUST-001` |
| `customer2` | `customer2` | `CUSTOMER` | `CUST-002` |
| `loan-operator` | `loan-operator` | `LOAN_OPERATOR` | - |
| `loan-approver` | `loan-approver` | `LOAN_APPROVER` | - |
| `savings-operator` | `savings-operator` | `SAVINGS_OPERATOR` | - |
| `product-admin` | `product-admin` | `LOAN_PRODUCT_ADMIN` | - |

## 3. Konvensi HTTP dan response

### 3.1 Header

Semua request yang dilindungi memakai:

```http
Authorization: Bearer <access-token>
X-Correlation-Id: <id-tracing-bisnis>
X-Request-Id: <id-request>
Content-Type: application/json
```

Untuk command `POST` yang membuat atau mengubah data, tambahkan:

```http
Idempotency-Key: <uuid-unik>
```

`X-Correlation-Id` dipakai untuk tracing lintas service. `Idempotency-Key` dipakai untuk mencegah duplikasi saat
client melakukan retry karena timeout.

### 3.2 Response sukses dan error

Response sukses memakai HTTP status sesuai operasi. Bentuk body utama ditentukan oleh DTO endpoint. Error distandarkan
sebagai problem detail berikut:

```json
{
  "timestamp": "2026-11-18T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/loan-applications",
  "requestId": "...",
  "correlationId": "...",
  "details": []
}
```

Status yang sering ditemui:

| Status | Arti |
|---|---|
| `200` | Query atau command berhasil |
| `201` | Resource baru dibuat |
| `400` | Payload/parameter tidak valid |
| `401` | Token tidak ada atau tidak valid |
| `403` | Role tidak memiliki izin |
| `404` | Resource tidak ditemukan |
| `409` | Konflik status, duplicate idempotency, atau optimistic locking |
| `422` | Rule bisnis tidak terpenuhi |
| `503` | Dependensi/eventual consistency belum siap |

Nominal uang dikirim sebagai string desimal, contohnya `"1000000.00"`, untuk mencegah kehilangan presisi di
frontend.

## 4. Mendapatkan token akses

Cara yang direkomendasikan untuk local development adalah helper script:

```bash
TOKEN=$(KEYCLOAK_USERNAME=customer1 KEYCLOAK_PASSWORD=customer1 bash scripts/keycloak-token.sh)
export TOKEN
echo "$TOKEN" | cut -c1-30
```

Atau langsung melalui endpoint gateway:

```bash
curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"customer1"}' | jq
```

Contoh response:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshExpiresIn": 1800,
  "scope": "openid profile email"
}
```

Simpan access token dan gunakan `Authorization: Bearer $TOKEN` pada request berikutnya. Token customer lain:

```bash
CUSTOMER_TOKEN=$(KEYCLOAK_USERNAME=customer1 KEYCLOAK_PASSWORD=customer1 bash scripts/keycloak-token.sh)
APPROVER_TOKEN=$(KEYCLOAK_USERNAME=loan-approver KEYCLOAK_PASSWORD=loan-approver bash scripts/keycloak-token.sh)
LOAN_OPERATOR_TOKEN=$(KEYCLOAK_USERNAME=loan-operator KEYCLOAK_PASSWORD=loan-operator bash scripts/keycloak-token.sh)
SAVINGS_OPERATOR_TOKEN=$(KEYCLOAK_USERNAME=savings-operator KEYCLOAK_PASSWORD=savings-operator bash scripts/keycloak-token.sh)
PRODUCT_ADMIN_TOKEN=$(KEYCLOAK_USERNAME=product-admin KEYCLOAK_PASSWORD=product-admin bash scripts/keycloak-token.sh)
```

## 5. Use case customer: melihat produk loan

### Tujuan

Customer melihat produk aktif yang dapat dipilih sebelum membuat quotation atau pengajuan. Endpoint list adalah
Search Service dan membaca Elasticsearch.

### API

```http
GET /api/v1/loan-products?limit=20&cursor=<optional-cursor>
```

### Contoh request

```bash
curl -sS "$BASE_URL/api/v1/loan-products?limit=20" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

### Contoh response

```json
{
  "items": [
    {
      "id": "...",
      "code": "KTA-001",
      "interestType": "FLAT",
      "annualRate": "12.00",
      "minAmount": "1000000.00",
      "maxAmount": "50000000.00",
      "active": true,
      "version": 0,
      "updatedAt": "2026-11-18T10:00:00Z"
    }
  ],
  "nextCursor": null
}
```

Jika product baru belum muncul, tunggu projection Kafka selesai atau jalankan replay projection sesuai bagian
troubleshooting.

## 6. Use case customer: quotation dan pengajuan loan

### 6.1 Membuat quotation

Quotation dipakai untuk menghitung estimasi cicilan sebelum customer membuat aplikasi. Quotation bukan persetujuan
loan dan tidak mencairkan dana.

```http
POST /api/v1/loan-quotes
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/loan-quotes" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: quote-$(uuidgen)" \
  -d '{
    "productCode": "KTA-001",
    "principalAmount": "10000000.00",
    "tenorMonths": 12
  }' | jq
```

### 6.2 Mengirim pengajuan

Pengajuan membuat record aplikasi berstatus `SUBMITTED`. Loan Service menyimpan data, audit trail, dan outbox event
dalam transaksi lokal yang sama.

```http
POST /api/v1/loan-applications
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/loan-applications" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Correlation-Id: application-$(uuidgen)" \
  -d '{
    "customerId": "CUST-001",
    "productCode": "KTA-001",
    "principalAmount": "10000000.00",
    "tenorMonths": 12
  }' | jq
```

Response utama:

```json
{
  "applicationId": "...",
  "customerId": "CUST-001",
  "productCode": "KTA-001",
  "principalAmount": "10000000.00",
  "tenorMonths": 12,
  "status": "SUBMITTED",
  "version": 0,
  "businessReference": "LOAN-APP-...",
  "createdAt": "2026-11-18T10:05:00Z"
}
```

Catatan: pada environment demo, `customerId` harus sesuai identitas customer yang digunakan. Pengajuan tidak boleh
langsung dianggap funded; harus melalui keputusan approver dan proses funding.

### 6.3 Melihat aplikasi milik customer

```http
GET /api/v1/loan-applications?customerId=CUST-001&limit=20&status=SUBMITTED
```

```bash
curl -sS "$BASE_URL/api/v1/loan-applications?customerId=CUST-001&limit=20" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

Status aplikasi: `SUBMITTED`, `APPROVED`, atau `REJECTED`.

## 7. Use case admin/approver: keputusan pengajuan

### Tujuan

Approver melakukan underwriting decision. Approval hanya mengubah keputusan aplikasi; pencairan/funding adalah proses
terpisah agar batas kewenangan dan audit trail jelas.

### 7.1 Approve

```http
POST /api/v1/loan-applications/{applicationId}/approve
```

```bash
APPLICATION_ID=<application-id-dari-submit>
curl -sS -X POST "$BASE_URL/api/v1/loan-applications/$APPLICATION_ID/approve" \
  -H "Authorization: Bearer $APPROVER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Correlation-Id: approve-$(uuidgen)" \
  -d '{}' | jq
```

### 7.2 Reject

```http
POST /api/v1/loan-applications/{applicationId}/reject
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/loan-applications/$APPLICATION_ID/reject" \
  -H "Authorization: Bearer $APPROVER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "reasonCode": "INSUFFICIENT_INCOME",
    "reason": "Income verification did not meet policy"
  }' | jq
```

Rule penting:

- Hanya approver yang boleh memutuskan approve/reject.
- Aplikasi yang sudah berada pada status final tidak boleh diputuskan ulang.
- Semua keputusan mencatat actor, role, waktu, status sebelum/sesudah, reason, correlation ID, dan request ID.

## 8. Use case operator: funding/pencairan loan

### Tujuan

Operator memulai pencairan untuk aplikasi yang telah `APPROVED`. Funding menjalankan Saga lintas Loan dan Ledger:

```text
Loan: APPROVED
  -> LoanFundingRequested
  -> Ledger membuat jurnal double-entry
  -> Ledger mengirim LoanFundingLedgerPosted / failure event
  -> Loan: FUNDED atau FUNDING_FAILED
```

Saga bersifat asynchronous dan aman untuk retry melalui outbox, consumer idempotency, dan business reference.

### API

```http
POST /api/v1/loans/{applicationId}/fund
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/loans/$APPLICATION_ID/fund" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Correlation-Id: funding-$(uuidgen)" \
  -d '{"simulateFailure":false}' | jq
```

Response awal dapat menunjukkan `FUNDING_PENDING`. Poll aplikasi/loan activity sampai `FUNDED` atau `FUNDING_FAILED`.
Jika ledger gagal, tidak boleh dianggap sukses; operator melakukan retry/reconciliation sesuai audit trail.

## 9. Use case customer/operator: savings account dan saldo

### 9.1 Membuka account

Customer atau savings operator membuka account. Account dimulai dari status yang dikonfigurasi service, lalu digunakan
untuk transaksi setelah aktif.

```http
POST /api/v1/savings/accounts
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/savings/accounts" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"customerId":"CUST-001"}' | jq
```

Response:

```json
{
  "accountId": "...",
  "accountNumber": "...",
  "customerId": "CUST-001",
  "currency": "IDR",
  "status": "ACTIVE",
  "currentBalance": "0.00",
  "version": 0,
  "createdAt": "2026-11-18T10:10:00Z",
  "updatedAt": "2026-11-18T10:10:00Z"
}
```

### 9.2 List account dan melihat saldo

List account adalah query Search Service/Elasticsearch:

```http
GET /api/v1/savings/accounts?customerId=CUST-001&status=ACTIVE&limit=20
```

```bash
curl -sS "$BASE_URL/api/v1/savings/accounts?customerId=CUST-001&status=ACTIVE&limit=20" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

Untuk detail saldo authoritative, gunakan Ledger Service:

```http
GET /api/v1/savings/accounts/{accountId}
```

```bash
ACCOUNT_ID=<account-id-dari-open-account>
curl -sS "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

Saldo pada detail Ledger adalah source of truth. Saldo pada Search adalah read model dan dapat tertinggal sesaat.

### 9.3 Deposit

Deposit menambah saldo dengan transaksi yang atomik. Service menggunakan row lock, journal double-entry, audit trail,
dan outbox event.

```http
POST /api/v1/savings/accounts/{accountId}/deposits
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/deposits" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Correlation-Id: deposit-$(uuidgen)" \
  -d '{"amount":"2500000.00"}' | jq
```

### 9.4 Withdrawal

Withdrawal mengurangi saldo hanya bila account aktif dan saldo mencukupi. Request yang sama tidak boleh diproses dua
kali karena idempotency.

```http
POST /api/v1/savings/accounts/{accountId}/withdrawals
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/withdrawals" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":"500000.00"}' | jq
```

Response transaksi:

```json
{
  "transactionId": "...",
  "accountId": "...",
  "businessReference": "SAVINGS-...",
  "operationType": "DEPOSIT",
  "amount": "2500000.00",
  "balanceAfter": "2500000.00",
  "journalId": "...",
  "createdAt": "2026-11-18T10:15:00Z"
}
```

### 9.5 Statement account

```http
GET /api/v1/savings/accounts/{accountId}/statement?cursor=<optional>&limit=50
```

```bash
curl -sS "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/statement?limit=50" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
```

Response memiliki `transactions` dan `nextCursor` untuk pagination cursor-based.

### 9.6 Mengubah status account

Savings operator dapat membekukan atau menutup account. Account `FROZEN` tidak boleh menerima transaksi normal dan
account `CLOSED` tidak dapat digunakan kembali.

```http
POST /api/v1/savings/accounts/{accountId}/status
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/savings/accounts/$ACCOUNT_ID/status" \
  -H "Authorization: Bearer $SAVINGS_OPERATOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"status":"FROZEN"}' | jq
```

Nilai status yang valid: `ACTIVE`, `FROZEN`, `CLOSED`.

## 10. Use case customer: pembayaran/cicilan loan

### Tujuan

Customer mengirim pembayaran untuk loan yang sudah funded. Payment memulai Saga repayment:

```text
Loan: menerima repayment request
  -> PENDING_SIMULATION/CONFIRMED_AWAITING_LEDGER
  -> Ledger membuat jurnal pembayaran
  -> RepaymentJournalPosted
  -> Loan: POSTED dan schedule/balance diperbarui
```

### API dan request

```http
POST /api/v1/loans/{loanId}/repayments
```

```bash
LOAN_ID=<loan-id-dari-funding>
curl -sS -X POST "$BASE_URL/api/v1/loans/$LOAN_ID/repayments" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Correlation-Id: repayment-$(uuidgen)" \
  -d '{"amount":"1000000.00"}' | jq
```

Response dapat memiliki status `PENDING_SIMULATION`, `CONFIRMED_AWAITING_LEDGER`, `POSTED`, atau `FAILED`. Pembayaran
baru dianggap berhasil lunas/terposting bila statusnya `POSTED` dan `ledgerReference` sudah tersedia.

## 11. Use case product admin: membuat produk loan

### Tujuan

Product admin membuat parameter produk yang menjadi referensi quotation dan aplikasi. Perubahan produk diproyeksikan ke
Search Service melalui event.

### API dan request

```http
POST /api/v1/loan-products
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/loan-products" \
  -H "Authorization: Bearer $PRODUCT_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Correlation-Id: product-$(uuidgen)" \
  -d '{
    "code":"KTA-001",
    "interestType":"FLAT",
    "annualRate":"12.00",
    "minAmount":"1000000.00",
    "maxAmount":"50000000.00",
    "active":true
  }' | jq
```

`interestType` yang tersedia: `FLAT` dan `ANNUITY`. Setelah response `201`, gunakan Loan Service untuk command dan
Search Service untuk memastikan produk sudah masuk read model.

## 12. Use case auditor/support: journal, activity, dan laporan

### 12.1 Menelusuri jurnal berdasarkan business reference

```http
GET /api/v1/journals/by-business-ref/{reference}
```

```bash
curl -sS "$BASE_URL/api/v1/journals/by-business-ref/$BUSINESS_REFERENCE" \
  -H "Authorization: Bearer $SAVINGS_OPERATOR_TOKEN" | jq
```

Journal harus balanced: total debit sama dengan total credit. Status jurnal dapat berupa `PENDING`, `POSTED`, atau
`REVERSED`.

### 12.2 Activity loan

```http
GET /api/v1/loans/{loanId}/activity
```

```bash
curl -sS "$BASE_URL/api/v1/loans/$LOAN_ID/activity" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq
```

Activity membantu support menelusuri state transition, event, correlation ID, dan kegagalan Saga tanpa mengubah data.

### 12.3 Laporan loan

```bash
curl -sS "$BASE_URL/api/v1/reports/loans/top-customers?from=2026-01-01&to=2026-12-31&limit=10" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq

curl -sS "$BASE_URL/api/v1/reports/loans/top-three?from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq

curl -sS "$BASE_URL/api/v1/reports/loans/running-total?from=2026-01-01&to=2026-12-31" \
  -H "Authorization: Bearer $LOAN_OPERATOR_TOKEN" | jq
```

Parameter `from` dan `to` menggunakan format tanggal sederhana `YYYY-MM-DD`. `from` bersifat inclusive,
sedangkan `to` juga dihitung penuh sampai akhir hari tersebut. Contoh `to=2026-12-31` mencakup seluruh tanggal
31 Desember 2026.

## 13. Daftar endpoint lengkap melalui gateway

| Use case | Method | Endpoint | Owner |
|---|---:|---|---|
| Login | POST | `/api/v1/auth/login` | Loan/Auth |
| List product | GET | `/api/v1/loan-products` | Search |
| Create product | POST | `/api/v1/loan-products` | Loan |
| Quote | POST | `/api/v1/loan-quotes` | Loan |
| Submit application | POST | `/api/v1/loan-applications` | Loan |
| List applications | GET | `/api/v1/loan-applications` | Search |
| Approve application | POST | `/api/v1/loan-applications/{applicationId}/approve` | Loan |
| Reject application | POST | `/api/v1/loan-applications/{applicationId}/reject` | Loan |
| Fund loan | POST | `/api/v1/loans/{applicationId}/fund` | Loan |
| Repay loan | POST | `/api/v1/loans/{loanId}/repayments` | Loan |
| List savings accounts | GET | `/api/v1/savings/accounts` | Search |
| Open savings account | POST | `/api/v1/savings/accounts` | Ledger |
| Account detail/saldo | GET | `/api/v1/savings/accounts/{accountId}` | Ledger |
| Account status | POST | `/api/v1/savings/accounts/{accountId}/status` | Ledger |
| Deposit | POST | `/api/v1/savings/accounts/{accountId}/deposits` | Ledger |
| Withdrawal | POST | `/api/v1/savings/accounts/{accountId}/withdrawals` | Ledger |
| Statement | GET | `/api/v1/savings/accounts/{accountId}/statement` | Ledger |
| Journal lookup | GET | `/api/v1/journals/by-business-ref/{reference}` | Ledger |
| Loan activity | GET | `/api/v1/loans/{loanId}/activity` | Search |

## 14. Urutan uji end-to-end yang direkomendasikan

1. Login sebagai `product-admin`, buat product, dan catat `code`.
2. Tunggu projection lalu login sebagai customer dan list product melalui Search.
3. Buat quote, submit application, dan catat `applicationId`.
4. Login sebagai approver, approve application.
5. Login sebagai loan operator, fund application; tunggu status Saga menjadi `FUNDED`.
6. Login sebagai customer, buka savings account dan catat `accountId`.
7. Deposit, cek detail saldo Ledger, lalu cek list account Search.
8. Kirim repayment, cek status repayment dan ledger journal.
9. Cek statement account, loan activity, dan audit/log berdasarkan `X-Correlation-Id`.

## 15. Audit trail, logging, dan observability

Setiap command penting harus dapat ditelusuri melalui:

- `actor`/subject JWT dan role;
- endpoint, method, status sebelum/sesudah, dan business reference;
- `X-Request-Id` dan `X-Correlation-Id`;
- timestamp, service, event type, dan hasil proses;
- alasan reject, failure code, atau reconciliation requirement;
- log terstruktur melalui SLF4J tanpa mencatat access token, password, atau data sensitif.

Audit trail bukan pengganti journal. Journal membuktikan dampak finansial double-entry, sedangkan audit trail
membuktikan siapa melakukan aksi dan bagaimana state berubah.

## 16. Troubleshooting

### `no Route matched with those values`

Pastikan URL memakai `http://localhost:8000`, path `/api/v1/...`, dan method yang benar. Cek route Kong:

```bash
docker compose ps
curl -i "$BASE_URL/api/v1/loan-products?limit=1" -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

### `401` atau `403`

Token mungkin expired atau role tidak sesuai. Login ulang dengan user yang benar. Contoh: approve membutuhkan
`loan-approver`, sedangkan create product membutuhkan `product-admin`.

### Data belum ada di Search

Search adalah eventually consistent. Pastikan event Kafka dan consumer sehat. Untuk replay event projection local:

```bash
bash scripts/replay-search-projections.sh
```

### Funding/repayment tertahan

Periksa status resource, `loan activity`, journal berdasarkan `businessReference`, log service, dan event Kafka. Jangan
mengulang command tanpa `Idempotency-Key` baru yang terkontrol; gunakan business reference untuk rekonsiliasi.

## 17. Swagger dan dokumentasi interaktif

Unified Swagger melalui gateway:

- [Unified Swagger](http://localhost:8000/swagger-ui/index.html)
- [Loan Service](http://localhost:8000/swagger-ui/index.html?urls.primaryName=Loan%20Service)
- [Ledger Service](http://localhost:8000/swagger-ui/index.html?urls.primaryName=Ledger%20Service)
- [Search Service](http://localhost:8000/swagger-ui/index.html?urls.primaryName=Search%20Service)

Di Swagger, klik **Authorize**, masukkan `Bearer <access-token>`, lalu jalankan endpoint sesuai role. Swagger membantu
uji kontrak HTTP; urutan bisnis dan penantian Saga tetap mengikuti panduan ini.
