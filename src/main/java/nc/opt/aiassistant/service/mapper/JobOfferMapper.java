package nc.opt.aiassistant.service.mapper;

import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.dto.JobOfferDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link JobOffer} and its DTO {@link JobOfferDTO}.
 */
@Mapper(componentModel = "spring")
public interface JobOfferMapper extends EntityMapper<JobOfferDTO, JobOffer> {}
