package com.fincore.search.infrastructure.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface LoanSearchDocumentRepository extends ElasticsearchRepository<LoanSearchDocument, String>,
        LoanSearchQueries {
}
