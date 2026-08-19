package nc.opt.aiassistant.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /job-offers?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobOfferCriteria implements Serializable, Criteria {

    /**
     * Class for filtering EmbeddingStatus
     */
    public static class EmbeddingStatusFilter extends Filter<nc.opt.aiassistant.domain.enumeration.EmbeddingStatus> {

        public EmbeddingStatusFilter() {}

        public EmbeddingStatusFilter(EmbeddingStatusFilter filter) {
            super(filter);
        }

        @Override
        public EmbeddingStatusFilter copy() {
            return new EmbeddingStatusFilter(this);
        }
    }

    /**
     * Class for filtering JobSource
     */
    public static class JobSourceFilter extends Filter<JobSource> {

        public JobSourceFilter() {}

        public JobSourceFilter(JobSourceFilter filter) {
            super(filter);
        }

        @Override
        public JobSourceFilter copy() {
            return new JobSourceFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private UUIDFilter id;

    private StringFilter title;

    private StringFilter company;

    private StringFilter location;

    private StringFilter country;

    private BooleanFilter remote;

    private StringFilter contentHash;

    private EmbeddingStatusFilter embeddingStatus;

    private StringFilter embeddingModel;

    private IntegerFilter reindexVersion;

    private IntegerFilter retryCount;

    private StringFilter indexingError;

    private JobSourceFilter source;

    private StringFilter sourceId;

    private StringFilter applyUrl;

    private IntegerFilter salaryMin;

    private IntegerFilter salaryMax;

    private StringFilter salaryCurrency;

    private StringFilter contractType;

    private StringFilter experienceLevel;

    private StringFilter category;

    private InstantFilter publishedAt;

    private InstantFilter createdAt;

    private InstantFilter indexedAt;

    private InstantFilter updatedAt;

    private InstantFilter expiresAt;

    private Boolean distinct;

    public JobOfferCriteria() {}

    public JobOfferCriteria(JobOfferCriteria other) {
        this.id = other.optionalId().map(UUIDFilter::copy).orElse(null);
        this.title = other.optionalTitle().map(StringFilter::copy).orElse(null);
        this.company = other.optionalCompany().map(StringFilter::copy).orElse(null);
        this.location = other.optionalLocation().map(StringFilter::copy).orElse(null);
        this.country = other.optionalCountry().map(StringFilter::copy).orElse(null);
        this.remote = other.optionalRemote().map(BooleanFilter::copy).orElse(null);
        this.contentHash = other.optionalContentHash().map(StringFilter::copy).orElse(null);
        this.embeddingStatus = other.optionalEmbeddingStatus().map(EmbeddingStatusFilter::copy).orElse(null);
        this.embeddingModel = other.optionalEmbeddingModel().map(StringFilter::copy).orElse(null);
        this.reindexVersion = other.optionalReindexVersion().map(IntegerFilter::copy).orElse(null);
        this.retryCount = other.optionalRetryCount().map(IntegerFilter::copy).orElse(null);
        this.indexingError = other.optionalIndexingError().map(StringFilter::copy).orElse(null);
        this.source = other.optionalSource().map(JobSourceFilter::copy).orElse(null);
        this.sourceId = other.optionalSourceId().map(StringFilter::copy).orElse(null);
        this.applyUrl = other.optionalApplyUrl().map(StringFilter::copy).orElse(null);
        this.salaryMin = other.optionalSalaryMin().map(IntegerFilter::copy).orElse(null);
        this.salaryMax = other.optionalSalaryMax().map(IntegerFilter::copy).orElse(null);
        this.salaryCurrency = other.optionalSalaryCurrency().map(StringFilter::copy).orElse(null);
        this.contractType = other.optionalContractType().map(StringFilter::copy).orElse(null);
        this.experienceLevel = other.optionalExperienceLevel().map(StringFilter::copy).orElse(null);
        this.category = other.optionalCategory().map(StringFilter::copy).orElse(null);
        this.publishedAt = other.optionalPublishedAt().map(InstantFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.indexedAt = other.optionalIndexedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.expiresAt = other.optionalExpiresAt().map(InstantFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public JobOfferCriteria copy() {
        return new JobOfferCriteria(this);
    }

    public UUIDFilter getId() {
        return id;
    }

    public Optional<UUIDFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public UUIDFilter id() {
        if (id == null) {
            setId(new UUIDFilter());
        }
        return id;
    }

    public void setId(UUIDFilter id) {
        this.id = id;
    }

    public StringFilter getTitle() {
        return title;
    }

    public Optional<StringFilter> optionalTitle() {
        return Optional.ofNullable(title);
    }

    public StringFilter title() {
        if (title == null) {
            setTitle(new StringFilter());
        }
        return title;
    }

    public void setTitle(StringFilter title) {
        this.title = title;
    }

    public StringFilter getCompany() {
        return company;
    }

    public Optional<StringFilter> optionalCompany() {
        return Optional.ofNullable(company);
    }

    public StringFilter company() {
        if (company == null) {
            setCompany(new StringFilter());
        }
        return company;
    }

    public void setCompany(StringFilter company) {
        this.company = company;
    }

    public StringFilter getLocation() {
        return location;
    }

    public Optional<StringFilter> optionalLocation() {
        return Optional.ofNullable(location);
    }

    public StringFilter location() {
        if (location == null) {
            setLocation(new StringFilter());
        }
        return location;
    }

    public void setLocation(StringFilter location) {
        this.location = location;
    }

    public StringFilter getCountry() {
        return country;
    }

    public Optional<StringFilter> optionalCountry() {
        return Optional.ofNullable(country);
    }

    public StringFilter country() {
        if (country == null) {
            setCountry(new StringFilter());
        }
        return country;
    }

    public void setCountry(StringFilter country) {
        this.country = country;
    }

    public BooleanFilter getRemote() {
        return remote;
    }

    public Optional<BooleanFilter> optionalRemote() {
        return Optional.ofNullable(remote);
    }

    public BooleanFilter remote() {
        if (remote == null) {
            setRemote(new BooleanFilter());
        }
        return remote;
    }

    public void setRemote(BooleanFilter remote) {
        this.remote = remote;
    }

    public StringFilter getContentHash() {
        return contentHash;
    }

    public Optional<StringFilter> optionalContentHash() {
        return Optional.ofNullable(contentHash);
    }

    public StringFilter contentHash() {
        if (contentHash == null) {
            setContentHash(new StringFilter());
        }
        return contentHash;
    }

    public void setContentHash(StringFilter contentHash) {
        this.contentHash = contentHash;
    }

    public EmbeddingStatusFilter getEmbeddingStatus() {
        return embeddingStatus;
    }

    public Optional<EmbeddingStatusFilter> optionalEmbeddingStatus() {
        return Optional.ofNullable(embeddingStatus);
    }

    public EmbeddingStatusFilter embeddingStatus() {
        if (embeddingStatus == null) {
            setEmbeddingStatus(new EmbeddingStatusFilter());
        }
        return embeddingStatus;
    }

    public void setEmbeddingStatus(EmbeddingStatusFilter embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
    }

    public StringFilter getEmbeddingModel() {
        return embeddingModel;
    }

    public Optional<StringFilter> optionalEmbeddingModel() {
        return Optional.ofNullable(embeddingModel);
    }

    public StringFilter embeddingModel() {
        if (embeddingModel == null) {
            setEmbeddingModel(new StringFilter());
        }
        return embeddingModel;
    }

    public void setEmbeddingModel(StringFilter embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public IntegerFilter getReindexVersion() {
        return reindexVersion;
    }

    public Optional<IntegerFilter> optionalReindexVersion() {
        return Optional.ofNullable(reindexVersion);
    }

    public IntegerFilter reindexVersion() {
        if (reindexVersion == null) {
            setReindexVersion(new IntegerFilter());
        }
        return reindexVersion;
    }

    public void setReindexVersion(IntegerFilter reindexVersion) {
        this.reindexVersion = reindexVersion;
    }

    public IntegerFilter getRetryCount() {
        return retryCount;
    }

    public Optional<IntegerFilter> optionalRetryCount() {
        return Optional.ofNullable(retryCount);
    }

    public IntegerFilter retryCount() {
        if (retryCount == null) {
            setRetryCount(new IntegerFilter());
        }
        return retryCount;
    }

    public void setRetryCount(IntegerFilter retryCount) {
        this.retryCount = retryCount;
    }

    public StringFilter getIndexingError() {
        return indexingError;
    }

    public Optional<StringFilter> optionalIndexingError() {
        return Optional.ofNullable(indexingError);
    }

    public StringFilter indexingError() {
        if (indexingError == null) {
            setIndexingError(new StringFilter());
        }
        return indexingError;
    }

    public void setIndexingError(StringFilter indexingError) {
        this.indexingError = indexingError;
    }

    public JobSourceFilter getSource() {
        return source;
    }

    public Optional<JobSourceFilter> optionalSource() {
        return Optional.ofNullable(source);
    }

    public JobSourceFilter source() {
        if (source == null) {
            setSource(new JobSourceFilter());
        }
        return source;
    }

    public void setSource(JobSourceFilter source) {
        this.source = source;
    }

    public StringFilter getSourceId() {
        return sourceId;
    }

    public Optional<StringFilter> optionalSourceId() {
        return Optional.ofNullable(sourceId);
    }

    public StringFilter sourceId() {
        if (sourceId == null) {
            setSourceId(new StringFilter());
        }
        return sourceId;
    }

    public void setSourceId(StringFilter sourceId) {
        this.sourceId = sourceId;
    }

    public StringFilter getApplyUrl() {
        return applyUrl;
    }

    public Optional<StringFilter> optionalApplyUrl() {
        return Optional.ofNullable(applyUrl);
    }

    public StringFilter applyUrl() {
        if (applyUrl == null) {
            setApplyUrl(new StringFilter());
        }
        return applyUrl;
    }

    public void setApplyUrl(StringFilter applyUrl) {
        this.applyUrl = applyUrl;
    }

    public IntegerFilter getSalaryMin() {
        return salaryMin;
    }

    public Optional<IntegerFilter> optionalSalaryMin() {
        return Optional.ofNullable(salaryMin);
    }

    public IntegerFilter salaryMin() {
        if (salaryMin == null) {
            setSalaryMin(new IntegerFilter());
        }
        return salaryMin;
    }

    public void setSalaryMin(IntegerFilter salaryMin) {
        this.salaryMin = salaryMin;
    }

    public IntegerFilter getSalaryMax() {
        return salaryMax;
    }

    public Optional<IntegerFilter> optionalSalaryMax() {
        return Optional.ofNullable(salaryMax);
    }

    public IntegerFilter salaryMax() {
        if (salaryMax == null) {
            setSalaryMax(new IntegerFilter());
        }
        return salaryMax;
    }

    public void setSalaryMax(IntegerFilter salaryMax) {
        this.salaryMax = salaryMax;
    }

    public StringFilter getSalaryCurrency() {
        return salaryCurrency;
    }

    public Optional<StringFilter> optionalSalaryCurrency() {
        return Optional.ofNullable(salaryCurrency);
    }

    public StringFilter salaryCurrency() {
        if (salaryCurrency == null) {
            setSalaryCurrency(new StringFilter());
        }
        return salaryCurrency;
    }

    public void setSalaryCurrency(StringFilter salaryCurrency) {
        this.salaryCurrency = salaryCurrency;
    }

    public StringFilter getContractType() {
        return contractType;
    }

    public Optional<StringFilter> optionalContractType() {
        return Optional.ofNullable(contractType);
    }

    public StringFilter contractType() {
        if (contractType == null) {
            setContractType(new StringFilter());
        }
        return contractType;
    }

    public void setContractType(StringFilter contractType) {
        this.contractType = contractType;
    }

    public StringFilter getExperienceLevel() {
        return experienceLevel;
    }

    public Optional<StringFilter> optionalExperienceLevel() {
        return Optional.ofNullable(experienceLevel);
    }

    public StringFilter experienceLevel() {
        if (experienceLevel == null) {
            setExperienceLevel(new StringFilter());
        }
        return experienceLevel;
    }

    public void setExperienceLevel(StringFilter experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public StringFilter getCategory() {
        return category;
    }

    public Optional<StringFilter> optionalCategory() {
        return Optional.ofNullable(category);
    }

    public StringFilter category() {
        if (category == null) {
            setCategory(new StringFilter());
        }
        return category;
    }

    public void setCategory(StringFilter category) {
        this.category = category;
    }

    public InstantFilter getPublishedAt() {
        return publishedAt;
    }

    public Optional<InstantFilter> optionalPublishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public InstantFilter publishedAt() {
        if (publishedAt == null) {
            setPublishedAt(new InstantFilter());
        }
        return publishedAt;
    }

    public void setPublishedAt(InstantFilter publishedAt) {
        this.publishedAt = publishedAt;
    }

    public InstantFilter getCreatedAt() {
        return createdAt;
    }

    public Optional<InstantFilter> optionalCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public InstantFilter createdAt() {
        if (createdAt == null) {
            setCreatedAt(new InstantFilter());
        }
        return createdAt;
    }

    public void setCreatedAt(InstantFilter createdAt) {
        this.createdAt = createdAt;
    }

    public InstantFilter getIndexedAt() {
        return indexedAt;
    }

    public Optional<InstantFilter> optionalIndexedAt() {
        return Optional.ofNullable(indexedAt);
    }

    public InstantFilter indexedAt() {
        if (indexedAt == null) {
            setIndexedAt(new InstantFilter());
        }
        return indexedAt;
    }

    public void setIndexedAt(InstantFilter indexedAt) {
        this.indexedAt = indexedAt;
    }

    public InstantFilter getUpdatedAt() {
        return updatedAt;
    }

    public Optional<InstantFilter> optionalUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    public InstantFilter updatedAt() {
        if (updatedAt == null) {
            setUpdatedAt(new InstantFilter());
        }
        return updatedAt;
    }

    public void setUpdatedAt(InstantFilter updatedAt) {
        this.updatedAt = updatedAt;
    }

    public InstantFilter getExpiresAt() {
        return expiresAt;
    }

    public Optional<InstantFilter> optionalExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public InstantFilter expiresAt() {
        if (expiresAt == null) {
            setExpiresAt(new InstantFilter());
        }
        return expiresAt;
    }

    public void setExpiresAt(InstantFilter expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JobOfferCriteria that = (JobOfferCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(title, that.title) &&
            Objects.equals(company, that.company) &&
            Objects.equals(location, that.location) &&
            Objects.equals(country, that.country) &&
            Objects.equals(remote, that.remote) &&
            Objects.equals(contentHash, that.contentHash) &&
            Objects.equals(embeddingStatus, that.embeddingStatus) &&
            Objects.equals(embeddingModel, that.embeddingModel) &&
            Objects.equals(reindexVersion, that.reindexVersion) &&
            Objects.equals(retryCount, that.retryCount) &&
            Objects.equals(indexingError, that.indexingError) &&
            Objects.equals(source, that.source) &&
            Objects.equals(sourceId, that.sourceId) &&
            Objects.equals(applyUrl, that.applyUrl) &&
            Objects.equals(salaryMin, that.salaryMin) &&
            Objects.equals(salaryMax, that.salaryMax) &&
            Objects.equals(salaryCurrency, that.salaryCurrency) &&
            Objects.equals(contractType, that.contractType) &&
            Objects.equals(experienceLevel, that.experienceLevel) &&
            Objects.equals(category, that.category) &&
            Objects.equals(publishedAt, that.publishedAt) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(indexedAt, that.indexedAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(expiresAt, that.expiresAt) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            title,
            company,
            location,
            country,
            remote,
            contentHash,
            embeddingStatus,
            embeddingModel,
            reindexVersion,
            retryCount,
            indexingError,
            source,
            sourceId,
            applyUrl,
            salaryMin,
            salaryMax,
            salaryCurrency,
            contractType,
            experienceLevel,
            category,
            publishedAt,
            createdAt,
            indexedAt,
            updatedAt,
            expiresAt,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobOfferCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalTitle().map(f -> "title=" + f + ", ").orElse("") +
            optionalCompany().map(f -> "company=" + f + ", ").orElse("") +
            optionalLocation().map(f -> "location=" + f + ", ").orElse("") +
            optionalCountry().map(f -> "country=" + f + ", ").orElse("") +
            optionalRemote().map(f -> "remote=" + f + ", ").orElse("") +
            optionalContentHash().map(f -> "contentHash=" + f + ", ").orElse("") +
            optionalEmbeddingStatus().map(f -> "embeddingStatus=" + f + ", ").orElse("") +
            optionalEmbeddingModel().map(f -> "embeddingModel=" + f + ", ").orElse("") +
            optionalReindexVersion().map(f -> "reindexVersion=" + f + ", ").orElse("") +
            optionalRetryCount().map(f -> "retryCount=" + f + ", ").orElse("") +
            optionalIndexingError().map(f -> "indexingError=" + f + ", ").orElse("") +
            optionalSource().map(f -> "source=" + f + ", ").orElse("") +
            optionalSourceId().map(f -> "sourceId=" + f + ", ").orElse("") +
            optionalApplyUrl().map(f -> "applyUrl=" + f + ", ").orElse("") +
            optionalSalaryMin().map(f -> "salaryMin=" + f + ", ").orElse("") +
            optionalSalaryMax().map(f -> "salaryMax=" + f + ", ").orElse("") +
            optionalSalaryCurrency().map(f -> "salaryCurrency=" + f + ", ").orElse("") +
            optionalContractType().map(f -> "contractType=" + f + ", ").orElse("") +
            optionalExperienceLevel().map(f -> "experienceLevel=" + f + ", ").orElse("") +
            optionalCategory().map(f -> "category=" + f + ", ").orElse("") +
            optionalPublishedAt().map(f -> "publishedAt=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalIndexedAt().map(f -> "indexedAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalExpiresAt().map(f -> "expiresAt=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
