package nc.opt.aiassistant.config.ai.rag;

import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    public static final int DOC_JIRA_NB_DIMENSIONS = 768;
    public static final int MAX_DOCUMENT_BATCH_SIZE = 1000;
    public static final int MAX_DOCUMENT_BATCH_SIZE_CODE_VECTOR = 5000;

    @Bean("docsVectorStore")
    public VectorStore docsVectorStore(
        JdbcTemplate jdbcTemplate,
        @Qualifier("docsEmbeddingModel") EmbeddingModel embeddingModel,
        ApplicationProperties applicationProperties
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(DOC_JIRA_NB_DIMENSIONS)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .vectorTableName(applicationProperties.getAi().getVectorStore().getDocs().getTable())
            .build();
    }

    @Bean("pdfVectorStore")
    public VectorStore pdfVectorStore(
        JdbcTemplate jdbcTemplate,
        @Qualifier("docsEmbeddingModel") EmbeddingModel embeddingModel,
        ApplicationProperties applicationProperties
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(DOC_JIRA_NB_DIMENSIONS)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .vectorTableName(applicationProperties.getAi().getVectorStore().getPdf().getTable())
            .build();
    }

    @Bean("jirasVectorStore")
    public VectorStore jirasVectorStore(
        JdbcTemplate jdbcTemplate,
        @Qualifier("docsEmbeddingModel") EmbeddingModel embeddingModel,
        ApplicationProperties applicationProperties
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(DOC_JIRA_NB_DIMENSIONS)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .vectorTableName(applicationProperties.getAi().getVectorStore().getJira().getTable())
            .build();
    }

    @Bean("codeVectorStore")
    public VectorStore codeVectorStore(
        JdbcTemplate jdbcTemplate,
        @Qualifier("codeEmbeddingModel") EmbeddingModel embeddingModel,
        ApplicationProperties applicationProperties
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1024)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            // index vectoriel performant
            .indexType(PgVectorStore.PgIndexType.HNSW)
            // batch ingestion
            .maxDocumentBatchSize(MAX_DOCUMENT_BATCH_SIZE_CODE_VECTOR)
            .vectorTableName(applicationProperties.getAi().getVectorStore().getCode().getTable())
            .build();
    }
}
