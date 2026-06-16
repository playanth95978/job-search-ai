package nc.opt.aiassistant.domain.enumeration;

/**
 * Status of the embedding process for a job offer or other searchable content.
 */
public enum EmbeddingStatus {
    /**
     * Lightweight offer scraped from a search-results list (title + teaser only).
     * Deliberately skipped by the background embedding poller: it is enriched with the
     * full description and queued for embedding lazily, when the user opens the offer.
     */
    STUB,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
