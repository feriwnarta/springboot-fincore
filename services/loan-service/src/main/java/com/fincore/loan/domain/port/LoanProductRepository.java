package com.fincore.loan.domain.port;

import java.util.List;
import java.util.Optional;

import com.fincore.loan.domain.model.LoanProduct;

public interface LoanProductRepository {

    List<LoanProduct> findActiveAfter(String codeCursor, int limit);

    Optional<LoanProduct> findActiveByCode(String code);

    Optional<LoanProduct> findByCode(String code);

    void save(LoanProduct product);
}
