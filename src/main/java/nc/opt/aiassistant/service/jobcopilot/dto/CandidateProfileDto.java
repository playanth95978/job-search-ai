package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record CandidateProfileDto(
    String fullName,
    String email,
    String location,
    Integer yearsOfExperience,
    List<String> skills,
    List<ExperienceDto> experiences,
    List<String> preferredRoles,
    List<String> languages,
    List<String> education,
    List<String> certifications
) {}
