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
    private final Seo seo = new Seo();
    private final RateLimit rateLimit = new RateLimit();

    /**
     * Per-caller quota applied by {@link nc.opt.aiassistant.web.filter.UserRateLimitFilter}.
     *
     * <p>{@code paths} lists the endpoints worth metering: an LLM turn, an embedding, a rerank, an
     * outbound scrape or a PDF layout. Cheap CRUD is left alone — metering it would only risk
     * breaking the UI for no gain. The defaults below are deliberately generous: the goal is to cap
     * abuse, not to throttle normal use, where opening a screen can fire a handful of calls.</p>
     */
    @Setter
    @Getter
    public static class RateLimit {

        private boolean enabled = true;

        /** Requests allowed per {@code refillPeriod}, per caller. */
        private int capacity = 30;

        private java.time.Duration refillPeriod = java.time.Duration.ofMinutes(1);

        private java.util.List<String> paths = java.util.List.of(
            "/api/cv-builder/**",
            "/api/job-copilot/assistant/**",
            "/api/job-copilot/interview/**",
            "/api/job-copilot/panel/**",
            "/api/job-copilot/career/**",
            "/api/job-copilot/cv/**",
            "/api/job-copilot/insights/**",
            "/api/job-copilot/opportunities/**",
            "/api/job-copilot/jobs/search-smart",
            "/api/job-copilot/jobs/match",
            "/api/job-copilot/jobs/match-advanced",
            "/api/job-copilot/jobs/fetch",
            "/api/job-copilot/jobs/translate",
            "/api/job-copilot/jobs/boards/suggestions",
            "/api/job-copilot/jobs/*/enrich",
            "/api/job-copilot/jobs/*/translate",
            "/api/job-copilot/jobs/*/positioning/**",
            "/api/job-copilot/jobs/*/tailored/**"
        );
    }

    // jhipster-needle-application-properties-property

    // jhipster-needle-application-properties-property-getter

    @Setter
    @Getter
    public static class Seo {

        /** Public base URL (scheme + host, no trailing slash) used to build absolute URLs in sitemap.xml. */
        private String baseUrl = "https://aicarreerfinder.com";
    }

    @Setter
    @Getter
    public static class JobCopilot {

        private JobCopilotConfig franceTravail;
        private Adzuna adzuna;
        private Careerjet careerjet = new Careerjet();
        private Jobicy jobicy = new Jobicy();
        private Workable workable = new Workable();
        private Ashby ashby = new Ashby();
        private Recruitee recruitee = new Recruitee();
        private Seek seek = new Seek();
        private Ingestion ingestion = new Ingestion();
        private Radar radar = new Radar();
        private Purge purge = new Purge();
        private Gdpr gdpr = new Gdpr();
        private Liveness liveness = new Liveness();
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
        /**
         * Ancienneté maximale (jours) des offres examinées à <b>chaque</b> scan.
         *
         * <p>Ce n'était auparavant qu'une fenêtre <i>initiale</i> : les scans suivants ne voyaient
         * que les offres créées depuis le scan précédent, soit ~15 minutes. Comme l'ingestion est
         * quotidienne, la quasi-totalité des scans travaillaient sur un ensemble vide, et une offre
         * passée sous le seuil n'était jamais réexaminée. La déduplication est assurée par identité
         * ({@code radar_hit} unique sur user + offre), pas par le temps : rejouer une fenêtre
         * glissante ne crée donc pas de doublon.</p>
         */
        private int lookbackDays = 7;
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
        /**
         * Default lifetime (days) applied to ingested offers whose source gives no expiry date, so the
         * daily purge has an {@code expires_at} to act on. Refreshed each time an offer is (re)ingested.
         */
        private int offerTtlDays = 30;
    }

    @Setter
    @Getter
    public static class Purge {

        /** Cron for the daily purge of expired offers. */
        private String cron = "0 0 4 * * *";
        /** Offers deleted per batch (one transaction per batch). */
        private int batchSize = 500;
        /** Batches deleted in parallel (virtual threads); keep under the JDBC pool size. */
        private int concurrency = 4;
        /** Cap per run; the remainder is handled on the next run. */
        private int maxPerRun = 50000;
    }

    /** Purge des données personnelles au-delà de la durée de conservation (RGPD art. 5-1-e). */
    @Setter
    @Getter
    public static class Gdpr {

        /** Désactivable en développement, où les jeux de test ont des dates arbitraires. */
        private boolean enabled = true;
        /** Cron de la purge quotidienne ; 5 h, après l'ingestion (2 h) et la purge des offres (4 h). */
        private String cron = "0 0 5 * * *";
        /**
         * Conservation à compter de la DERNIÈRE activité de l'utilisateur, pas de la création de la
         * ligne. 730 jours = 2 ans, recommandation CNIL pour les données de candidature.
         */
        private int retentionDays = 730;
        /** Utilisateurs traités par transaction. */
        private int batchSize = 100;
    }

    @Setter
    @Getter
    public static class Liveness {

        /** Offers whose apply URL was checked more recently than this (hours) are skipped. */
        private int recheckHours = 24;
        /** Offers verified per batch/transaction. */
        private int batchSize = 25;
        /** Partitions checked in parallel (virtual threads); keep under the JDBC pool size. */
        private int concurrency = 4;
        /** Cap of offers checked per partition per run. */
        private int maxPerRun = 2000;
        /** Per-request HTTP timeout (seconds). */
        private int httpTimeoutSeconds = 6;
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

    /** Careerjet Search API v4 — Basic auth with the API key, and a mandatory end-user ip/user-agent. */
    @Setter
    @Getter
    public static class Careerjet {

        private String apiKey;
        /** Market: Careerjet partitions its index by locale (fr_FR, en_AU, en_GB…). */
        private String localeCode = "fr_FR";
        private String userIp;
        private String userAgent = "Mozilla/5.0 (compatible; JobSearchAI/1.0)";
        /** Site declared on the Careerjet account; sent as Referer, mandatory. */
        private String referer;
        private int pageSize = 50;
        private int maxPages = 5;
    }

    /** Jobicy remote-jobs API — no key; their terms cap polling at one call per hour, hence the cache. */
    @Setter
    @Getter
    public static class Jobicy {

        /** Listings fetched per call; 1..100 per the API. */
        private int count = 100;
        /** Minimum interval between two calls; must stay >= 1h to respect their fair-use policy. */
        private java.time.Duration cacheTtl = java.time.Duration.ofHours(1);
    }

    /** Workable public careers API — one account slug per company, no key. */
    @Setter
    @Getter
    public static class Workable {

        /** Comma-separated account slugs crawled by the ingestion. */
        private String accounts;
    }

    /** Ashby public job-posting API — one board name per company, no key. */
    @Setter
    @Getter
    public static class Ashby {

        /** Comma-separated job-board names crawled by the ingestion. */
        private String boards;
    }

    /** Recruitee careers-site API — one subdomain per company, no key. */
    @Setter
    @Getter
    public static class Recruitee {

        /** Comma-separated company subdomains crawled by the ingestion. */
        private String companies;
    }

    @Getter
    public static class Ai {

        private final Ollama ollama = new Ollama();
        private final LlamaServer llamaServer = new LlamaServer();
        private final Embedding embedding = new Embedding();
        private final Mistral mistral = new Mistral();
        private final Voxtral voxtral = new Voxtral();
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
        public static class Voxtral {

            /**
             * Voice in the interview roleplay is opt-in: every turn costs a transcription plus a speech
             * synthesis call, so it stays off until someone turns it on deliberately.
             */
            private boolean enabled = false;

            private String baseUrl = "https://api.mistral.ai";

            /** Voxtral Mini Transcribe 2 — batch speech-to-text. */
            private String transcriptionModel = "voxtral-mini-latest";

            /** Voxtral TTS. */
            private String ttsModel = "voxtral-mini-tts-2603";

            /**
             * Voice the recruiter speaks with: one of Voxtral TTS's preset voices, or a cloned profile.
             * Required when enabled — there is no implicit default.
             */
            private String voice;

            /** mp3 keeps the payload small for a request/response turn; pcm only pays off when streaming. */
            private String responseFormat = "mp3";

            /** Ceiling on the uploaded answer, before it ever reaches Mistral. */
            private int maxUploadBytes = 10 * 1024 * 1024;

            /** Audio can take a while to synthesise; well above the default REST read timeout. */
            private int timeoutSeconds = 120;
        }

        @Setter
        @Getter
        public static class Ollama {

            private String baseUrl;
            private String model;
            private String token;
            private boolean enabled;
            /** Read timeout (seconds) for Ollama calls — CPU-only hosts need a generous value for embeddings. */
            private int timeoutSeconds = 180;
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
