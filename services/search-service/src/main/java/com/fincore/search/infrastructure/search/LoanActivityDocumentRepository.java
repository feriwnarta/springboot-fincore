package com.fincore.search.infrastructure.search;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface LoanActivityDocumentRepository extends MongoRepository<LoanActivityDocument, String> {

    Optional<LoanActivityDocument> findByLoanIdAndCustomerId(String loanId, String customerId);
}
