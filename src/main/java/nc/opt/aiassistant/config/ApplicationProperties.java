package nc.opt.aiassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Aiassistant.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@Getter
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    // jhipster-needle-application-properties-property

    // jhipster-needle-application-properties-property-getter

    @Setter
    @Getter
    public static class Liquibase {

        private Boolean asyncStart = true;
    }

    @Setter
    @Getter
    public static class GoogleDrive {

        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }

    private final GoogleDrive googleDrive = new GoogleDrive();

    private final Ai ai = new Ai();
    private final JobCopilot jobCopilot = new JobCopilot();

    // jhipster-needle-application-properties-property

    // jhipster-needle-application-properties-property-getter

    @Setter
    @Getter
    public static class JobCopilot {

        private JobCopilotConfig franceTravail;
        private Adzuna adzuna;
    }

    @Setter
    @Getter
    public static class JobCopilotConfig {

        private String clientId;
        private String clientSecret;
    }

    @Setter
    @Getter
    public static class Adzuna {

        private String appId;
        private String appKey;
        private String country;
        private String where;
        private int resultsPerPage;
        private int maxPages;
        private String queries;
    }

    @Getter
    public static class Ai {

        private final Ollama ollama = new Ollama();
        private final LlamaServer llamaServer = new LlamaServer();
        private final Embedding embedding = new Embedding();
        private final Mistral mistral = new Mistral();
        private final VectorStore vectorStore = new VectorStore();

        @Setter
        @Getter
        public static class Mistral {

            private String model;
            private String baseUrl;
            private String apiKey;
        }

        @Setter
        @Getter
        public static class Ollama {

            private String baseUrl;
            private String model;
            private String token;
            private boolean enabled;
        }

        @Setter
        @Getter
        public static class LlamaServer {

            private String baseUrl = "http://localhost:8080/v1";
            private String model = "qwen3-35b-q4";
            private String apiKey = "no-key";
            private boolean enabled = false;
        }

        @Getter
        public static class Embedding {

            private final ModelConfig docs = new ModelConfig();
            private final ModelConfig code = new ModelConfig();

            @Setter
            @Getter
            public static class ModelConfig {

                private String model;
            }
        }

        @Getter
        public static class VectorStore {

            private final TableConfig docs = new TableConfig();
            private final TableConfig code = new TableConfig();
            private final TableConfig jira = new TableConfig();
            private final TableConfig pdf = new TableConfig();

            @Setter
            @Getter
            public static class TableConfig {

                private String table;
            }
        }
    }

    // jhipster-needle-application-properties-property-class
}
