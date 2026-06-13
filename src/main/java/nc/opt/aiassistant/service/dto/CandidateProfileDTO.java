package nc.opt.aiassistant.service.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A DTO for the {@link nc.aattia.rag.domain.CandidateProfile} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CandidateProfileDTO implements Serializable {

    private UUID id;

    @NotNull
    private String userId;

    private String fullName;

    private String email;

    private String location;

    private Integer yearsOfExperience;

    @Lob
    private String skills;

    @Lob
    private String experiences;

    @Lob
    private String preferredRoles;

    @Lob
    private String languages;

    @Lob
    private String education;

    @Lob
    private String certifications;

    @Lob
    private String rawMarkdown;

    private String cvFilename;

    @NotNull
    private Instant createdAt;

    private Instant updatedAt;

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getExperiences() {
        return experiences;
    }

    public void setExperiences(String experiences) {
        this.experiences = experiences;
    }

    public String getPreferredRoles() {
        return preferredRoles;
    }

    public void setPreferredRoles(String preferredRoles) {
        this.preferredRoles = preferredRoles;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getCertifications() {
        return certifications;
    }

    public void setCertifications(String certifications) {
        this.certifications = certifications;
    }

    public String getRawMarkdown() {
        return rawMarkdown;
    }

    public void setRawMarkdown(String rawMarkdown) {
        this.rawMarkdown = rawMarkdown;
    }

    public String getCvFilename() {
        return cvFilename;
    }

    public void setCvFilename(String cvFilename) {
        this.cvFilename = cvFilename;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CandidateProfileDTO)) {
            return false;
        }

        CandidateProfileDTO candidateProfileDTO = (CandidateProfileDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, candidateProfileDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CandidateProfileDTO{" +
            "id='" + getId() + "'" +
            ", userId='" + getUserId() + "'" +
            ", fullName='" + getFullName() + "'" +
            ", email='" + getEmail() + "'" +
            ", location='" + getLocation() + "'" +
            ", yearsOfExperience=" + getYearsOfExperience() +
            ", skills='" + getSkills() + "'" +
            ", experiences='" + getExperiences() + "'" +
            ", preferredRoles='" + getPreferredRoles() + "'" +
            ", languages='" + getLanguages() + "'" +
            ", education='" + getEducation() + "'" +
            ", certifications='" + getCertifications() + "'" +
            ", rawMarkdown='" + getRawMarkdown() + "'" +
            ", cvFilename='" + getCvFilename() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
