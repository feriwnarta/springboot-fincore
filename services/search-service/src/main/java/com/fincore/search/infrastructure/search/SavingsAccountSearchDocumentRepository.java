package com.fincore.search.infrastructure.search;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SavingsAccountSearchDocumentRepository
        extends ElasticsearchRepository<SavingsAccountSearchDocument, String> {

    List<SavingsAccountSearchDocument> findByCustomerIdOrderByAccountNumber(String customerId, Pageable pageable);

    List<SavingsAccountSearchDocument> findByCustomerIdAndStatusOrderByAccountNumber(
            String customerId, String status, Pageable pageable);

    List<SavingsAccountSearchDocument> findAllByOrderByAccountNumber(Pageable pageable);

    List<SavingsAccountSearchDocument> findByStatusOrderByAccountNumber(String status, Pageable pageable);
}
