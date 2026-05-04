package com.deepaudit;

import com.deepaudit.persistence.repository.QcRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Phase 0 smoke test: verifies the Spring context loads without a live
 * Postgres. We exclude DataSourceAutoConfiguration so the bare scaffold
 * boots without a database. Phase 1 will replace this with Testcontainers.
 */
@SpringBootTest(properties =
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class DeepauditApplicationTests {

    @MockBean
    private QcRuleRepository qcRuleRepository;

    @Test
    void contextLoads() {
    }
}
