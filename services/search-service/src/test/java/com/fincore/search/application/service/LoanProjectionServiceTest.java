package com.fincore.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fincore.search.domain.model.LoanEventProjection;
import com.fincore.search.infrastructure.messaging.LoanEventProjectionParser;
import com.fincore.search.infrastructure.search.LoanActivityDocument;
import com.fincore.search.infrastructure.search.LoanActivityDocumentRepository;
import com.fincore.search.infrastructure.search.LoanSearchDocument;
import com.fincore.search.infrastructure.search.LoanSearchDocumentRepository;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class LoanProjectionServiceTest {

    private final LoanSearchDocumentRepository searchRepository = Mockito.mock(LoanSearchDocumentRepository.class);
    private final LoanActivityDocumentRepository activityRepository = Mockito.mock(LoanActivityDocumentRepository.class);
    private final LoanProjectionService service = new LoanProjectionService(searchRepository, activityRepository);

    @Test
    void ignoresStaleSearchEvent() {
        UUID loanId = UUID.randomUUID();
        when(searchRepository.findById(loanId.toString())).thenReturn(Optional.of(new LoanSearchDocument(
                loanId.toString(), "customer-1", "P-1", "FUNDED", new BigDecimal("100"), "funded", 5,
                Instant.parse("2026-11-17T00:00:00Z"))));
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.empty());

        service.project(new LoanEventProjection(loanId, "customer-1", "P-1", "APPROVED", new BigDecimal("100"),
                "approved", 4, Instant.parse("2026-11-17T00:00:00Z"), UUID.randomUUID(), "LoanApproved"));

        verify(searchRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LoanApproved", "LoanRejected"})
    void lateLegacyDecisionAtFundingVersionCannotReplaceFunding(String type) {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));
        var funded = event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0);
        service.project(funded);
        when(searchRepository.findById(loanId.toString())).thenReturn(Optional.of(new LoanSearchDocument(
                loanId.toString(), funded.customerId(), funded.productCode(), funded.status(), funded.principalAmount(),
                funded.description(), funded.aggregateVersion(), funded.occurredAt())));

        var lateDecision = new LoanEventProjectionParser().parse("""
                {"applicationId":"%s","eventType":"%s","aggregateVersion":2,
                 "productCode":"P-1","principalAmount":"10"}
                """.formatted(loanId, type), Instant.EPOCH.plusSeconds(60)).orElseThrow();
        service.project(lateDecision);

        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getCurrentStatus()).isEqualTo("FUNDED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanFunded");
        verify(searchRepository).save(assertArg(document -> assertThat(document.getStatus()).isEqualTo("FUNDED")));
        verify(activityRepository).save(activity);
    }

    @Test
    void fundingAtTheSameVersionAdvancesActivityEvenWithAnEarlierTimestamp() {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));
        service.project(event(loanId, null, 2, "LoanApproved", "APPROVED", 10));
        when(searchRepository.findById(loanId.toString())).thenReturn(Optional.of(new LoanSearchDocument(
                loanId.toString(), null, "P-1", "APPROVED", BigDecimal.TEN, "approved", 2, Instant.EPOCH.plusSeconds(10))));

        service.project(event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0));

        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getCurrentStatus()).isEqualTo("FUNDED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanApproved", "LoanFunded");
        verify(searchRepository).save(argThat(document -> "FUNDED".equals(document.getStatus())
                && "customer-1".equals(document.getCustomerId())));
    }

    @Test
    void indexedFundingBlocksAnEqualVersionDecisionWhenActivityIsMissing() {
        UUID loanId = UUID.randomUUID();
        when(searchRepository.findById(loanId.toString())).thenReturn(Optional.of(new LoanSearchDocument(
                loanId.toString(), "customer-1", "P-1", "FUNDED", BigDecimal.TEN, "funded", 2, Instant.EPOCH)));

        service.project(event(loanId, null, 2, "LoanApproved", "APPROVED", 10));

        verify(searchRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void fundingInLegacyActivityBlocksADecisionWhenItsLifecycleVersionIsMissing() {
        UUID loanId = UUID.randomUUID();
        var legacy = new LoanActivityDocument(loanId.toString());
        legacy.setCustomerId("customer-1");
        legacy.addActivity(UUID.randomUUID().toString(), "LoanFunded", Instant.EPOCH);
        legacy.addActivity(UUID.randomUUID().toString(), "RepaymentJournalPosted", Instant.EPOCH.plusSeconds(1));
        legacy.setCurrentStatus("REPAYMENT_POSTED");
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(legacy));

        service.project(event(loanId, null, 2, "LoanApproved", "APPROVED", 10));

        assertThat(legacy.getCurrentStatus()).isEqualTo("REPAYMENT_POSTED");
        assertThat(legacy.getActivities()).hasSize(2);
        verify(searchRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void equalFundingAlreadyInSearchCanStillCompleteActivityProjection() {
        UUID loanId = UUID.randomUUID();
        when(searchRepository.findById(loanId.toString())).thenReturn(Optional.of(new LoanSearchDocument(
                loanId.toString(), "customer-1", "P-1", "FUNDED", BigDecimal.TEN, "funded", 2, Instant.EPOCH)));

        service.project(event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0));

        verify(searchRepository, never()).save(any());
        verify(activityRepository).save(assertArg(activity -> {
            assertThat(activity.getCurrentStatus()).isEqualTo("FUNDED");
            assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        }));
    }

    @Test
    void replayRestoresADeletedIndexDocumentWithoutDuplicatingActivity() {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));
        var indexed = trackSearchWrites(loanId);
        var funded = event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0);
        service.project(funded);
        service.project(event(loanId, null, 1, "RepaymentJournalPosted", "REPAYMENT_POSTED", 1));
        assertThat(indexed.get()).isNotNull();
        indexed.set(null);

        service.project(funded);
        service.project(funded);

        assertThat(indexed.get()).isNotNull().satisfies(document -> {
            assertThat(document.getLoanId()).isEqualTo(loanId.toString());
            assertThat(document.getStatus()).isEqualTo("FUNDED");
            assertThat(document.getAggregateVersion()).isEqualTo(2);
            assertThat(document.getCustomerId()).isEqualTo("customer-1");
        });
        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getCurrentStatus()).isEqualTo("REPAYMENT_POSTED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanFunded", "RepaymentJournalPosted");
        verify(searchRepository, times(2)).save(any());
        verify(activityRepository, times(2)).save(activity);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void staleLifecycleDuplicateCannotRestoreADeletedIndexDocument(long approvalVersion) {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));
        var indexed = trackSearchWrites(loanId);
        var approval = event(loanId, null, approvalVersion, "LoanApproved", "APPROVED", 0);
        service.project(approval);
        service.project(event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 1));
        indexed.set(null);

        service.project(approval);

        assertThat(indexed.get()).isNull();
        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getCurrentStatus()).isEqualTo("FUNDED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanApproved", "LoanFunded");
        verify(searchRepository, times(2)).save(any());
        verify(activityRepository, times(2)).save(activity);
    }

    @Test
    void restoresKnownCustomerOwnershipWhenAReplayOmitsTheCustomerId() {
        UUID loanId = UUID.randomUUID();
        var approval = event(loanId, null, 2, "LoanApproved", "APPROVED", 0);
        var activity = new LoanActivityDocument(loanId.toString());
        activity.setCustomerId("customer-1");
        activity.recordLoanLifecycle(2, approval.eventType());
        activity.addActivity(approval.eventId().toString(), approval.eventType(), approval.occurredAt());
        activity.setCurrentStatus(approval.status());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));

        service.project(approval);

        verify(searchRepository).save(assertArg(document -> {
            assertThat(document.getStatus()).isEqualTo("APPROVED");
            assertThat(document.getCustomerId()).isEqualTo("customer-1");
        }));
        verify(activityRepository, never()).save(any());
    }

    private AtomicReference<LoanSearchDocument> trackSearchWrites(UUID loanId) {
        var indexed = new AtomicReference<LoanSearchDocument>();
        when(searchRepository.findById(loanId.toString())).thenAnswer(invocation -> Optional.ofNullable(indexed.get()));
        when(searchRepository.save(any(LoanSearchDocument.class))).thenAnswer(invocation -> {
            LoanSearchDocument saved = invocation.getArgument(0);
            indexed.set(saved);
            return saved;
        });
        return indexed;
    }

    @Test
    void recordsOwnerOnActivityWhenEventSuppliesIt() {
        UUID loanId = UUID.randomUUID();
        service.project(event(loanId, "customer-1", 1, "LoanFunded"));

        verify(activityRepository).save(assertArg(activity ->
                assertThat(activity.getCustomerId()).isEqualTo("customer-1")));
    }

    @Test
    void backfillsLegacyActivityAndKeepsOwnershipWhenLaterEventOmitsIt() {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));

        service.project(event(loanId, "customer-1", 1, "LoanFunded"));
        service.project(event(loanId, null, 2, "RepaymentJournalPosted"));

        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getActivities()).hasSize(2);
    }

    @Test
    void fundingAndIndependentRepaymentsKeepTheirHistoryWithoutRegressingCurrentStatus() {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));

        var funded = event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0);
        var confirmation = event(loanId, null, 1, "RepaymentConfirmed", "REPAYMENT_CONFIRMED", 1);
        var posted = event(loanId, null, 1, "RepaymentJournalPosted", "REPAYMENT_POSTED", 2);
        var nextConfirmation = event(loanId, null, 1, "RepaymentConfirmed", "REPAYMENT_CONFIRMED", 3);
        service.project(funded);
        service.project(posted);
        service.project(confirmation);
        service.project(confirmation);
        service.project(event(loanId, "customer-1", 1, "LoanApproved", "APPROVED", 4));
        service.project(event(loanId, "customer-1", 2, "LoanRejected", "LoanRejected", 5));

        assertThat(activity.getCurrentStatus()).isEqualTo("REPAYMENT_POSTED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanFunded", "RepaymentConfirmed", "RepaymentJournalPosted");

        service.project(nextConfirmation);
        when(searchRepository.findById(loanId.toString())).thenReturn(Optional.of(new LoanSearchDocument(
                loanId.toString(), funded.customerId(), funded.productCode(), funded.status(), funded.principalAmount(),
                funded.description(), funded.aggregateVersion(), funded.occurredAt())));
        service.project(funded);
        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getLastLoanAggregateVersion()).isEqualTo(2);
        assertThat(activity.getCurrentStatus()).isEqualTo("REPAYMENT_CONFIRMED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanFunded", "RepaymentConfirmed", "RepaymentJournalPosted", "RepaymentConfirmed");
        verify(activityRepository, times(4)).save(activity);
        verify(searchRepository).save(any());
    }

    @Test
    void repaymentBeforeFundingPreservesTheLaterStatusAndSetsOwnershipWhenFundingArrives() {
        UUID loanId = UUID.randomUUID();
        var activity = new LoanActivityDocument(loanId.toString());
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));

        service.project(event(loanId, null, 1, "RepaymentJournalPosted", "REPAYMENT_POSTED", 2));
        service.project(event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0));

        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getCurrentStatus()).isEqualTo("REPAYMENT_POSTED");
        assertThat(activity.getActivities()).extracting(LoanActivityDocument.Activity::type)
                .containsExactly("LoanFunded", "RepaymentJournalPosted");
    }

    @Test
    void legacyMixedVersionCannotBlockFundingOrItsOwnership() {
        UUID loanId = UUID.randomUUID();
        var conversions = MongoCustomConversions.create(adapter -> {});
        var mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        var activity = converter.read(LoanActivityDocument.class, new Document("_id", loanId.toString())
                .append("lastAggregateVersion", 5L));
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(activity));

        service.project(event(loanId, "customer-1", 2, "LoanFunded", "FUNDED", 0));

        assertThat(activity.getCustomerId()).isEqualTo("customer-1");
        assertThat(activity.getCurrentStatus()).isEqualTo("FUNDED");
    }

    @Test
    void leavesOwnershipUnsetWhenEventOmitsIt() {
        service.project(event(UUID.randomUUID(), null, 1, "LoanSubmitted"));

        verify(activityRepository).save(assertArg(activity ->
                assertThat(activity.getCustomerId()).isNull()));
        verify(searchRepository).save(assertArg(document -> assertThat(document.getCustomerId()).isNull()));
    }

    @Test
    void conflictingOwnershipCannotReplaceAnExistingOwnerOrAppendToTheirActivity() {
        UUID loanId = UUID.randomUUID();
        var existing = new LoanActivityDocument(loanId.toString());
        existing.setCustomerId("customer-1");
        when(activityRepository.findById(loanId.toString())).thenReturn(Optional.of(existing));

        service.project(event(loanId, "customer-2", 2, "LoanFunded"));

        assertThat(existing.getCustomerId()).isEqualTo("customer-1");
        assertThat(existing.getActivities()).isEmpty();
        verify(activityRepository, never()).save(any());
        verify(searchRepository, never()).save(any());
    }

    private static LoanEventProjection event(
            UUID loanId, String customerId, long version, String type, String status, long seconds) {
        return new LoanEventProjection(loanId, customerId, "P-1", status, BigDecimal.TEN, "loan",
                version, Instant.EPOCH.plusSeconds(seconds), UUID.randomUUID(), type);
    }

    private static LoanEventProjection event(UUID loanId, String customerId, long version, String type) {
        return new LoanEventProjection(loanId, customerId, "P-1", "FUNDED", BigDecimal.TEN, "loan",
                version, Instant.EPOCH, UUID.randomUUID(), type);
    }
}
