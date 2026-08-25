package nc.opt.aiassistant.service.jobcopilot.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nc.opt.aiassistant.service.jobcopilot.dto.ChatAction.ChatMutation;
import nc.opt.aiassistant.service.jobcopilot.dto.ToolOutcome.CitedOffer;
import org.junit.jupiter.api.Test;

/**
 * Mirror of the Rust {@code chat_actions} test module. Both backends serve the same Angular
 * front-end, so the same conversation must offer the same buttons whichever one answered — these
 * assertions are what keeps the two in step.
 */
class ChatActionFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CitedOffer offer(String title) {
        return new CitedOffer(UUID.randomUUID(), title);
    }

    private static List<String> keys(List<ChatAction> actions) {
        return actions
            .stream()
            .map(action -> action.label().key())
            .toList();
    }

    @Test
    void noToolCalledMeansNoButton() {
        // A purely conversational answer must not sprout buttons: they would suggest an action
        // nothing in the turn justifies.
        assertThat(ChatActionFactory.actionsFor(List.of())).isEmpty();
    }

    @Test
    void aFruitfulSearchOffersTheOffersThenTheListThenTheFilters() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(
            List.of(new ToolOutcome.OffersFound(List.of(offer("Developpeur"), offer("Devops"), offer("Data")), 12))
        );

        assertThat(keys(actions)).containsExactly(
            "chat.action.openOffer",
            "chat.action.openOffer",
            "chat.action.openOffer",
            "chat.action.viewAllOffers",
            "chat.action.onlyPermanent",
            "chat.action.onlyRemote"
        );
    }

    @Test
    void theTotalIsAParameterNotPartOfTheLabel() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(List.of(new ToolOutcome.OffersFound(List.of(offer("Dev")), 12)));

        ChatAction viewAll = actions
            .stream()
            .filter(a -> a.label().key().endsWith("viewAllOffers"))
            .findFirst()
            .orElseThrow();

        assertThat(viewAll.label().params()).isEqualTo(Map.of("count", 12));
    }

    @Test
    void citedOffersAreCappedAtThree() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(
            List.of(new ToolOutcome.OffersFound(List.of(offer("a"), offer("b"), offer("c"), offer("d"), offer("e")), 5))
        );

        assertThat(
            keys(actions)
                .stream()
                .filter(k -> k.endsWith("openOffer"))
                .count()
        ).isEqualTo(3);
    }

    @Test
    void anEmptySearchOffersAWayOut() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(List.of(new ToolOutcome.OffersFound(List.of(), 0)));

        assertThat(keys(actions)).containsExactly("chat.action.widenSearch", "chat.action.openPreferences");
    }

    @Test
    void aMissingProfilePointsToCvImport() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(List.of(new ToolOutcome.ProfileMissing()));

        assertThat(keys(actions)).containsExactly("chat.action.importCv");
        assertThat(actions.get(0)).isInstanceOf(ChatAction.Navigate.class);
        assertThat(((ChatAction.Navigate) actions.get(0)).route()).isEqualTo(ChatActionFactory.ROUTE_ONBOARDING);
    }

    @Test
    void aSingleOfferUnlocksTheMutatingActions() {
        CitedOffer target = offer("Developpeur backend");
        List<ChatAction> actions = ChatActionFactory.actionsFor(List.of(new ToolOutcome.OffersFound(List.of(target), 1)));

        List<ChatAction.Mutate> mutations = actions
            .stream()
            .filter(ChatAction.Mutate.class::isInstance)
            .map(ChatAction.Mutate.class::cast)
            .toList();

        assertThat(mutations).hasSize(2);
        assertThat(mutations).allSatisfy(m -> assertThat(m.offerId()).isEqualTo(target.id()));
        assertThat(mutations.get(0).operation()).isEqualTo(ChatMutation.GENERATE_COVER_LETTER);
        assertThat(mutations.get(0).confirm()).isFalse();
        assertThat(mutations.get(1).operation()).isEqualTo(ChatMutation.APPLY);
        assertThat(mutations.get(1).confirm()).isTrue();
    }

    @Test
    void severalOffersUnlockNoMutatingAction() {
        // "Apply" across three offers means nothing: the user could not tell which one goes out.
        List<ChatAction> actions = ChatActionFactory.actionsFor(List.of(new ToolOutcome.OffersFound(List.of(offer("a"), offer("b")), 2)));

        assertThat(actions).noneMatch(ChatAction.Mutate.class::isInstance);
    }

    @Test
    void twoToolsNamingTwoDifferentOffersUnlockNothing() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(
            List.of(new ToolOutcome.OffersFound(List.of(offer("a")), 1), new ToolOutcome.CoverLetterGenerated(UUID.randomUUID()))
        );

        assertThat(actions).noneMatch(ChatAction.Mutate.class::isInstance);
    }

    @Test
    void theActionCountIsCapped() {
        List<ChatAction> actions = ChatActionFactory.actionsFor(
            List.of(
                new ToolOutcome.OffersFound(List.of(offer("a"), offer("b"), offer("c")), 30),
                new ToolOutcome.MatchesFound(List.of(offer("d"), offer("e"), offer("f")), 30)
            )
        );

        assertThat(actions).hasSize(ChatActionFactory.MAX_ACTIONS);
    }

    @Test
    void everyRouteStaysInternal() {
        // The front hands `route` to `navigateByUrl`: an absolute URL would redirect out of the app.
        List<ChatAction> actions = ChatActionFactory.actionsFor(
            List.of(
                new ToolOutcome.OffersFound(List.of(offer("a")), 1),
                new ToolOutcome.ProfileMissing(),
                new ToolOutcome.ApplicationsListed(3),
                new ToolOutcome.CoverLetterGenerated(UUID.randomUUID())
            )
        );

        assertThat(actions)
            .filteredOn(ChatAction.Navigate.class::isInstance)
            .map(ChatAction.Navigate.class::cast)
            .allSatisfy(navigate -> {
                assertThat(navigate.route()).startsWith("/job-copilot/");
                assertThat(navigate.route()).doesNotContain("//");
            });
    }

    @Test
    void theJsonShapeCarriesTheDiscriminatorAndCamelCase() throws Exception {
        // The front discriminates on `type` and reads `offerId`: that is the contract, so it is tested.
        UUID id = UUID.randomUUID();
        JsonNode json = MAPPER.valueToTree(new ChatAction.Mutate(ChatAction.ChatLabel.plain("apply"), ChatMutation.APPLY, id, true));

        assertThat(json.get("type").asText()).isEqualTo("mutate");
        assertThat(json.get("operation").asText()).isEqualTo("APPLY");
        assertThat(json.get("offerId").asText()).isEqualTo(id.toString());
        assertThat(json.get("confirm").asBoolean()).isTrue();
        assertThat(json.get("label").get("key").asText()).isEqualTo("chat.action.apply");
        // A parameterless label must not expose an empty object: the front would interpolate nothing.
        assertThat(json.get("label").has("params")).isFalse();
    }

    @Test
    void theActionsFrameIsShapedForTheFront() throws Exception {
        JsonNode json = MAPPER.valueToTree(
            ChatEvent.actions("msg-1", List.of(new ChatAction.Prompt(ChatAction.ChatLabel.plain("widenSearch"), "Elargis")))
        );

        assertThat(json.get("type").asText()).isEqualTo("actions");
        assertThat(json.has("content")).isFalse();
        assertThat(json.get("actions").get(0).get("type").asText()).isEqualTo("prompt");
    }
}
