package nc.opt.aiassistant.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.service.jobcopilot.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class CandidateProfileMapper implements EntityMapper<CandidateProfileDto, CandidateProfile> {

    @Autowired
    protected ObjectMapper objectMapper;

    @Override
    @Mapping(target = "skills", source = "skills", qualifiedByName = "jsonToSkills")
    @Mapping(target = "experiences", source = "experiences", qualifiedByName = "jsonToExperiences")
    @Mapping(target = "preferredRoles", source = "preferredRoles", qualifiedByName = "jsonToStringList")
    @Mapping(target = "languages", source = "languages", qualifiedByName = "jsonToLanguages")
    @Mapping(target = "education", source = "education", qualifiedByName = "jsonToEducation")
    @Mapping(target = "certifications", source = "certifications", qualifiedByName = "jsonToCertifications")
    public abstract CandidateProfileDto toDto(CandidateProfile entity);

    @Override
    @Mapping(target = "skills", source = "skills", qualifiedByName = "skillsToJson")
    @Mapping(target = "experiences", source = "experiences", qualifiedByName = "experiencesToJson")
    @Mapping(target = "preferredRoles", source = "preferredRoles", qualifiedByName = "objectToJson")
    @Mapping(target = "languages", source = "languages", qualifiedByName = "languagesToJson")
    @Mapping(target = "education", source = "education", qualifiedByName = "educationToJson")
    @Mapping(target = "certifications", source = "certifications", qualifiedByName = "certificationsToJson")
    public abstract CandidateProfile toEntity(CandidateProfileDto dto);

    @Override
    @Mapping(target = "skills", source = "skills", qualifiedByName = "skillsToJson")
    @Mapping(target = "experiences", source = "experiences", qualifiedByName = "experiencesToJson")
    @Mapping(target = "preferredRoles", source = "preferredRoles", qualifiedByName = "objectToJson")
    @Mapping(target = "languages", source = "languages", qualifiedByName = "languagesToJson")
    @Mapping(target = "education", source = "education", qualifiedByName = "educationToJson")
    @Mapping(target = "certifications", source = "certifications", qualifiedByName = "certificationsToJson")
    public abstract void partialUpdate(@org.mapstruct.MappingTarget CandidateProfile entity, CandidateProfileDto dto);

    @Named("jsonToSkills")
    protected List<SkillDto> jsonToSkills(String json) {
        return jsonToList(json, new TypeReference<List<SkillDto>>() {});
    }

    @Named("skillsToJson")
    protected String skillsToJson(List<SkillDto> list) {
        return objectToJson(list);
    }

    @Named("jsonToExperiences")
    protected List<ExperienceDto> jsonToExperiences(String json) {
        return jsonToList(json, new TypeReference<List<ExperienceDto>>() {});
    }

    @Named("experiencesToJson")
    protected String experiencesToJson(List<ExperienceDto> list) {
        return objectToJson(list);
    }

    @Named("jsonToStringList")
    protected List<String> jsonToStringList(String json) {
        return jsonToList(json, new TypeReference<List<String>>() {});
    }

    @Named("jsonToLanguages")
    protected List<LanguageDto> jsonToLanguages(String json) {
        return jsonToList(json, new TypeReference<List<LanguageDto>>() {});
    }

    @Named("languagesToJson")
    protected String languagesToJson(List<LanguageDto> list) {
        return objectToJson(list);
    }

    @Named("jsonToEducation")
    protected List<EducationDto> jsonToEducation(String json) {
        return jsonToList(json, new TypeReference<List<EducationDto>>() {});
    }

    @Named("educationToJson")
    protected String educationToJson(List<EducationDto> list) {
        return objectToJson(list);
    }

    @Named("jsonToCertifications")
    protected List<CertificationDto> jsonToCertifications(String json) {
        return jsonToList(json, new TypeReference<List<CertificationDto>>() {});
    }

    @Named("certificationsToJson")
    protected String certificationsToJson(List<CertificationDto> list) {
        return objectToJson(list);
    }

    @Named("objectToJson")
    protected String objectToJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    protected <T> List<T> jsonToList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
