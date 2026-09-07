package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Shape the LLM must return when extracting a candidate profile from a CV.
 *
 * <p>Deliberately narrower than {@link CandidateProfileDto}: it carries only the fields that can be
 * read from a CV, leaving out the persistence concerns ({@code id}, {@code userId}, {@code rawMarkdown},
 * timestamps…) the model has no business inventing. Spring AI's {@code BeanOutputConverter} derives the
 * JSON schema from this record, so {@code call().entity(ExtractedProfileDto.class)} replaces hand-written
 * JSON instructions and manual parsing.</p>
 */
public record ExtractedProfileDto(
    String fullName,
    String email,
    String location,
    Integer yearsOfExperience,
    List<SkillDto> skills,
    List<ExperienceDto> experiences,
    List<String> preferredRoles,
    List<LanguageDto> languages,
    List<EducationDto> education,
    List<CertificationDto> certifications
) {
    /** True when the model returned nothing usable — no identity, no skills and no experience. */
    public boolean isEmpty() {
        return (
            (fullName == null || fullName.isBlank()) &&
            (skills == null || skills.isEmpty()) &&
            (experiences == null || experiences.isEmpty())
        );
    }
}
