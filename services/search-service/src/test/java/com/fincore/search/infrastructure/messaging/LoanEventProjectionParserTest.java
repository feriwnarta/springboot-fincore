package com.fincore.search.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fincore.search.domain.model.LoanEventProjection;

import org.junit.jupiter.api.Test;

class LoanEventProjectionParserTest {

    private static final Instant RECORD_TIMESTAMP = Instant.parse("2026-11-17T00:00:10Z");

    private final LoanEventProjectionParser parser = new LoanEventProjectionParser();

    @Test
    void parsesRepaymentAcknowledgementAsLoanActivity() {
        UUID loanId = UUID.randomUUID();
        var projection = parse("{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"RepaymentJournalPosted\",\"aggregateId\":\"" + loanId
                + "\",\"aggregateVersion\":2,\"occurredAt\":\"2026-11-17T00:00:00Z\","
                + "\"payload\":{\"repaymentId\":\"rep-1\",\"amount\":\"10.0000\"}}" );

        assertThat(projection).isPresent();
        assertThat(projection.orElseThrow().loanId()).isEqualTo(loanId);
        assertThat(projection.orElseThrow().status()).isEqualTo("REPAYMENT_POSTED");
        assertThat(projection.orElseThrow().occurredAt()).isEqualTo(Instant.parse("2026-11-17T00:00:00Z"));
        assertThat(projection.orElseThrow().customerId()).isNull();
    }

    @Test
    void parsesConfirmedRepaymentUsingItsLoanIdAndIndependentAggregateVersion() {
        UUID loanId = UUID.randomUUID();
        var confirmation = parse("""
                {"eventType":"RepaymentConfirmed","aggregateId":"%s","aggregateVersion":1,
                 "occurredAt":"2026-11-17T00:00:00Z",
                 "payload":{"repaymentId":"%s","amount":"10.0000","principalAmount":"8.0000",
                            "interestAmount":"2.0000","currency":"IDR"}}
                """.formatted(loanId, UUID.randomUUID())).orElseThrow();

        assertThat(confirmation.loanId()).isEqualTo(loanId);
        assertThat(confirmation.aggregateVersion()).isEqualTo(1);
        assertThat(confirmation.status()).isEqualTo("REPAYMENT_CONFIRMED");
        assertThat(confirmation.customerId()).isNull();
    }

    @Test
    void legacyDecisionUsesTheStableRecordTimestampOnReplay() {
        String payload = """
                {"eventType":"LoanApproved","applicationId":"%s","aggregateVersion":2,
                 "productCode":"P-1","principalAmount":"100.00"}
                """.formatted(UUID.randomUUID());

        var first = parse(payload).orElseThrow();
        var replay = parse(payload).orElseThrow();

        assertThat(replay.occurredAt()).isEqualTo(first.occurredAt()).isEqualTo(RECORD_TIMESTAMP);
    }

    @Test
    void parsesExactOwnershipFromThePayload() {
        var projection = parse("""
                {"eventType":"LoanFunded","aggregateId":"%s",
                 "payload":{"customerId":"Customer-001","principalAmount":"100.00"}}
                """.formatted(UUID.randomUUID()));

        assertThat(projection.orElseThrow().customerId()).isEqualTo("Customer-001");
    }

    @Test
    void treatsAnExplicitNullOwnershipFieldAsMissing() {
        var projection = parse("""
                {"eventType":"LoanSubmitted","applicationId":"%s","customerId":null,"principalAmount":"100.00"}
                """.formatted(UUID.randomUUID()));

        assertThat(projection.orElseThrow().customerId()).isNull();
    }

    private Optional<LoanEventProjection> parse(String payload) {
        return parser.parse(payload, RECORD_TIMESTAMP);
    }
}
