package com.shifa.typeahead;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the Spring context loads without errors.
 *
 * Uses the "test" profile which swaps PostgreSQL for an H2 in-memory database,
 * so this test runs on any machine without Docker or a live database.
 *
 * Production still uses PostgreSQL via Docker (docker-compose.yml).
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {

    @Test
    void contextLoads() {
        // If the application context starts successfully this test passes.
        // No assertions needed — a startup failure will throw and fail the test.
    }
}
