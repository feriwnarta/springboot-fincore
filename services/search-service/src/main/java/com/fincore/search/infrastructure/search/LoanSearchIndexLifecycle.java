package com.fincore.search.infrastructure.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Profile("!test")
public class LoanSearchIndexLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoanSearchIndexLifecycle.class);

    private final ElasticsearchOperations operations;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        ensure(LoanSearchDocument.class, "fincore-loans-v1");
        ensure(LoanProductSearchDocument.class, "fincore-loan-products-v1");
        ensure(SavingsAccountSearchDocument.class, "fincore-savings-accounts-v1");
    }

    private void ensure(Class<?> documentType, String indexName) {
        try {
            var indexOperations = operations.indexOps(documentType);
            if (indexOperations.exists()) {
                LOGGER.info("search_index_ready index={} created=false", indexName);
                return;
            }
            boolean created = indexOperations.createWithMapping();
            LOGGER.info("search_index_ready index={} created={}", indexName, created);
        } catch (RuntimeException exception) {
            LOGGER.error("search_index_initialization_failed index={}", indexName, exception);
        }
    }
}
