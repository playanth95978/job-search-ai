package nc.opt.aiassistant.service.jobcopilot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import java.util.UUID;

/**
 * A clickable action offered at the end of an assistant turn.
 *
 * <p><b>Shared contract.</b> The Angular front-end is served by both this backend and the Rust one,
 * so this type mirrors {@code ChatAction} in {@code server/src/services/chat_actions.rs} field for
 * field. The JSON shape is the contract: a {@code type} discriminator plus a payload. Changing one
 * side without the other silently breaks whichever backend was not updated — the front would receive
 * an action shape it cannot switch on.</p>
 *
 * <p><b>Decided by code, never by the model.</b> Actions are derived from what the agent's tools
 * actually did (see {@link ToolOutcome}). A model choosing its own buttons invents offer ids and
 * routes that do not exist.</p>
 *
 * <p><b>The turn is over.</b> These are shortcuts for the <em>next</em> turn; they suspend neither
 * the agent nor the SSE stream. Conversation history lives in Postgres, so the next turn recovers
 * the full context without any state being held between the two — which is what lets the service be
 * replicated without sticky sessions.</p>
 *
 * <p>See {@code docs/plan-actions-chatbot.md} in the Rust repository for the overall plan.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChatAction.Prompt.class, name = "prompt"),
    @JsonSubTypes.Type(value = ChatAction.Navigate.class, name = "navigate"),
    @JsonSubTypes.Type(value = ChatAction.Mutate.class, name = "mutate"),
})
public sealed interface ChatAction {
    /** The i18n key rendered on the button. */
    ChatLabel label();

    /**
     * Sends {@code text} back into the conversation as a new user turn.
     *
     * <p>Covers suggested questions, multiple-choice answers and filter refinements — all three are
     * the same mechanism seen from the front.</p>
     */
    record Prompt(ChatLabel label, String text) implements ChatAction {}

    /**
     * Opens an application route.
     *
     * <p>Always an internal path, never an absolute URL: the front passes this straight to
     * {@code navigateByUrl}.</p>
     */
    record Navigate(ChatLabel label, String route) implements ChatAction {}

    /**
     * Calls an API.
     *
     * @param offerId the offer the operation applies to, {@code null} when the operation needs none
     * @param confirm whether the user must confirm before the call is made; always {@code true} for
     *                {@link ChatMutation#APPLY}, which is irreversible from the user's point of view
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Mutate(ChatLabel label, ChatMutation operation, UUID offerId, boolean confirm) implements ChatAction {}

    /**
     * A button label: an <b>i18n key</b> and its parameters, never a sentence.
     *
     * <p>The backend builds no displayable text, consistent with the rest of the application, which
     * goes through keys everywhere. Parameters serve labels carrying a value — "View the 12 offers"
     * is the {@code viewAllOffers} key with {@code {"count": 12}}.</p>
     *
     * @param key    key in the Angular bundle, e.g. {@code chat.action.viewAllOffers}
     * @param params interpolation parameters, {@code null} when the key expects none
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatLabel(String key, Map<String, Object> params) {
        /** Prefix shared by every action key, so callers pass only the leaf. */
        private static final String PREFIX = "chat.action.";

        public static ChatLabel plain(String key) {
            return new ChatLabel(PREFIX + key, null);
        }

        public static ChatLabel with(String key, Map<String, Object> params) {
            return new ChatLabel(PREFIX + key, params);
        }
    }

    /**
     * Operations the front knows how to trigger.
     *
     * <p>A closed enum rather than a free string: this is what guarantees a received operation maps
     * to a call the front can actually make. A string would force a default branch in the template,
     * hence a button that does nothing.</p>
     */
    enum ChatMutation {
        GENERATE_COVER_LETTER,
        APPLY,
    }
}
