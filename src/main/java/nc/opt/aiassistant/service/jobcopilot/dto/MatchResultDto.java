package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record MatchResultDto(JobOfferDto jobOffer, double score, List<String> strengths, List<String> weaknesses) {}
