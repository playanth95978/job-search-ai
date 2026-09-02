package nc.opt.aiassistant.domain;

import com.drew.lang.annotations.NotNull;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Data
@Entity
@Table(name = "job_offer")
@EntityListeners(AuditingEntityListener.class)
public class JobOffer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "company")
    private String company;

    @Column(name = "location")
    private String location;

    @Column(name = "country")
    private String country;

    @Column(name = "remote")
    private Boolean remote;

    @Lob
    @Column(name = "description")
    private String description;

    /**
     * Texte optimisé pour BM25 + embeddings
     */
    // Pas de @Lob : Hibernate le traduirait en Large Object PostgreSQL et n'écrirait qu'un OID dans
    // la colonne, ce qui rendait l'index BM25 inopérant. La colonne historique `search_text` reste en
    // base (elle contient les anciens OID) mais n'est plus mappée.
    @Column(name = "search_text_plain")
    private String searchText;

    /**
     * Skills extraits IA
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", columnDefinition = "jsonb")
    private String skills;

    /**
     * Metadata IA enrichie
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * Payload brut provider
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    /**
     * Embedding vectoriel
     *
     * Stocké via migration SQL:
     * embedding vector(768)
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private float[] embedding;

    /**
     * Hash contenu pour éviter réindexation
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status")
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "reindex_version")
    private Integer reindexVersion = 1;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "indexing_error")
    private String indexingError;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private JobSource source;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "apply_url")
    private String applyUrl;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "salary_currency")
    private String salaryCurrency;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "experience_level")
    private String experienceLevel;

    /**
     * Job family, derived from the title at ingestion by
     * {@link nc.opt.aiassistant.service.jobcopilot.JobCategoryClassifier} because no source provides a
     * shared taxonomy. Stored so it can be filtered with a Specification like any other column.
     */
    @Column(name = "category")
    private String category;

    /**
     * The source's own taxonomy value (a ROME code, an Adzuna tag), set by the connector and consumed by
     * {@link nc.opt.aiassistant.service.jobcopilot.JobCategoryResolver} at ingestion. Not persisted: only
     * the resolved family is, and keeping the raw label would add a column nothing queries.
     */
    @Transient
    private String sourceCategory;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Last time the liveness job verified the apply URL is still reachable (null = never checked). */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
}
