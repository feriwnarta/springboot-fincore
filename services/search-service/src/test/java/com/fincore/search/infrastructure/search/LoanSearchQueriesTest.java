package com.fincore.search.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Field;
import org.springframework.data.elasticsearch.core.query.Query;

class LoanSearchQueriesTest {

    private static final String CUSTOMER_ID = "Customer-001";
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final LoanSearchQueries repository = new LoanSearchQueriesImpl(operations);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubEmptyResults() {
        SearchHits<LoanSearchDocument> hits = mock(SearchHits.class);
        when(hits.stream()).thenAnswer(invocation -> Stream.empty());
        when(operations.search(any(Query.class), eq(LoanSearchDocument.class)))
                .thenReturn(hits);
    }

    @ParameterizedTest
    @CsvSource({
            ", , , ''",
            ", FUNDED, , status",
            ", , P-1, productCode",
            ", FUNDED, P-1, status/productCode",
            "loan, , , ''",
            "loan, FUNDED, , status",
            "loan, , P-1, productCode",
            "loan, FUNDED, P-1, status/productCode"
    })
    void conjunctivelyAppliesEveryProvidedFilter(String text, String status, String productCode, String fields) {
        for (Optional<String> owner : List.of(Optional.of(CUSTOMER_ID), Optional.<String>empty())) {
            repository.findMatching(owner, text, status, productCode, PAGE);

            verify(operations).search(assertArg((Query query) -> {
                var criteria = ((CriteriaQuery) query).getCriteria();
                var expectedFields = new ArrayList<String>();
                owner.ifPresent(id -> expectedFields.add("customerId"));
                if (!fields.isEmpty()) {
                    expectedFields.addAll(List.of(fields.split("/")));
                }
                assertThat(criteria.getCriteriaChain()).extracting(Criteria::getField)
                        .extracting(Field::getName).containsExactlyElementsOf(expectedFields);
                assertThat(criteria.getCriteriaChain()).noneMatch(Criteria::isOr);
                assertThat(criteria.getSubCriteria()).hasSize(text == null ? 0 : 1);
            }), eq(LoanSearchDocument.class));
            clearInvocations(operations);
        }
    }

    @Test
    void textAlternativesAreGroupedInsideTheRequiredOwnershipClause() {
        repository.findMatching(Optional.of(CUSTOMER_ID), "loan", "FUNDED", "P-1", PAGE);

        verify(operations).search(assertArg((Query query) -> {
            var criteria = ((CriteriaQuery) query).getCriteria();
            assertThat(criteria.isOr()).isFalse();
            assertThat(criteria.getCriteriaChain()).first().satisfies(owner ->
                    assertThat(owner.getQueryCriteriaEntries()).singleElement().satisfies(entry -> {
                        assertThat(entry.getKey()).isEqualTo(Criteria.OperationKey.IN);
                        assertThat(entry.getValue()).isEqualTo(List.of(CUSTOMER_ID));
                    }));
            assertThat(criteria.getSubCriteria()).singleElement().satisfies(text ->
                    assertThat(text.getCriteriaChain())
                            .satisfiesExactly(description -> {
                                assertThat(description.getField().getName()).isEqualTo("description");
                                assertThat(description.isOr()).isFalse();
                            }, product -> {
                                assertThat(product.getField().getName()).isEqualTo("productCode");
                                assertThat(product.isOr()).isTrue();
                            }));
        }), eq(LoanSearchDocument.class));
    }
}
