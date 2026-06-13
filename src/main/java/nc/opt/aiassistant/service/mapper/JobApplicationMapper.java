package nc.opt.aiassistant.service.mapper;

import java.util.Objects;
import java.util.UUID;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.dto.CandidateProfileDTO;
import nc.opt.aiassistant.service.dto.JobApplicationDTO;
import nc.opt.aiassistant.service.dto.JobOfferDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Mapper for the entity {@link JobApplication} and its DTO {@link JobApplicationDTO}.
 */
@Mapper(componentModel = "spring")
public interface JobApplicationMapper extends EntityMapper<JobApplicationDTO, JobApplication> {
    @Mapping(target = "jobOffer", source = "jobOffer", qualifiedByName = "jobOfferId")
    @Mapping(target = "candidateProfile", source = "candidateProfile", qualifiedByName = "candidateProfileId")
    JobApplicationDTO toDto(JobApplication s);

    @Named("jobOfferId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    JobOfferDTO toDtoJobOfferId(JobOffer jobOffer);

    @Named("candidateProfileId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    CandidateProfileDTO toDtoCandidateProfileId(CandidateProfile candidateProfile);

    default String map(UUID value) {
        return Objects.toString(value, null);
    }
}
