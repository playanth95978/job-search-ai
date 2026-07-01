package nc.opt.aiassistant.service.jobcopilot.dto;

/** A single assistant turn: the conversation it belongs to and the assistant's reply text. */
public record AssistantMessageDto(String conversationId, String message) {}
