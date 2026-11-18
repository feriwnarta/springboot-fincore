package com.fincore.loan.application.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fincore.loan.domain.model.LoanApplication;
import com.fincore.loan.domain.model.LoanFunding;
import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.domain.port.LoanApplicationRepository;
import com.fincore.loan.domain.port.LoanFundingRepository;
import com.fincore.loan.domain.port.LoanProductRepository;
import com.fincore.loan.domain.port.LoanRepaymentScheduleRepository;
import com.fincore.loan.domain.service.RepaymentScheduleGenerator;
import com.fincore.platform.web.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepaymentScheduleService {

    private final LoanFundingRepository fundingRepository;
    private final LoanApplicationRepository applicationRepository;
    private final LoanProductRepository productRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final RepaymentScheduleGenerator scheduleGenerator;

    @Transactional
    public void generateForFundedLoan(UUID loanId, Instant fundedAt) {
        LoanFunding funding = fundingRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Funded loan was not found"));
        LoanApplication application = applicationRepository.findById(funding.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application was not found"));
        LoanProduct product = productRepository.findActiveByCode(funding.productCode())
                .orElseThrow(() -> new ResourceNotFoundException("Active loan product was not found"));
        scheduleRepository.saveIfAbsent(loanId, scheduleGenerator.generate(
                product,
                funding.principalAmount(),
                application.tenorMonths(),
                fundedAt.atZone(ZoneOffset.UTC).toLocalDate().plusMonths(1)));
    }
}
