package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * A single assistant turn: the conversation it belongs to, the assistant's reply text, and the
 * clickable actions offered afterwards.
 *
 * <p>{@code actions} is empty for a purely conversational answer — buttons would suggest an action
 * nothing in the turn justifies. Same shape as the Rust backend's {@code AssistantMessageDto}: both
 * serve the same Angular front-end.</p>
 */
public record AssistantMessageDto(String conversationId, String message, List<ChatAction> actions) {
    /** Reply carrying no action, the common case. */
    public static AssistantMessageDto of(String conversationId, String message) {
        return new AssistantMessageDto(conversationId, message, List.of());
    }
}
