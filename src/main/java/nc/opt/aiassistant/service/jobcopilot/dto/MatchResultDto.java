package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record MatchResultDto(JobOfferDto jobOffer, double score, List<MatchTagDto> strengths, List<MatchTagDto> weaknesses) {}
