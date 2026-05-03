package com.deepaudit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Phase 0 smoke test: verifies the Spring context loads without a live
 * Postgres. We exclude DataSourceAutoConfiguration so the bare scaffold
 * boots without a database. Phase 1 will replace this with Testcontainers.
 */
@SpringBootTest(properties =
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
class DeepauditApplicationTests {

    @Test
    void contextLoads() {
    }
}
