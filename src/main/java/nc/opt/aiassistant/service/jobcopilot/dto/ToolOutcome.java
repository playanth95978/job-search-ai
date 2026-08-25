package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;
import java.util.UUID;

/**
 * What one of the agent's tools produced during a turn.
 *
 * <p>Filled in by the tools, read once the turn is over. This is the only input to
 * {@link ChatActionFactory}: no action is offered without a corresponding fact in this list.</p>
 *
 * <p>Mirrors {@code ToolOutcome} in {@code server/src/services/chat_actions.rs}. This one is
 * internal — it never crosses the wire — so it may hold more than the Rust variant if a tool that
 * exists only here needs it.</p>
 */
public sealed interface ToolOutcome {
    /**
     * Key under which the per-turn collector travels in Spring AI's {@code ToolContext}.
     *
     * <p>Declared here rather than on the tool helper so the agent service, which lives in another
     * package, can read it without the helper having to become public.</p>
     */
    String CONTEXT_KEY = "outcomes";

    /** An offer cited by a tool, reduced to what a button needs to display. */
    record CitedOffer(UUID id, String title) {}

    /**
     * The job search answered.
     *
     * @param offers the cited offers, already truncated by the tool
     * @param total  how many matched in all; may exceed {@code offers.size()}
     */
    record OffersFound(List<CitedOffer> offers, int total) implements ToolOutcome {}

    /** Profile matching answered. */
    record MatchesFound(List<CitedOffer> offers, int total) implements ToolOutcome {}

    /** The profile tool found nothing: the user has not imported a CV. */
    record ProfileMissing() implements ToolOutcome {}

    /** The application list was read. */
    record ApplicationsListed(int total) implements ToolOutcome {}

    /** A cover letter was generated for {@code offerId}. */
    record CoverLetterGenerated(UUID offerId) implements ToolOutcome {}
}
