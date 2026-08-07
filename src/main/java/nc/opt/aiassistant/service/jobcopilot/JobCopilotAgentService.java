package nc.opt.aiassistant.service.jobcopilot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.jobcopilot.dto.ChatEvent;
import nc.opt.aiassistant.service.jobcopilot.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Conversational Job Copilot agent. A single tool-enabled {@link ChatClient} is wired here with all
 * the job-copilot tools (job search, profile matching, CV profile, application tracking, cover-letter
 * generation, geolocation) and JDBC-backed chat memory keyed by conversation id. Tools resolve the
 * current user server-side via SecurityUtils, so the LLM never sees or supplies a user id.
 */
@Slf4j
@Service
public class JobCopilotAgentService {

    private static final String SYSTEM_PROMPT = """
        You are Job Copilot, an assistant for the New Caledonian job market (and remote/international roles).
        You help the signed-in user search job offers, match offers to their CV profile, compare locations,
        generate cover letters, and track their applications.

        Use the available tools whenever they can answer the request with real data instead of guessing:
        - search job offers, match the user's profile, read their CV profile, list/update their applications,
          generate a cover letter, or compare two locations.
        - for job offers that are NOT in the internal database (external companies, roles posted only online,
          international/remote listings), use the web search tool to look them up on the internet, then
          summarize the findings with their source links. Prefer the internal job-search tool first; fall
          back to web search when it returns nothing relevant.
        The tools already act on behalf of the signed-in user — never ask for or invent a user id.
        Be concise and practical. Answer in the user's language (French by default for local queries).
        If the user has no CV/profile yet, invite them to upload one before profile-based actions.
        """;

    private final ChatClient chatClient;

    public JobCopilotAgentService(
        @Qualifier("mistralAiChatModel") ChatModel chatModel,
        ChatMemoryRepository chatMemoryRepository,
        JobAgentToolbox jobAgentToolbox,
        ObjectProvider<ToolCallbackProvider> mcpToolCallbackProviders
    ) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(20).build();

        // Outils par défaut = outils internes (@Tool) + fournisseurs d'outils MCP externes (recherche web).
        // defaultTools(Object...) accepte un mélange hétérogène (beans @Tool, ToolCallbackProvider, collections)
        // et reste la seule API non dépréciée en Spring AI 2.0. Les serveurs MCP sont optionnels : si aucun
        // n'est configuré/joignable, l'agent démarre quand même avec ses seuls outils internes.
        List<Object> defaultTools = new ArrayList<>(Arrays.asList(jobAgentToolbox.tools()));
        mcpToolCallbackProviders.forEach(defaultTools::add);
        long mcpToolCount = mcpToolCallbackProviders
            .stream()
            .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
            .count();
        log.info("Job Copilot : {} outil(s) MCP externe(s) chargé(s)", mcpToolCount);

        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(defaultTools)
            .build();
    }

    /** Runs one turn of the conversation (blocking), letting the model call tools as needed. */
    public String chat(String conversationId, String userId, String message) {
        return chatClient
            .prompt()
            .user(message)
            .toolContext(userContext(userId))
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    }

    /**
     * Streams one turn as Server-Sent Events (start / token* / end). The {@code userId} is captured
     * on the request thread and passed via the tool context, because tool calls run on reactive
     * threads where the security ThreadLocal is not available.
     */
    public Flux<ChatEvent> stream(String conversationId, String userId, String message) {
        String id = UUID.randomUUID().toString();
        Flux<String> tokens = chatClient
            .prompt()
            .user(message)
            .toolContext(userContext(userId))
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .stream()
            .content();
        return toChunk(tokens, id);
    }

    /** Wraps a token flux into start/token/end events, batching tokens to limit SSE chatter. */
    private Flux<ChatEvent> toChunk(Flux<String> tokenFlux, String id) {
        return tokenFlux
            .bufferTimeout(20, Duration.ofMillis(100))
            .map(tokens -> String.join("", tokens))
            .filter(content -> !content.isEmpty())
            .map(content -> new ChatEvent(id, "token", content))
            .startWith(new ChatEvent(id, "start", null))
            .concatWithValues(new ChatEvent(id, "end", null))
            .onErrorResume(e -> {
                log.warn("Assistant stream failed for conversation {}: {}", id, e.getMessage());
                return Flux.just(new ChatEvent(id, "error", e.getMessage()));
            });
    }

    private Map<String, Object> userContext(String userId) {
        return userId == null || userId.isBlank() ? Map.of() : Map.of("userId", userId);
    }
}
