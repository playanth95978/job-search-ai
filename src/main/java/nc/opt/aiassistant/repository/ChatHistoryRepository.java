package nc.opt.aiassistant.repository;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.chat.ChatHistoryDto;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
public class ChatHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ChatMemoryRepository chatMemoryRepository;

    public List<ChatHistoryDto> getChatHistory() {
        String sql = """
            SELECT conversation_id, content, type, timestamp
            FROM (
                SELECT conversation_id, content, type, timestamp,
                       ROW_NUMBER() OVER(PARTITION BY conversation_id ORDER BY timestamp DESC) as rn
                FROM spring_ai_chat_memory
                WHERE type = 'USER' AND conversation_id = ?
            ) t
            WHERE rn = 1
            ORDER BY timestamp DESC
            """;

        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow();

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new ChatHistoryDto(
                    rs.getString("conversation_id"),
                    rs.getString("content"),
                    rs.getString("type"),
                    rs.getTimestamp("timestamp") != null ? rs.getTimestamp("timestamp").toInstant() : Instant.now()
                ),
            userLogin
        );
    }

    public List<ChatHistoryDto> getChatHistoryByConversationId(String conversationId) {
        String sql = """
            SELECT conversation_id, content, type, timestamp
            FROM spring_ai_chat_memory
            WHERE conversation_id = ?
            ORDER BY timestamp ASC
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new ChatHistoryDto(
                    rs.getString("conversation_id"),
                    rs.getString("content"),
                    rs.getString("type"),
                    rs.getTimestamp("timestamp") != null ? rs.getTimestamp("timestamp").toInstant() : Instant.now()
                ),
            conversationId
        );
    }
}
