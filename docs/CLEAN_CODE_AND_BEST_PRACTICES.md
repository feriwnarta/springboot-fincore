# FinCore Clean Code & Best Practices

Dokumen ini menjadi aturan kualitas kode untuk seluruh service FinCore.

## Dependency Injection

- `@Service`, `@Repository`, `@Component`, `@RestController`, dan `@Configuration` digunakan untuk mendaftarkan bean ke Spring IoC.
- Dependency wajib menggunakan constructor injection.
- Gunakan Lombok `@RequiredArgsConstructor` dengan field `private final`.
- `@Autowired` tidak digunakan pada constructor tunggal.
- Setter injection hanya untuk dependency opsional atau property yang memang dapat berubah; bukan untuk dependency utama.
- Hindari field injection `@Autowired private ...`.
- Jangan gunakan `@AllArgsConstructor` untuk service karena dapat memasukkan field non-dependency ke constructor.

Contoh:

```java
@Service
@RequiredArgsConstructor
public class JournalPostingService {

    private final JournalRepository journalRepository;
    private final LedgerAuditRepository auditRepository;
    private final LedgerOutboxRepository outboxRepository;
}
```

## Boundary dan arsitektur

- Controller hanya menangani HTTP, validation, metadata request, dan mapping DTO.
- Application service menangani orchestration dan transaction boundary.
- Domain tidak bergantung pada Spring, JDBC, Kafka, Redis, Elasticsearch, atau MongoDB.
- Implementasi policy domain diregistrasikan dari package `config`; class domain tidak menggunakan stereotype Spring seperti `@Component`.
- Port domain berada di domain untuk kontrak business state; port query/read-model boleh berada di application agar query projection tidak masuk ke domain.
- Adapter tetap berada di infrastructure.
- SQL hanya berada di adapter persistence.
- Request/response DTO tidak dipakai sebagai domain model atau event payload.
- Mapper eksplisit digunakan pada setiap boundary.

## Financial code

- Gunakan `BigDecimal`, tidak menggunakan `float` atau `double`.
- Nominal event dan response memakai decimal string dengan scale yang terdokumentasi.
- Jurnal wajib memvalidasi debit = kredit.
- Idempotency dan unique business reference harus dijaga di database.
- Redis tidak boleh menjadi sumber kebenaran saldo atau ledger.

## Logging dan audit

- Gunakan SLF4J (`org.slf4j`) sebagai logging facade pada application dan library code.
- Gunakan Lombok `@Slf4j` bila module sudah menggunakan Lombok; alternatifnya gunakan `LoggerFactory` dengan field `private static final`.
- Logback digunakan sebagai runtime backend melalui konfigurasi `logback-spring.xml` dari Spring Boot.
- Jangan mengimpor class Logback (`ch.qos.logback.*`) ke business code; konfigurasi backend hanya berada di resource logging.
- Gunakan parameterized logging, misalnya `log.info("Journal posted: {}", journalId)`, bukan konkatenasi string.
- Sertakan exception sebagai parameter terakhir, misalnya `log.error("Failed to process event {}", eventId, exception)`.
- Gunakan structured logging dengan `correlationId`, `requestId`, `eventId`, `businessReference`, dan `outcome` jika tersedia.
- Gunakan MDC untuk correlation metadata dan pastikan context dibersihkan pada akhir request atau message processing.
- Jangan log token, password, secret, raw PII, atau request body finansial secara penuh.
- Audit trail append-only dan berada dalam transaction yang sama dengan state bisnis/outbox.
- Error response tidak mengekspos stacktrace.

## Testing

- Fitur baru dimulai dari failing test.
- Domain memakai pure unit test.
- Application service memakai fake port atau mock hanya jika boundary memang perlu diisolasi.
- Adapter JDBC memakai embedded database atau integration test.
- Spring context test harus tetap lulus.
- Perubahan tidak boleh selesai sebelum `mvn clean verify` lulus.

## Code review checklist

- Apakah class memiliki satu tanggung jawab?
- Apakah dependency terlihat jelas dari constructor?
- Apakah domain bebas dari framework?
- Apakah DTO mapping eksplisit?
- Apakah transaction boundary tepat?
- Apakah duplicate/retry/concurrency sudah diuji?
- Apakah log bebas dari data sensitif?
- Apakah nama method menjelaskan business behavior?
- Apakah ada code duplication atau dead code?
