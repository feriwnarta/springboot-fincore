package com.fincore.loan.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fincore.loan.domain.model.LoanProduct;
import com.fincore.loan.LoanServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = LoanServiceApplication.class)
@ActiveProfiles("test")
@Import(JdbcLoanProductRepository.class)
@Sql("/loan-product-test-schema.sql")
class JdbcLoanProductRepositoryTest {

    @Autowired
    private JdbcLoanProductRepository repository;

    @Test
    void findsOnlyActiveProductsAfterTheCursorUsingNativeSql() {
        List<LoanProduct> products = repository.findActiveAfter("PERSONAL_ANNUITY", 10);

        assertThat(products).extracting(LoanProduct::code)
                .containsExactly("PERSONAL_FLAT");
        assertThat(products.getFirst().active()).isTrue();
    }

    @Test
    void findsAnActiveProductByCodeAndDoesNotReturnInactiveProducts() {
        assertThat(repository.findActiveByCode("PERSONAL_ANNUITY")).isPresent();
        assertThat(repository.findActiveByCode("INACTIVE_PRODUCT")).isEmpty();
    }
}
