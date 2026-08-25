package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nc.opt.aiassistant.service.jobcopilot.dto.ChatAction.ChatLabel;
import nc.opt.aiassistant.service.jobcopilot.dto.ChatAction.ChatMutation;

/**
 * Turns what the tools did into buttons to show.
 *
 * <p>Pure: no database, no model, no clock. That is what makes it exhaustively testable, and the
 * reason the button decision lives here rather than in the controller.</p>
 *
 * <p><b>Kept in step with {@code actions_for} in the Rust backend.</b> Both serve the same Angular
 * front-end, so the same conversation must offer the same buttons whichever backend answered. Any
 * rule changed here is to be changed there too.</p>
 */
public final class ChatActionFactory {

    /**
     * Most actions attached to one message.
     *
     * <p>Beyond that the thread stops reading like a conversation and starts looking like a form.
     * The value fits exactly the busiest case — three cited offers, a "see all", two filters — which
     * is also a way of saying we do not want it richer than that.</p>
     */
    static final int MAX_ACTIONS = 6;

    /**
     * How many offers get their own button.
     *
     * <p>Three: past that the "see the list" button does the job better, and a stack of offer
     * buttons pushes the answer text off screen.</p>
     */
    static final int MAX_CITED_OFFERS = 3;

    // Angular routes. Gathered here rather than inlined: these strings are handed to
    // `navigateByUrl`, so a typo yields a blank page with no error. Having them in one place also
    // makes plain what the chatbot is allowed to open.
    static final String ROUTE_SEARCH = "/job-copilot/search";
    static final String ROUTE_MATCHES = "/job-copilot/matches";
    static final String ROUTE_APPLICATIONS = "/job-copilot/applications";
    static final String ROUTE_ONBOARDING = "/job-copilot/onboarding";
    static final String ROUTE_SETTINGS = "/job-copilot/settings";
    static final String ROUTE_CV_BUILDER = "/job-copilot/cv-builder";

    private ChatActionFactory() {}

    /**
     * Builds the buttons for a finished turn.
     *
     * <p>Construction order is display order, and it is deliberate: the most committing actions
     * first (open a specific offer, act on it), invitations to rephrase last. The {@link #MAX_ACTIONS}
     * cap therefore truncates the tail, never the essential.</p>
     */
    public static List<ChatAction> actionsFor(List<ToolOutcome> outcomes) {
        List<ChatAction> actions = new ArrayList<>();

        for (ToolOutcome outcome : outcomes) {
            switch (outcome) {
                case ToolOutcome.OffersFound found when found.total() > 0 -> {
                    citeOffers(actions, found.offers());
                    actions.add(new ChatAction.Navigate(ChatLabel.with("viewAllOffers", Map.of("count", found.total())), ROUTE_SEARCH));
                    // The two most asked-for filters. One per offer attribute would turn the thread
                    // into a facet panel — that is the search page's job, not the chat's.
                    actions.add(new ChatAction.Prompt(ChatLabel.plain("onlyPermanent"), "Uniquement les offres en CDI"));
                    actions.add(new ChatAction.Prompt(ChatLabel.plain("onlyRemote"), "Uniquement les offres en teletravail"));
                }
                // Fruitless search: the two ways out are widening, or fixing preferences that are
                // too narrow. Offering nothing would leave the user facing "no results" with no door.
                case ToolOutcome.OffersFound ignored -> {
                    actions.add(new ChatAction.Prompt(ChatLabel.plain("widenSearch"), "Elargis la recherche"));
                    actions.add(new ChatAction.Navigate(ChatLabel.plain("openPreferences"), ROUTE_SETTINGS));
                }
                case ToolOutcome.MatchesFound found when found.total() > 0 -> {
                    citeOffers(actions, found.offers());
                    actions.add(new ChatAction.Navigate(ChatLabel.with("viewAllMatches", Map.of("count", found.total())), ROUTE_MATCHES));
                }
                // No match is almost always a thin profile rather than an empty corpus.
                case ToolOutcome.MatchesFound ignored -> actions.add(
                    new ChatAction.Navigate(ChatLabel.plain("completeProfile"), ROUTE_CV_BUILDER)
                );
                case ToolOutcome.ProfileMissing ignored -> actions.add(
                    new ChatAction.Navigate(ChatLabel.plain("importCv"), ROUTE_ONBOARDING)
                );
                case ToolOutcome.ApplicationsListed listed -> actions.add(
                    new ChatAction.Navigate(ChatLabel.with("viewApplications", Map.of("count", listed.total())), ROUTE_APPLICATIONS)
                );
                case ToolOutcome.CoverLetterGenerated generated -> actions.add(
                    new ChatAction.Navigate(ChatLabel.plain("openCoverLetter"), offerRoute(generated.offerId()))
                );
            }
        }

        // Actions that change something are offered only when the turn is about *one* identified
        // offer. Across three offers, "Apply" would be ambiguous: the user could not tell which one
        // goes out.
        singleSubjectOffer(outcomes).ifPresent(offerId -> {
            // Generating a letter is reversible: it is saved, not sent.
            actions.add(new ChatAction.Mutate(ChatLabel.plain("generateCoverLetter"), ChatMutation.GENERATE_COVER_LETTER, offerId, false));
            // Applying is irreversible from the user's point of view: the application goes out.
            actions.add(new ChatAction.Mutate(ChatLabel.plain("apply"), ChatMutation.APPLY, offerId, true));
        });

        return actions.size() > MAX_ACTIONS ? List.copyOf(actions.subList(0, MAX_ACTIONS)) : List.copyOf(actions);
    }

    private static void citeOffers(List<ChatAction> actions, List<ToolOutcome.CitedOffer> offers) {
        offers
            .stream()
            .limit(MAX_CITED_OFFERS)
            .forEach(offer ->
                actions.add(
                    new ChatAction.Navigate(
                        // The job title is *data*, not an interface label: it goes in as a key
                        // parameter rather than as the button's text.
                        ChatLabel.with("openOffer", Map.of("title", offer.title())),
                        offerRoute(offer.id())
                    )
                )
            );
    }

    /**
     * Route opening one specific offer.
     *
     * <p>The application has <b>no</b> offer-detail page: the {@code job-copilot} routes stop at list
     * pages. We therefore go through the search page with the id as a parameter, leaving it to
     * highlight the offer. The day a dedicated route exists, this is the only place to change.</p>
     */
    private static String offerRoute(UUID offerId) {
        return ROUTE_SEARCH + "?offerId=" + offerId;
    }

    /**
     * The single offer the turn is about, if there is only one.
     *
     * <p>A generated letter names its offer unambiguously. A search or a match only do so when they
     * returned a single result. Two tools naming two different offers yield nothing: we do not choose
     * on the user's behalf.</p>
     */
    private static Optional<UUID> singleSubjectOffer(List<ToolOutcome> outcomes) {
        Set<UUID> candidates = new LinkedHashSet<>();
        for (ToolOutcome outcome : outcomes) {
            switch (outcome) {
                case ToolOutcome.CoverLetterGenerated generated -> candidates.add(generated.offerId());
                case ToolOutcome.OffersFound found when found.total() == 1 && !found.offers().isEmpty() -> candidates.add(
                    found.offers().get(0).id()
                );
                case ToolOutcome.MatchesFound found when found.total() == 1 && !found.offers().isEmpty() -> candidates.add(
                    found.offers().get(0).id()
                );
                default -> {
                    // Other outcomes name no offer.
                }
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.iterator().next()) : Optional.empty();
    }
}
