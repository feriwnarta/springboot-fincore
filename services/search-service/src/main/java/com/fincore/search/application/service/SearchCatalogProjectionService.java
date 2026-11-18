package com.fincore.search.application.service;

import com.fincore.search.infrastructure.messaging.CatalogEventProjection;
import com.fincore.search.infrastructure.search.LoanProductSearchDocument;
import com.fincore.search.infrastructure.search.LoanProductSearchDocumentRepository;
import com.fincore.search.infrastructure.search.SavingsAccountSearchDocument;
import com.fincore.search.infrastructure.search.SavingsAccountSearchDocumentRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchCatalogProjectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchCatalogProjectionService.class);

    private final LoanProductSearchDocumentRepository productRepository;
    private final SavingsAccountSearchDocumentRepository accountRepository;

    public void project(CatalogEventProjection event) {
        if (event.type() == CatalogEventProjection.Type.LOAN_PRODUCT) {
            productRepository.findById(event.aggregateId().toString())
                    .filter(current -> current.getVersion() >= event.aggregateVersion())
                    .ifPresentOrElse(current -> LOGGER.debug("catalog_projection_stale_event_ignored aggregateId={}",
                            event.aggregateId()), () -> productRepository.save(new LoanProductSearchDocument(
                                    event.aggregateId().toString(), event.code(), event.interestType(), event.annualRate(),
                                    event.minAmount(), event.maxAmount(), event.active(), event.aggregateVersion(),
                                    event.updatedAt())));
            return;
        }
        accountRepository.findById(event.aggregateId().toString())
                .filter(current -> current.getVersion() >= event.aggregateVersion())
                .ifPresentOrElse(current -> LOGGER.debug("catalog_projection_stale_event_ignored aggregateId={}",
                        event.aggregateId()), () -> accountRepository.save(new SavingsAccountSearchDocument(
                                event.aggregateId().toString(), event.accountNumber(), event.customerId(),
                                event.currency(), event.status(), event.currentBalance(), event.aggregateVersion(),
                                event.createdAt(), event.updatedAt())));
    }
}
