package nc.opt.aiassistant.service.jobcopilot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nc.opt.aiassistant.service.dto.JobOfferDTO;

/**
 * One Opportunity Radar item for the feed: a matched new offer, the match score, why it fits the
 * user ("why you" strengths), and its read/dismissed state.
 */
public record RadarHitDto(
    UUID id,
    JobOfferDTO jobOffer,
    double score,
    List<MatchTagDto> whyYou,
    boolean seen,
    boolean dismissed,
    Instant createdAt
) {}
