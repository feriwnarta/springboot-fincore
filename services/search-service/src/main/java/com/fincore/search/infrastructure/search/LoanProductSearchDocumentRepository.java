package com.fincore.search.infrastructure.search;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface LoanProductSearchDocumentRepository
        extends ElasticsearchRepository<LoanProductSearchDocument, String> {

    List<LoanProductSearchDocument> findByActiveTrueOrderByCode(Pageable pageable);
}
