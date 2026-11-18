package com.fincore.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import com.fincore.search.infrastructure.search.LoanSearchDocumentRepository;
import com.fincore.search.infrastructure.search.LoanActivityDocumentRepository;

@ActiveProfiles("test")
@SpringBootTest(classes = SearchServiceApplication.class, properties = {
        "FINCORE_LOAN_EVENTS_TOPIC=test.loan.events",
        "FINCORE_LEDGER_EVENTS_TOPIC=test.ledger.events"
})
class SearchServiceApplicationTests {

    @MockitoBean
    LoanSearchDocumentRepository loanSearchDocumentRepository;

    @MockitoBean
    LoanActivityDocumentRepository loanActivityDocumentRepository;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    @Test
    void projectionSubscribesToConfiguredLoanAndLedgerTopics() {
        assertThat(listeners.getListenerContainers()).singleElement().satisfies(container ->
                assertThat(container.getContainerProperties().getTopics())
                        .containsExactlyInAnyOrder("test.loan.events", "test.ledger.events"));
    }
}
