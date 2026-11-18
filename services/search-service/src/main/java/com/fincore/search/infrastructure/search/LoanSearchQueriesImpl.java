package com.fincore.search.infrastructure.search;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

@RequiredArgsConstructor
class LoanSearchQueriesImpl implements LoanSearchQueries {

    private final ElasticsearchOperations operations;

    @Override
    public List<LoanSearchDocument> findMatching(Optional<String> customerId, String query, String status, String productCode,
            Pageable pageable) {
        Criteria criteria = Criteria.and();
        // IN on a keyword field becomes an exact Elasticsearch terms query.
        customerId.ifPresent(owner -> criteria.and(Criteria.where("customerId").in(owner)));
        if (status != null) {
            criteria.and(Criteria.where("status").in(status));
        }
        if (productCode != null) {
            criteria.and(Criteria.where("productCode").in(productCode));
        }
        if (query != null && !query.isBlank()) {
            criteria.subCriteria(Criteria.where("description").contains(query).or("productCode").contains(query));
        }
        return operations.search(new CriteriaQuery(criteria, pageable), LoanSearchDocument.class)
                .stream().map(SearchHit::getContent).toList();
    }
}
