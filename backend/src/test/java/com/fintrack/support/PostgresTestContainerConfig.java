package com.fintrack.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Starts one throwaway PostgreSQL container and wires it in as the datasource.
 * Import it into any test that needs the database. The same container is reused
 * by every test class in the run because the Spring context is cached.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
