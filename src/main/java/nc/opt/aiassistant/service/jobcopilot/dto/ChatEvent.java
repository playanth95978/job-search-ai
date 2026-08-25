package nc.opt.aiassistant.service.jobcopilot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One Server-Sent Event of a streamed assistant reply.
 *
 * <p>{@code actions} rides in its own frame rather than inside {@code content}: a JSON array encoded
 * into a string would force the front to parse it defensively, and a malformed payload would surface
 * as a broken button instead of a typed error. Same envelope as the Rust backend, which serves the
 * same front-end.</p>
 *
 * @param id      stable id of the streamed message (same across its start/token/end events)
 * @param type    "start", "token", "ping" (keep-alive, no content), "actions", "end" or "error"
 * @param content token text for "token" events (error message for "error"); null otherwise
 * @param actions the buttons to show, on an "actions" frame only; null otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEvent(String id, String type, String content, List<ChatAction> actions) {
    /** Text-carrying frame: start, token, ping, end, error. */
    public static ChatEvent of(String id, String type, String content) {
        return new ChatEvent(id, type, content, null);
    }

    /**
     * Frame carrying the end-of-turn actions.
     *
     * <p>Emitted <b>after the last token and before {@code end}</b>: the front must have finished
     * painting the text before the buttons appear, otherwise they show up mid-sentence while the
     * reply is still being written.</p>
     */
    public static ChatEvent actions(String id, List<ChatAction> actions) {
        return new ChatEvent(id, "actions", null, actions);
    }
}
