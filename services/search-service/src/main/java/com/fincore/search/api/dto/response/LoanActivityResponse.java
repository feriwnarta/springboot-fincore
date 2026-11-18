package com.fincore.search.api.dto.response;

import java.time.Instant;
import java.util.List;

public record LoanActivityResponse(String loanId, String currentStatus, List<Activity> activities) {

    public record Activity(String eventId, String type, Instant at) {
    }
}
