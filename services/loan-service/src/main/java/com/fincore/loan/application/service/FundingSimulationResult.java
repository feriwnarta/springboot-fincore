package com.fincore.loan.application.service;

public record FundingSimulationResult(boolean successful, String reason) {

    public FundingSimulationResult {
        if (reason == null || reason.isBlank()) {
            reason = successful ? "SIMULATED_SUCCESS" : "SIMULATED_FAILURE";
        }
    }
}
