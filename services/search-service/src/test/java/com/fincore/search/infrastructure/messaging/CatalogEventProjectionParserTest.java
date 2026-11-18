package com.fincore.search.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CatalogEventProjectionParserTest {

    private final CatalogEventProjectionParser parser = new CatalogEventProjectionParser();

    @Test
    void parsesLoanProductCreatedEvent() {
        var event = parser.parse("""
                {"eventId":"11111111-1111-1111-1111-111111111111","eventType":"LoanProductCreated",
                 "aggregateId":"22222222-2222-2222-2222-222222222222","aggregateVersion":1,
                 "payload":{"code":"KPR-001","interestType":"FLAT","annualRate":"12.5",
                 "minAmount":"1000","maxAmount":"100000","active":true,"updatedAt":"2026-11-18T10:00:00Z"}}
                """, Instant.parse("2026-11-18T10:01:00Z"));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(CatalogEventProjection.Type.LOAN_PRODUCT);
        assertThat(event.orElseThrow().code()).isEqualTo("KPR-001");
        assertThat(event.orElseThrow().active()).isTrue();
    }

    @Test
    void parsesSavingsAccountStateEvents() {
        var event = parser.parse("""
                {"eventId":"33333333-3333-3333-3333-333333333333","eventType":"SavingsAccountOpened",
                 "aggregateId":"44444444-4444-4444-4444-444444444444","aggregateVersion":1,
                 "payload":{"accountNumber":"SAV-001","customerId":"customer-001","currency":"IDR",
                 "status":"ACTIVE","currentBalance":"0","createdAt":"2026-11-18T10:00:00Z",
                 "updatedAt":"2026-11-18T10:00:00Z"}}
                """, Instant.parse("2026-11-18T10:01:00Z"));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(CatalogEventProjection.Type.SAVINGS_ACCOUNT);
        assertThat(event.orElseThrow().customerId()).isEqualTo("customer-001");
        assertThat(event.orElseThrow().accountNumber()).isEqualTo("SAV-001");
    }
}
