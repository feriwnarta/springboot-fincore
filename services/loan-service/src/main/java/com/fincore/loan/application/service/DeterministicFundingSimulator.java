package com.fincore.loan.application.service;

import com.fincore.loan.application.command.RequestFundingCommand;
import com.fincore.loan.domain.model.LoanApplication;

import org.springframework.stereotype.Component;

@Component
public class DeterministicFundingSimulator implements FundingSimulator {

    @Override
    public FundingSimulationResult simulate(LoanApplication application, RequestFundingCommand command) {
        return command.simulateFailure()
                ? new FundingSimulationResult(false, "SIMULATED_FAILURE")
                : new FundingSimulationResult(true, "SIMULATED_SUCCESS");
    }
}
