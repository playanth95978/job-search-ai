package nc.opt.aiassistant.service.jobcopilot.ats;

import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.JobOffer_;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.criteria.JobOfferCriteria;
import nc.opt.aiassistant.service.dto.JobOfferDTO;
import nc.opt.aiassistant.service.mapper.JobOfferMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link JobOffer} entities in the database.
 * The main input is a {@link JobOfferCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link JobOfferDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class JobOfferQueryService extends QueryService<JobOffer> {

    private static final Logger LOG = LoggerFactory.getLogger(JobOfferQueryService.class);

    private final JobOfferRepository jobOfferRepository;

    private final JobOfferMapper jobOfferMapper;

    public JobOfferQueryService(JobOfferRepository jobOfferRepository, JobOfferMapper jobOfferMapper) {
        this.jobOfferRepository = jobOfferRepository;
        this.jobOfferMapper = jobOfferMapper;
    }

    /**
     * Return a {@link Page} of {@link JobOfferDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<JobOfferDTO> findByCriteria(JobOfferCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<JobOffer> specification = createSpecification(criteria);
        return jobOfferRepository.findAll(specification, page).map(jobOfferMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(JobOfferCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<JobOffer> specification = createSpecification(criteria);
        return jobOfferRepository.count(specification);
    }

    /**
     * The {@link Specification} a criteria set resolves to, exposed so the facet counts can be computed
     * under exactly the same filters as the result list (see
     * {@link nc.opt.aiassistant.service.jobcopilot.ats.JobOfferFacetService}).
     */
    public Specification<JobOffer> toSpecification(JobOfferCriteria criteria) {
        return createSpecification(criteria);
    }

    /**
     * Function to convert {@link JobOfferCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<JobOffer> createSpecification(JobOfferCriteria criteria) {
        Specification<JobOffer> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildSpecification(criteria.getId(), JobOffer_.id),
                    buildStringSpecification(criteria.getTitle(), JobOffer_.title),
                    buildStringSpecification(criteria.getCompany(), JobOffer_.company),
                    buildStringSpecification(criteria.getLocation(), JobOffer_.location),
                    buildStringSpecification(criteria.getCountry(), JobOffer_.country),
                    buildSpecification(criteria.getRemote(), JobOffer_.remote),
                    buildStringSpecification(criteria.getContentHash(), JobOffer_.contentHash),
                    buildSpecification(criteria.getEmbeddingStatus(), JobOffer_.embeddingStatus),
                    buildStringSpecification(criteria.getEmbeddingModel(), JobOffer_.embeddingModel),
                    buildRangeSpecification(criteria.getReindexVersion(), JobOffer_.reindexVersion),
                    buildRangeSpecification(criteria.getRetryCount(), JobOffer_.retryCount),
                    buildStringSpecification(criteria.getIndexingError(), JobOffer_.indexingError),
                    buildSpecification(criteria.getSource(), JobOffer_.source),
                    buildStringSpecification(criteria.getSourceId(), JobOffer_.sourceId),
                    buildStringSpecification(criteria.getApplyUrl(), JobOffer_.applyUrl),
                    buildRangeSpecification(criteria.getSalaryMin(), JobOffer_.salaryMin),
                    buildRangeSpecification(criteria.getSalaryMax(), JobOffer_.salaryMax),
                    buildStringSpecification(criteria.getSalaryCurrency(), JobOffer_.salaryCurrency),
                    buildStringSpecification(criteria.getContractType(), JobOffer_.contractType),
                    buildStringSpecification(criteria.getExperienceLevel(), JobOffer_.experienceLevel),
                    buildStringSpecification(criteria.getCategory(), JobOffer_.category),
                    buildRangeSpecification(criteria.getPublishedAt(), JobOffer_.publishedAt),
                    buildRangeSpecification(criteria.getCreatedAt(), JobOffer_.createdAt),
                    buildRangeSpecification(criteria.getIndexedAt(), JobOffer_.indexedAt),
                    buildRangeSpecification(criteria.getUpdatedAt(), JobOffer_.updatedAt),
                    buildRangeSpecification(criteria.getExpiresAt(), JobOffer_.expiresAt)
                )
            );
        }
        return specification;
    }
}
