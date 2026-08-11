package nc.opt.aiassistant.config;

import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public interface DatabaseTestcontainer {
    /**
     * ParadeDB et non un PostgreSQL vanilla : le schéma crée les extensions {@code vector} (embeddings)
     * et {@code pg_search} (BM25), absentes de l'image officielle — Liquibase échouait alors dès le
     * premier changelog, et avec lui tous les tests d'intégration.
     */
    @Container
    PostgreSQLContainer databaseContainer = new PostgreSQLContainer(
        DockerImageName.parse("paradedb/paradedb:latest").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("job-search-ai")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(DatabaseTestcontainer.class)))
        .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", databaseContainer::getJdbcUrl);
        registry.add("spring.datasource.username", databaseContainer::getUsername);
        registry.add("spring.datasource.password", databaseContainer::getPassword);
    }
}
