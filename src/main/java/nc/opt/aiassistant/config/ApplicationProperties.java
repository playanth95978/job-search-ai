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
        private Seek seek = new Seek();
        private Ingestion ingestion = new Ingestion();
        private Radar radar = new Radar();
    }

    @Setter
    @Getter
    public static class Radar {

        /** Opportunity Radar scan is off by default; enable to start surfacing new-offer matches. */
        private boolean enabled = false;
        /** Minimum match score (0..1) for an offer to become a radar hit. */
        private double threshold = 0.65;
        /** Nearest recent offers evaluated per user per scan. */
        private int candidateLimit = 100;
        /** Max new hits created per user per scan (anti-flood). */
        private int maxPerScan = 10;
        /** On a user's first scan, how far back to look (days) instead of the whole corpus. */
        private int initialWindowDays = 3;
        /** Delay between scans, in ms. */
        private long scanIntervalMs = 900000;
    }

    @Setter
    @Getter
    public static class JobCopilotConfig {

        private String clientId;
        private String clientSecret;
        /** Comma-separated INSEE département codes crossed with each ROME grand domaine partition; blank = national. */
        private String departements;
    }

    @Setter
    @Getter
    public static class Seek {

        /** Seek is bot-protected; the connector bean is only registered when this is true. */
        private boolean enabled = false;
    }

    @Setter
    @Getter
    public static class Ingestion {

        private int chunkSize = 200;
        private int concurrency = 4;
        private int skipLimit = 100;
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
