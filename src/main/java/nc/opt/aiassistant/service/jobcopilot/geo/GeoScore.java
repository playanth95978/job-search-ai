package nc.opt.aiassistant.service.jobcopilot.geo;

/**
 * Result of comparing two locations: a 0..1 relevance score plus the level that produced it
 * (so callers can render a reason / tag).
 */
public record GeoScore(double score, GeoMatchLevel level) {}
