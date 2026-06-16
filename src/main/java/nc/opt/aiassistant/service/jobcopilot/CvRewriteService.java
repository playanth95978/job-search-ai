package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.jobcopilot.dto.RewriteRequestDto;
import nc.opt.aiassistant.service.jobcopilot.dto.RewriteResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Rewrites a single CV free-text field into three styles (professional, concise, ATS-optimized)
 * in one LLM call — cheaper given the Mistral client rate limit.
 */
@Slf4j
@Service
public class CvRewriteService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public CvRewriteService(@Qualifier("mistralAiChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    public RewriteResponseDto rewrite(RewriteRequestDto request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            return new RewriteResponseDto("", "", "");
        }

        String context = buildContext(request);
        String prompt = """
            You are an expert CV/resume writer. Rewrite the %s text below in THREE variants.
            Keep the SAME LANGUAGE as the input text. Do not invent facts; only rephrase and improve.

            - "professional": polished, impactful, strong action verbs.
            - "concise": shorter, straight to the point, no filler.
            - "ats": optimized for Applicant Tracking Systems (relevant keywords, plain phrasing).
            %s
            Return ONLY valid JSON with exactly these keys:
            { "professional": "string", "concise": "string", "ats": "string" }

            Text to rewrite:
            %s
            """.formatted(safeKind(request.kind()), context, text);

        String json = cleanJsonResponse(chatClient.prompt().user(prompt).call().content());
        try {
            return objectMapper.readValue(json, RewriteResponseDto.class);
        } catch (Exception e) {
            log.error("Failed to parse rewrite response: {}", e.getMessage());
            // Fallback: return the original text so the UI still shows something usable.
            return new RewriteResponseDto(text, text, text);
        }
    }

    private String buildContext(RewriteRequestDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.jobTitle() != null && !r.jobTitle().isBlank()) {
            sb.append("Target role: ").append(r.jobTitle()).append(". ");
        }
        if (r.company() != null && !r.company().isBlank()) {
            sb.append("Company: ").append(r.company()).append(". ");
        }
        return sb.isEmpty() ? "" : "Context: " + sb + "\n";
    }

    private String safeKind(String kind) {
        return switch (kind == null ? "" : kind) {
            case "summary" -> "professional summary";
            case "experience" -> "work experience description";
            case "project" -> "project description";
            default -> "CV";
        };
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        raw = raw.strip();
        if (raw.startsWith("```json")) raw = raw.substring(7);
        if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        return raw.strip();
    }
}
