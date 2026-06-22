package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Full resume structure produced by the AI from free-form input. Mirrors the front-end
 * {@code Resume} model (minus client-only concerns: ids, theme, template, section order),
 * so the LLM can return it directly via {@code .entity(GeneratedResumeDto.class)}.
 */
public record GeneratedResumeDto(
    GeneratedPersonalDto personal,
    String summary,
    List<GeneratedExperienceDto> experiences,
    List<GeneratedEducationDto> education,
    List<GeneratedProjectDto> projects,
    List<GeneratedCertificationDto> certifications,
    List<GeneratedSkillDto> skills,
    List<GeneratedLanguageDto> languages
) {
    public record GeneratedPersonalDto(
        String firstName,
        String lastName,
        String title,
        String email,
        String phone,
        String location,
        String linkedin,
        String github,
        String website
    ) {}

    public record GeneratedExperienceDto(
        String jobTitle,
        String company,
        String startDate,
        String endDate,
        boolean current,
        String description,
        List<String> achievements
    ) {}

    public record GeneratedEducationDto(String degree, String school, String startDate, String endDate, String description) {}

    public record GeneratedProjectDto(String name, String description, String url, List<String> technologies) {}

    public record GeneratedCertificationDto(String name, String issuer, String date) {}

    /** {@code level} on a 1..5 scale (3 when unknown). */
    public record GeneratedSkillDto(String name, int level) {}

    public record GeneratedLanguageDto(String name, String level) {}
}
