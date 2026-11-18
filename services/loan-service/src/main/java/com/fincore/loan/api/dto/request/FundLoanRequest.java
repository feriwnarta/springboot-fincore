package com.fincore.loan.api.dto.request;

public record FundLoanRequest(Boolean simulateFailure) {

    public boolean shouldSimulateFailure() {
        return Boolean.TRUE.equals(simulateFailure);
    }
}
