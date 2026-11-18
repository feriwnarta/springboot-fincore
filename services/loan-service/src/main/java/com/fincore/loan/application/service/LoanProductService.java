package com.fincore.loan.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fincore.loan.application.command.CreateLoanProductCommand;
import com.fincore.loan.application.query.LoanProductPage;
import com.fincore.loan.domain.enums.IdempotencyStatus;
import com.fincore.loan.domain.model.IdempotencyRecord;
import com.fincore.loan.domain.model.LoanAuditEntry;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.port.LoanAuditRepository;
import com.fincore.loan.domain.port.LoanIdempotencyRepository;
import com.fincore.loan.domain.port.LoanProductRepository;
import com.fincore.loan.domain.model.LoanOutboxEntry;
import com.fincore.loan.domain.port.LoanOutboxRepository;
import com.fincore.platform.security.AuditActor;
import com.fincore.platform.security.CurrentIdentity;
import com.fincore.platform.web.ConflictException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanProductService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    private static final String CREATE_OPERATION = "CREATE_LOAN_PRODUCT";

    private final LoanProductRepository repository;
    private final LoanAuditRepository auditRepository;
    private final LoanIdempotencyRepository idempotencyRepository;
    private final LoanOutboxRepository outboxRepository;
    private final CurrentIdentity currentIdentity;

    @Autowired
    public LoanProductService(LoanProductRepository repository, LoanAuditRepository auditRepository,
            LoanIdempotencyRepository idempotencyRepository, LoanOutboxRepository outboxRepository,
            CurrentIdentity currentIdentity) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.currentIdentity = currentIdentity;
    }

    public LoanProductService(LoanProductRepository repository, LoanAuditRepository auditRepository,
            LoanIdempotencyRepository idempotencyRepository, CurrentIdentity currentIdentity) {
        this(repository, auditRepository, idempotencyRepository, null, currentIdentity);
    }

    public LoanProductPage list(Integer requestedLimit, String cursor) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        validateLimit(limit);

        String decodedCursor = cursor == null || cursor.isBlank() ? null : decodeCursor(cursor);
        List<LoanProduct> candidates = repository.findActiveAfter(decodedCursor, limit + 1);
        boolean hasNext = candidates.size() > limit;
        List<LoanProduct> pageItems = hasNext ? candidates.subList(0, limit) : candidates;
        String nextCursor = hasNext ? encodeCursor(pageItems.getLast().code()) : null;
        return new LoanProductPage(pageItems, nextCursor, hasNext);
    }

    @Transactional
    public LoanProduct create(CreateLoanProductCommand command) {
        AuditActor actor = AuditActor.from(currentIdentity, "LOAN_PRODUCT_ADMIN");
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        String fingerprint = fingerprintFor(command, code);
        IdempotencyRecord requested = new IdempotencyRecord(
                actor.id(), CREATE_OPERATION, command.idempotencyKey(), fingerprint,
                null, IdempotencyStatus.IN_PROGRESS);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(
                requested.callerScope(), requested.operation(), requested.idemKey());
        if (existing.isPresent()) {
            return replay(existing.get(), requested, code);
        }
        if (repository.findByCode(code).isPresent()) {
            throw new ConflictException("Loan product code already exists");
        }
        if (!idempotencyRepository.createIfAbsent(requested)) {
            return replay(requireIdempotencyRecord(requested), requested, code);
        }

        Instant now = Instant.now();
        LoanProduct product = new LoanProduct(
                UUID.randomUUID(), code, command.interestType(), command.annualRate(),
                command.minAmount(), command.maxAmount(), command.active(), 1, now);
        repository.save(product);
        UUID eventId = UUID.randomUUID();
        auditRepository.append(new LoanAuditEntry(
                UUID.randomUUID(), eventId, "LoanProduct", product.id().toString(), product.version(),
                "CREATE", "SUCCESS", actor.id(), actor.role(), command.correlationId(), command.requestId(),
                "PRODUCT-" + product.code(), null,
                LoanLedgerEventJson.serializeObject(Map.of(
                        "source", "loan-service", "schemaVersion", 1, "code", product.code(),
                        "interestType", product.interestType().name())), now));
        if (outboxRepository != null) {
            outboxRepository.append(new LoanOutboxEntry(
                    eventId, product.id(), product.version(), "LoanProductCreated", 1, "PRODUCT-" + product.code(),
                    LoanLedgerEventJson.serializeObject(Map.ofEntries(
                            Map.entry("eventId", eventId.toString()), Map.entry("eventType", "LoanProductCreated"),
                            Map.entry("schemaVersion", 1), Map.entry("aggregateId", product.id().toString()),
                            Map.entry("aggregateVersion", product.version()), Map.entry("code", product.code()),
                            Map.entry("interestType", product.interestType().name()),
                            Map.entry("annualRate", product.annualRate().toPlainString()),
                            Map.entry("minAmount", product.minAmount().toPlainString()),
                            Map.entry("maxAmount", product.maxAmount().toPlainString()),
                            Map.entry("active", product.active()), Map.entry("updatedAt", product.updatedAt().toString()))), now));
        }
        idempotencyRepository.markSucceeded(requested, product.id().toString());
        return product;
    }

    public String decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.isBlank()) {
                throw new IllegalArgumentException("cursor must not be blank");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor must be a valid opaque cursor", exception);
        }
    }

    private static String encodeCursor(String code) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(code.getBytes(StandardCharsets.UTF_8));
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private LoanProduct replay(IdempotencyRecord existing, IdempotencyRecord requested, String code) {
        if (!requested.requestFingerprint().equals(existing.requestFingerprint())) {
            throw new ConflictException("Idempotency key was reused with a different payload");
        }
        if (existing.resultRef() == null || existing.resultRef().isBlank()) {
            throw new ConflictException("Idempotency request is being processed");
        }
        return repository.findByCode(code)
                .orElseThrow(() -> new ConflictException("Idempotency result is unavailable"));
    }

    private IdempotencyRecord requireIdempotencyRecord(IdempotencyRecord requested) {
        return idempotencyRepository.findByKey(
                requested.callerScope(), requested.operation(), requested.idemKey())
                .orElseThrow(() -> new ConflictException("Idempotency request is being processed"));
    }

    private static String fingerprintFor(CreateLoanProductCommand command, String normalizedCode) {
        String canonical = String.join("|", normalizedCode, command.interestType().name(),
                command.annualRate().toPlainString(), command.minAmount().toPlainString(),
                command.maxAmount().toPlainString(), Boolean.toString(command.active()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
