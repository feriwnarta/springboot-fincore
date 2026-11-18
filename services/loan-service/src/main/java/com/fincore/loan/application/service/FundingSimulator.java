package com.fincore.loan.application.service;

import com.fincore.loan.application.command.RequestFundingCommand;
import com.fincore.loan.domain.model.LoanApplication;

public interface FundingSimulator {

    FundingSimulationResult simulate(LoanApplication application, RequestFundingCommand command);
}
