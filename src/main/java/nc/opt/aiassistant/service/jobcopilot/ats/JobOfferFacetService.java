package nc.opt.aiassistant.service.jobcopilot.ats;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.JobOffer_;
import nc.opt.aiassistant.service.criteria.JobOfferCriteria;
import nc.opt.aiassistant.service.jobcopilot.dto.OfferFacetsDto;
import nc.opt.aiassistant.service.jobcopilot.dto.OfferFacetsDto.FacetValueDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts the distinct values of the filterable columns, under the criteria currently applied.
 *
 * <p>The filter panel needs this: with server-side filtering the client only ever holds one page of
 * results, so it cannot count anything itself, and a hard-coded option list would offer values that
 * return nothing. Each facet is one {@code GROUP BY} sharing the very same
 * {@link Specification} as the result query, so the counts always agree with the list.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobOfferFacetService {

    /** Guards against a facet list the size of the corpus (e.g. company on a broad query). */
    private static final int MAX_VALUES_PER_FACET = 25;

    private final EntityManager entityManager;
    private final JobOfferQueryService queryService;

    public OfferFacetsDto facets(JobOfferCriteria criteria) {
        Specification<JobOffer> specification = queryService.toSpecification(criteria);
        return new OfferFacetsDto(
            countBy(specification, JobOffer_.LOCATION),
            countBy(specification, JobOffer_.COMPANY),
            countBy(specification, JobOffer_.CONTRACT_TYPE),
            countBy(specification, JobOffer_.CATEGORY)
        );
    }

    /**
     * {@code SELECT <column>, COUNT(*) ... WHERE <criteria> AND <column> IS NOT NULL GROUP BY <column>
     * ORDER BY COUNT(*) DESC}. Nulls are excluded rather than grouped: "unknown" is not a value a user
     * can usefully filter on, and for `category` it would mean "not backfilled yet".
     */
    private List<FacetValueDto> countBy(Specification<JobOffer> specification, String attribute) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<FacetValueDto> query = builder.createQuery(FacetValueDto.class);
        Root<JobOffer> root = query.from(JobOffer.class);

        var column = root.<String>get(attribute);
        query.select(builder.construct(FacetValueDto.class, column, builder.count(root)));

        Predicate filters = specification.toPredicate(root, query, builder);
        Predicate present = builder.and(column.isNotNull(), builder.notEqual(builder.trim(column), ""));
        query.where(filters == null ? present : builder.and(filters, present));

        query.groupBy(column);
        query.orderBy(builder.desc(builder.count(root)), builder.asc(column));

        return entityManager.createQuery(query).setMaxResults(MAX_VALUES_PER_FACET).getResultList();
    }
}
