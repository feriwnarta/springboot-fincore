package com.fincore.search.infrastructure.search;

import static com.fincore.search.domain.model.LoanEventProjection.isLifecycleEvent;
import static com.fincore.search.domain.model.LoanEventProjection.lifecyclePrecedence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "loan_activity_views")
public class LoanActivityDocument {

    private static final Comparator<Activity> ACTIVITY_ORDER = Comparator
            .comparing((Activity activity) -> !isLifecycleEvent(activity.type()))
            .thenComparingInt(activity -> lifecyclePrecedence(activity.type()))
            .thenComparing(Activity::at)
            .thenComparing(activity -> "RepaymentJournalPosted".equals(activity.type()))
            .thenComparing(Activity::eventId);

    @Id
    private String loanId;
    private String customerId;
    // Legacy lastAggregateVersion mixed loan and repayment versions. Use only the loan lifecycle watermark.
    private long lastLoanAggregateVersion;
    private String lastLoanEventType;
    private String currentStatus;
    private List<Activity> activities = new ArrayList<>();

    protected LoanActivityDocument() {
    }

    public LoanActivityDocument(String loanId) {
        this.loanId = loanId;
    }

    public String getLoanId() { return loanId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getLastLoanAggregateVersion() { return lastLoanAggregateVersion; }
    public String getCurrentStatus() { return currentStatus; }
    public List<Activity> getActivities() { return activities; }
    public void setCurrentStatus(String value) { this.currentStatus = value; }

    public int lastLoanPrecedence() {
        if (lastLoanEventType != null) {
            return lifecyclePrecedence(lastLoanEventType);
        }
        // Earlier Mongo documents did not keep the lifecycle type separately from activity status.
        int historyPrecedence = activities.stream()
                .mapToInt(activity -> lifecyclePrecedence(activity.type()))
                .max()
                .orElse(0);
        return Math.max(historyPrecedence, lifecyclePrecedence(currentStatus));
    }

    public void recordLoanLifecycle(long version, String eventType) {
        lastLoanAggregateVersion = version;
        lastLoanEventType = eventType;
    }

    public boolean containsEvent(String eventId) {
        return activities.stream().anyMatch(activity -> activity.eventId().equals(eventId));
    }

    public boolean addActivity(String eventId, String type, Instant at) {
        var entry = new Activity(eventId, type, at);
        activities.add(entry);
        activities.sort(ACTIVITY_ORDER);
        if (activities.size() > 100) {
            activities = new ArrayList<>(activities.subList(activities.size() - 100, activities.size()));
        }
        return entry.equals(activities.getLast());
    }

    public record Activity(String eventId, String type, Instant at) {
    }
}
