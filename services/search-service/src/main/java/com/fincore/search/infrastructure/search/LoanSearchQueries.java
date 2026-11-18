package com.fincore.search.infrastructure.search;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface LoanSearchQueries {

    List<LoanSearchDocument> findMatching(Optional<String> customerId, String query, String status, String productCode,
            Pageable pageable);
}
