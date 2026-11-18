package com.fincore.loan.application.query;

import java.math.BigDecimal;

public record TopCustomerReportRow(String customerId, BigDecimal fundedPrincipal) {
}
