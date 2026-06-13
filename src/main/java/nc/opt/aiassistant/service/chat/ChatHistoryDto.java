package nc.opt.aiassistant.service.chat;

public record ChatHistoryDto(String conversationId, String content, String type, java.time.Instant timestamp) {}
