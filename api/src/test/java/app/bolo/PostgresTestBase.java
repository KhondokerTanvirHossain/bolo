package app.bolo;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One real Postgres for every Spring test context; every database rule is tested against it. Spring owns the
 * container's lifecycle (no @Testcontainers), so a cached context never points at a container JUnit has stopped.
 */
public abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCommand("postgres", "-c", "max_connections=400");

    static {
        // Spring keeps a context per distinct test configuration alive; small pools keep them under the limit.
        System.setProperty("spring.datasource.hikari.maximum-pool-size", "4");
    }
}
