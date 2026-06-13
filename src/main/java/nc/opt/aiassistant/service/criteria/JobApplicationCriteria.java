package nc.opt.aiassistant.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link nc.aattia.rag.domain.JobApplication} entity. This class is used
 * in {@link nc.aattia.rag.web.rest.JobApplicationResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /job-applications?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobApplicationCriteria implements Serializable, Criteria {

    /**
     * Class for filtering ApplicationStatus
     */
    public static class ApplicationStatusFilter extends Filter<ApplicationStatus> {

        public ApplicationStatusFilter() {}

        public ApplicationStatusFilter(ApplicationStatusFilter filter) {
            super(filter);
        }

        @Override
        public ApplicationStatusFilter copy() {
            return new ApplicationStatusFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private UUIDFilter id;

    private StringFilter userId;

    private ApplicationStatusFilter status;

    private DoubleFilter matchScore;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private InstantFilter appliedAt;

    private UUIDFilter jobOfferId;

    private UUIDFilter candidateProfileId;

    private Boolean distinct;

    public JobApplicationCriteria() {}

    public JobApplicationCriteria(JobApplicationCriteria other) {
        this.id = other.optionalId().map(UUIDFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(ApplicationStatusFilter::copy).orElse(null);
        this.matchScore = other.optionalMatchScore().map(DoubleFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.appliedAt = other.optionalAppliedAt().map(InstantFilter::copy).orElse(null);
        this.jobOfferId = other.optionalJobOfferId().map(UUIDFilter::copy).orElse(null);
        this.candidateProfileId = other.optionalCandidateProfileId().map(UUIDFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public JobApplicationCriteria copy() {
        return new JobApplicationCriteria(this);
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

    public StringFilter getUserId() {
        return userId;
    }

    public Optional<StringFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public StringFilter userId() {
        if (userId == null) {
            setUserId(new StringFilter());
        }
        return userId;
    }

    public void setUserId(StringFilter userId) {
        this.userId = userId;
    }

    public ApplicationStatusFilter getStatus() {
        return status;
    }

    public Optional<ApplicationStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public ApplicationStatusFilter status() {
        if (status == null) {
            setStatus(new ApplicationStatusFilter());
        }
        return status;
    }

    public void setStatus(ApplicationStatusFilter status) {
        this.status = status;
    }

    public DoubleFilter getMatchScore() {
        return matchScore;
    }

    public Optional<DoubleFilter> optionalMatchScore() {
        return Optional.ofNullable(matchScore);
    }

    public DoubleFilter matchScore() {
        if (matchScore == null) {
            setMatchScore(new DoubleFilter());
        }
        return matchScore;
    }

    public void setMatchScore(DoubleFilter matchScore) {
        this.matchScore = matchScore;
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

    public InstantFilter getAppliedAt() {
        return appliedAt;
    }

    public Optional<InstantFilter> optionalAppliedAt() {
        return Optional.ofNullable(appliedAt);
    }

    public InstantFilter appliedAt() {
        if (appliedAt == null) {
            setAppliedAt(new InstantFilter());
        }
        return appliedAt;
    }

    public void setAppliedAt(InstantFilter appliedAt) {
        this.appliedAt = appliedAt;
    }

    public UUIDFilter getJobOfferId() {
        return jobOfferId;
    }

    public Optional<UUIDFilter> optionalJobOfferId() {
        return Optional.ofNullable(jobOfferId);
    }

    public UUIDFilter jobOfferId() {
        if (jobOfferId == null) {
            setJobOfferId(new UUIDFilter());
        }
        return jobOfferId;
    }

    public void setJobOfferId(UUIDFilter jobOfferId) {
        this.jobOfferId = jobOfferId;
    }

    public UUIDFilter getCandidateProfileId() {
        return candidateProfileId;
    }

    public Optional<UUIDFilter> optionalCandidateProfileId() {
        return Optional.ofNullable(candidateProfileId);
    }

    public UUIDFilter candidateProfileId() {
        if (candidateProfileId == null) {
            setCandidateProfileId(new UUIDFilter());
        }
        return candidateProfileId;
    }

    public void setCandidateProfileId(UUIDFilter candidateProfileId) {
        this.candidateProfileId = candidateProfileId;
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
        final JobApplicationCriteria that = (JobApplicationCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(status, that.status) &&
            Objects.equals(matchScore, that.matchScore) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(appliedAt, that.appliedAt) &&
            Objects.equals(jobOfferId, that.jobOfferId) &&
            Objects.equals(candidateProfileId, that.candidateProfileId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, status, matchScore, createdAt, updatedAt, appliedAt, jobOfferId, candidateProfileId, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobApplicationCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalMatchScore().map(f -> "matchScore=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalAppliedAt().map(f -> "appliedAt=" + f + ", ").orElse("") +
            optionalJobOfferId().map(f -> "jobOfferId=" + f + ", ").orElse("") +
            optionalCandidateProfileId().map(f -> "candidateProfileId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
