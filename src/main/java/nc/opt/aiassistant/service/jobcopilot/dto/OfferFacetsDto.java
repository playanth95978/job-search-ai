package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Available filter values with their counts, under the criteria currently applied. Feeds the
 * left-hand filter panel: every value listed has at least one matching offer.
 */
public record OfferFacetsDto(
    List<FacetValueDto> locations,
    List<FacetValueDto> companies,
    List<FacetValueDto> contractTypes,
    List<FacetValueDto> categories
) {
    /** One facet entry. Constructed directly by the JPA criteria query, hence the {@code long} count. */
    public record FacetValueDto(String value, long count) {}
}
