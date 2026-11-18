package com.fincore.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = LedgerServiceApplication.class)
@ActiveProfiles("test")
class LedgerServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
