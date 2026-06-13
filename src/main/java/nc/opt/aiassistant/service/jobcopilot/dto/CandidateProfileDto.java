package nc.opt.aiassistant.service.jobcopilot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateProfileDto(
    UUID id,
    String userId,
    String fullName,
    String email,
    String location,
    Integer yearsOfExperience,
    List<SkillDto> skills,
    List<ExperienceDto> experiences,
    List<String> preferredRoles,
    List<LanguageDto> languages,
    List<EducationDto> education,
    List<CertificationDto> certifications,
    String rawMarkdown,
    String cvFilename,
    Instant createdAt,
    Instant updatedAt
) {}
