package com.fincore.search.application.service;

import static com.fincore.search.domain.model.LoanEventProjection.isLifecycleEvent;
import static com.fincore.search.domain.model.LoanEventProjection.lifecyclePrecedence;
import static com.fincore.search.infrastructure.search.LoanSearchDocument.LEGACY_UNKNOWN_CUSTOMER_ID;

import com.fincore.search.domain.model.LoanEventProjection;
import com.fincore.search.infrastructure.search.LoanActivityDocument;
import com.fincore.search.infrastructure.search.LoanActivityDocumentRepository;
import com.fincore.search.infrastructure.search.LoanSearchDocument;
import com.fincore.search.infrastructure.search.LoanSearchDocumentRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanProjectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoanProjectionService.class);

    private final LoanSearchDocumentRepository searchRepository;
    private final LoanActivityDocumentRepository activityRepository;

    public void project(LoanEventProjection event) {
        String loanId = event.loanId().toString();
        LoanActivityDocument activity = activityRepository.findById(loanId)
                .orElseGet(() -> new LoanActivityDocument(loanId));
        String eventId = event.eventId().toString();
        boolean lifecycle = isLifecycleEvent(event.eventType());
        String eventCustomerId = event.customerId();
        if (conflictingOwner(activity.getCustomerId(), eventCustomerId)) {
            logOwnershipConflict(event);
            return;
        }

        // A replay can repair Elasticsearch even when Mongo has already committed the event.
        if (lifecycle && !projectSearch(event, activity)) {
            return;
        }
        if (activity.containsEvent(eventId)) {
            LOGGER.warn("loan_activity_duplicate_event_ignored loanId={} eventId={}", loanId, eventId);
            return;
        }
        if (lifecycle) {
            activity.recordLoanLifecycle(event.aggregateVersion(), event.eventType());
        }
        if (eventCustomerId != null) {
            activity.setCustomerId(eventCustomerId);
        }
        if (activity.addActivity(eventId, event.eventType(), event.occurredAt())) {
            activity.setCurrentStatus(event.status());
        }
        activityRepository.save(activity);
    }

    private boolean projectSearch(LoanEventProjection event, LoanActivityDocument activity) {
        String loanId = activity.getLoanId();
        LoanSearchDocument current = searchRepository.findById(loanId).orElse(null);
        int indexOrder = current == null ? 1
                : compareLifecycle(event, current.getAggregateVersion(), lifecyclePrecedence(current.getStatus()));
        if (indexOrder < 0
                || compareLifecycle(event, activity.getLastLoanAggregateVersion(), activity.lastLoanPrecedence()) < 0) {
            LOGGER.warn("loan_projection_stale_event_ignored loanId={} eventId={} aggregateVersion={}",
                    loanId, event.eventId(), event.aggregateVersion());
            return false;
        }

        String indexedCustomerId = current == null ? null : knownCustomerId(current.getCustomerId());
        if (conflictingOwner(indexedCustomerId, event.customerId())) {
            logOwnershipConflict(event);
            return false;
        }

        String customerId = event.customerId();
        if (customerId == null) {
            customerId = indexedCustomerId == null ? knownCustomerId(activity.getCustomerId()) : indexedCustomerId;
        }
        if (indexOrder > 0 || (indexedCustomerId == null && customerId != null)) {
            int tenorMonths = event.tenorMonths() > 0
                    ? event.tenorMonths()
                    : current == null ? 0 : current.getTenorMonths();
            String businessReference = event.businessReference() != null
                    ? event.businessReference()
                    : current == null ? null : current.getBusinessReference();
            java.time.Instant createdAt = event.createdAt() != null
                    ? event.createdAt()
                    : current == null ? event.occurredAt() : current.getCreatedAt();
            searchRepository.save(new LoanSearchDocument(
                    loanId, customerId, event.productCode(), event.status(), event.principalAmount(),
                    event.description(), tenorMonths, businessReference, createdAt,
                    event.aggregateVersion(), event.occurredAt()));
        }
        return true;
    }

    private static String knownCustomerId(String customerId) {
        return customerId == null || customerId.isBlank() || LEGACY_UNKNOWN_CUSTOMER_ID.equals(customerId)
                ? null : customerId;
    }

    private static int compareLifecycle(LoanEventProjection incoming, long currentVersion, int currentPrecedence) {
        int stageOrder = Integer.compare(lifecyclePrecedence(incoming.eventType()), currentPrecedence);
        // Legacy activity may retain a lifecycle stage without a reliable loan version.
        if (currentVersion == 0 && stageOrder < 0) {
            return stageOrder;
        }
        int versionOrder = Long.compare(incoming.aggregateVersion(), currentVersion);
        return versionOrder == 0 ? stageOrder : versionOrder;
    }

    private static boolean conflictingOwner(String existing, String incoming) {
        return existing != null && incoming != null && !existing.equals(incoming);
    }

    private static void logOwnershipConflict(LoanEventProjection event) {
        LOGGER.warn("loan_projection_conflicting_owner_ignored loanId={} eventId={}", event.loanId(), event.eventId());
    }
}
