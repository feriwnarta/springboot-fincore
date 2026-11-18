package com.fincore.loan;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = LoanServiceApplication.class)
@ActiveProfiles("test")
class LoanServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
