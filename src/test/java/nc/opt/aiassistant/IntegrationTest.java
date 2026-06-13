package nc.opt.aiassistant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import nc.opt.aiassistant.config.AsyncSyncConfiguration;
import nc.opt.aiassistant.config.DatabaseTestcontainer;
import nc.opt.aiassistant.config.JacksonConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
        JobSearchAiApp.class,
        JacksonConfiguration.class,
        AsyncSyncConfiguration.class,
        nc.opt.aiassistant.config.JacksonHibernateConfiguration.class,
    }
)
@ImportTestcontainers(DatabaseTestcontainer.class)
public @interface IntegrationTest {}
