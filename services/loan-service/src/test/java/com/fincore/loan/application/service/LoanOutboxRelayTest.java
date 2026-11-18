package com.fincore.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fincore.loan.application.port.LoanEventPublisher;
import com.fincore.loan.application.port.LoanOutboxRelayRepository;
import com.fincore.loan.application.query.LoanOutboxMessage;
import com.fincore.loan.config.LoanOutboxRelayProperties;

import org.junit.jupiter.api.Test;

class LoanOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void publishesClaimedEventsAndMarksThemPublished() {
        FakeRepository repository = new FakeRepository(List.of(message("event-1")));
        FakePublisher publisher = new FakePublisher();

        new LoanOutboxRelay(repository, publisher, properties(true), CLOCK).runOnce();

        assertThat(publisher.published).extracting(LoanOutboxMessage::eventId)
                .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(repository.published).containsExactly("00000000-0000-0000-0000-000000000001");
        assertThat(repository.failed).isEmpty();
        assertThat(repository.claimedAt).isEqualTo(NOW);
    }

    @Test
    void recordsFailureAndContinuesWithTheNextEvent() {
        FakeRepository repository = new FakeRepository(List.of(message("event-1"), message("event-2")));
        FakePublisher publisher = new FakePublisher();
        publisher.failFor.add(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        new LoanOutboxRelay(repository, publisher, properties(true), CLOCK).runOnce();

        assertThat(publisher.published).extracting(LoanOutboxMessage::eventId)
                .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(repository.published).containsExactly("00000000-0000-0000-0000-000000000002");
        assertThat(repository.failed).hasSize(1);
        assertThat(repository.failed.getFirst()).hasSize(1000);
    }

    @Test
    void doesNothingWhenNoEventIsClaimed() {
        FakeRepository repository = new FakeRepository(List.of());
        FakePublisher publisher = new FakePublisher();

        new LoanOutboxRelay(repository, publisher, properties(true), CLOCK).runOnce();

        assertThat(publisher.published).isEmpty();
        assertThat(repository.published).isEmpty();
        assertThat(repository.failed).isEmpty();
    }

    @Test
    void doesNotClaimWhenRelayIsDisabled() {
        FakeRepository repository = new FakeRepository(List.of(message("event-1")));
        FakePublisher publisher = new FakePublisher();

        new LoanOutboxRelay(repository, publisher, properties(false), CLOCK).runOnce();

        assertThat(repository.claimCalls).isZero();
        assertThat(publisher.published).isEmpty();
    }

    private static LoanOutboxRelayProperties properties(boolean enabled) {
        return new LoanOutboxRelayProperties(enabled, 50, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    private static LoanOutboxMessage message(String eventType) {
        String suffix = eventType.substring(eventType.length() - 1);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-00000000000" + suffix);
        return new LoanOutboxMessage(
                eventId, UUID.fromString("10000000-0000-0000-0000-00000000000" + suffix), eventType,
                "APP-" + suffix, "{\"eventType\":\"" + eventType + "\"}",
                UUID.fromString("10000000-0000-0000-0000-000000000001"), NOW);
    }

    private static final class FakeRepository implements LoanOutboxRelayRepository {

        private final List<LoanOutboxMessage> messages;
        private final List<String> published = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private Instant claimedAt;
        private int claimCalls;

        private FakeRepository(List<LoanOutboxMessage> messages) {
            this.messages = messages;
        }

        @Override
        public List<LoanOutboxMessage> claimBatch(int limit, Instant now, Duration lease, UUID claimToken) {
            claimCalls++;
            claimedAt = now;
            return messages;
        }

        @Override
        public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
            published.add(eventId.toString());
        }

        @Override
        public void markFailed(UUID eventId, UUID claimToken, Instant failedAt, String errorMessage) {
            failed.add(errorMessage);
        }
    }

    private static final class FakePublisher implements LoanEventPublisher {

        private final List<LoanOutboxMessage> published = new ArrayList<>();
        private final Set<UUID> failFor = new HashSet<>();

        @Override
        public void publish(LoanOutboxMessage message) throws Exception {
            if (failFor.contains(message.eventId())) {
                throw new IOException("broker unavailable " + "x".repeat(1200));
            }
            published.add(message);
        }
    }
}
