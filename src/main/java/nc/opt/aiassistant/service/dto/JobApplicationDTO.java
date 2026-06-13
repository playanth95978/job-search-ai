package nc.opt.aiassistant.service.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;

/**
 * A DTO for the {@link nc.aattia.rag.domain.JobApplication} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class JobApplicationDTO implements Serializable {

    private UUID id;

    @NotNull
    private String userId;

    private ApplicationStatus status;

    @Lob
    private String coverLetter;

    @Lob
    private String notes;

    private Double matchScore;

    @NotNull
    private Instant createdAt;

    private Instant updatedAt;

    private Instant appliedAt;

    private JobOfferDTO jobOffer;

    private CandidateProfileDTO candidateProfile;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public JobOfferDTO getJobOffer() {
        return jobOffer;
    }

    public void setJobOffer(JobOfferDTO jobOffer) {
        this.jobOffer = jobOffer;
    }

    public CandidateProfileDTO getCandidateProfile() {
        return candidateProfile;
    }

    public void setCandidateProfile(CandidateProfileDTO candidateProfile) {
        this.candidateProfile = candidateProfile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobApplicationDTO)) {
            return false;
        }

        JobApplicationDTO jobApplicationDTO = (JobApplicationDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, jobApplicationDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "JobApplicationDTO{" +
            "id='" + getId() + "'" +
            ", userId='" + getUserId() + "'" +
            ", status='" + getStatus() + "'" +
            ", coverLetter='" + getCoverLetter() + "'" +
            ", notes='" + getNotes() + "'" +
            ", matchScore=" + getMatchScore() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", appliedAt='" + getAppliedAt() + "'" +
            ", jobOffer=" + getJobOffer() +
            ", candidateProfile=" + getCandidateProfile() +
            "}";
    }
}
