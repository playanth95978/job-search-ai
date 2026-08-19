package nc.opt.aiassistant.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.domain.enumeration.JobSource;

/**
 * A DTO for the {@link nc.aattia.rag.domain.JobOffer} entity.
 */
@Schema(description = "JHipster JDL model for Job Search AI Assistant")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobOfferDTO implements Serializable {

    private UUID id;

    @NotNull
    private String title;

    private String company;

    private String location;

    private String country;

    private Boolean remote;

    @Lob
    private String description;

    @Lob
    private String searchText;

    @Lob
    private String skills;

    @Lob
    private String metadata;

    @Lob
    private String rawPayload;

    @Size(max = 64)
    private String contentHash;

    private EmbeddingStatus embeddingStatus;

    private String embeddingModel;

    private Integer reindexVersion;

    private Integer retryCount;

    private String indexingError;

    private JobSource source;

    private String sourceId;

    private String applyUrl;

    private Integer salaryMin;

    private Integer salaryMax;

    private String salaryCurrency;

    private String contractType;

    private String experienceLevel;

    /** Job family derived at ingestion; drives the Category filter. */
    private String category;

    private Instant publishedAt;

    @NotNull
    private Instant createdAt;

    private Instant indexedAt;

    private Instant updatedAt;

    private Instant expiresAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Boolean getRemote() {
        return remote;
    }

    public void setRemote(Boolean remote) {
        this.remote = remote;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public EmbeddingStatus getEmbeddingStatus() {
        return embeddingStatus;
    }

    public void setEmbeddingStatus(EmbeddingStatus embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getReindexVersion() {
        return reindexVersion;
    }

    public void setReindexVersion(Integer reindexVersion) {
        this.reindexVersion = reindexVersion;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getIndexingError() {
        return indexingError;
    }

    public void setIndexingError(String indexingError) {
        this.indexingError = indexingError;
    }

    public JobSource getSource() {
        return source;
    }

    public void setSource(JobSource source) {
        this.source = source;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public void setApplyUrl(String applyUrl) {
        this.applyUrl = applyUrl;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Integer salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(Integer salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String salaryCurrency) {
        this.salaryCurrency = salaryCurrency;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobOfferDTO)) {
            return false;
        }

        JobOfferDTO jobOfferDTO = (JobOfferDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, jobOfferDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobOfferDTO{" +
            "id='" + getId() + "'" +
            ", title='" + getTitle() + "'" +
            ", company='" + getCompany() + "'" +
            ", location='" + getLocation() + "'" +
            ", country='" + getCountry() + "'" +
            ", remote='" + getRemote() + "'" +
            ", description='" + getDescription() + "'" +
            ", searchText='" + getSearchText() + "'" +
            ", skills='" + getSkills() + "'" +
            ", metadata='" + getMetadata() + "'" +
            ", rawPayload='" + getRawPayload() + "'" +
            ", contentHash='" + getContentHash() + "'" +
            ", embeddingStatus='" + getEmbeddingStatus() + "'" +
            ", embeddingModel='" + getEmbeddingModel() + "'" +
            ", reindexVersion=" + getReindexVersion() +
            ", retryCount=" + getRetryCount() +
            ", indexingError='" + getIndexingError() + "'" +
            ", source='" + getSource() + "'" +
            ", sourceId='" + getSourceId() + "'" +
            ", applyUrl='" + getApplyUrl() + "'" +
            ", salaryMin=" + getSalaryMin() +
            ", salaryMax=" + getSalaryMax() +
            ", salaryCurrency='" + getSalaryCurrency() + "'" +
            ", contractType='" + getContractType() + "'" +
            ", experienceLevel='" + getExperienceLevel() + "'" +
            ", category='" + getCategory() + "'" +
            ", publishedAt='" + getPublishedAt() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", indexedAt='" + getIndexedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", expiresAt='" + getExpiresAt() + "'" +
            "}";
    }
}
