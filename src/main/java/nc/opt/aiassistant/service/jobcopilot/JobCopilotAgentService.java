package nc.opt.aiassistant.service.jobcopilot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.ai.AiErrors;
import nc.opt.aiassistant.service.ai.MistralRateLimiter;
import nc.opt.aiassistant.service.jobcopilot.dto.ChatEvent;
import nc.opt.aiassistant.service.jobcopilot.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Conversational Job Copilot agent. A single tool-enabled {@link ChatClient} is wired here with all
 * the job-copilot tools (job search, profile matching, CV profile, application tracking, cover-letter
 * generation, geolocation) and JDBC-backed chat memory keyed by conversation id. Tools resolve the
 * current user server-side via SecurityUtils, so the LLM never sees or supplies a user id.
 *
 * <p>Calls are throttled by {@link MistralRateLimiter} and, when the provider still answers 429
 * (free-tier quota), the turn is replayed on the smaller/cheaper Mistral model rather than failing.
 */
@Slf4j
@Service
public class JobCopilotAgentService {

    private static final String SYSTEM_PROMPT = """
        You are Job Copilot, an assistant for the New Caledonian job market (and remote/international roles).
        You help the signed-in user search job offers, match offers to their CV profile, compare locations,
        generate cover letters, and track their applications.

        Use the available tools whenever they can answer the request with real data instead of guessing:
        - when the user asks for the *best offers for them*, "what should I apply to", or why an offer fits
          their profile, use the profile-matching tool with their own words as the query: it runs the job
          search engine and scores each offer against their CV (skills, location, salary, preferences).
        - search job offers, read their CV profile, list/update their applications, generate a cover letter,
          or compare two locations.
        - for job offers that are NOT in the internal database (external companies, roles posted only online,
          international/remote listings), use the web search tool to look them up on the internet, then
          summarize the findings with their source links. Prefer the internal job-search tool first; fall
          back to web search when it returns nothing relevant.
        The tools already act on behalf of the signed-in user — never ask for or invent a user id.
        Base every score, skill, salary, location and requirement you mention strictly on the tool output:
        never invent or round a match score, a missing skill, a salary or a company detail.
        Be concise and practical. Answer in the user's language (French by default for local queries).
        If the user has no CV/profile yet, invite them to upload one before profile-based actions.
        """;

    /** Sent while the model is still thinking, so proxies do not close an idle SSE connection. */
    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(15);

    private final ChatClient chatClient;
    private final ChatClient fallbackChatClient;
    private final MistralRateLimiter rateLimiter;
    private final Duration streamTimeout;

    public JobCopilotAgentService(
        @Qualifier("mistralAiChatModel") ChatModel chatModel,
        @Qualifier("mistralSmallChatOptions") ChatOptions.Builder fallbackOptions,
        ChatMemoryRepository chatMemoryRepository,
        JobAgentToolbox jobAgentToolbox,
        ObjectProvider<ToolCallbackProvider> mcpToolCallbackProviders,
        MistralRateLimiter rateLimiter,
        @Value("${spring.ai.mistral.stream-timeout:180s}") Duration streamTimeout
    ) {
        this.rateLimiter = rateLimiter;
        this.streamTimeout = streamTimeout;
        ChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(20).build();

        // Outils par défaut = outils internes (@Tool) + fournisseurs d'outils MCP externes (recherche web).
        // defaultTools(Object...) accepte un mélange hétérogène (beans @Tool, ToolCallbackProvider, collections)
        // et reste la seule API non dépréciée en Spring AI 2.0. Les serveurs MCP sont optionnels : si aucun
        // n'est configuré/joignable, l'agent démarre quand même avec ses seuls outils internes.
        List<Object> defaultTools = new ArrayList<>(Arrays.asList(jobAgentToolbox.tools()));
        int mcpToolCount = 0;
        for (ToolCallbackProvider provider : mcpToolCallbackProviders) {
            try {
                mcpToolCount += provider.getToolCallbacks().length;
                defaultTools.add(provider);
            } catch (RuntimeException e) {
                // Serveur MCP injoignable ou en erreur : on dégrade proprement plutôt que d'empêcher
                // l'agent de démarrer. La recherche web sera simplement indisponible.
                log.warn("Job Copilot : fournisseur d'outils MCP ignoré ({})", e.getMessage());
            }
        }
        log.info("Job Copilot : {} outil(s) MCP externe(s) chargé(s)", mcpToolCount);

        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(defaultTools)
            .build();

        // Même agent (mêmes outils, même mémoire), seul le modèle change : utilisé uniquement quand le
        // modèle principal renvoie 429. Les options du client priment sur celles du ChatModel.
        this.fallbackChatClient = ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(defaultTools)
            .defaultOptions(fallbackOptions)
            .build();
    }

    /** Runs one turn of the conversation (blocking), letting the model call tools as needed. */
    public String chat(String conversationId, String userId, String message) {
        if (!rateLimiter.acquire()) {
            log.info("Job Copilot : quota local atteint, bascule directe sur le modèle de repli");
            return call(fallbackChatClient, conversationId, userId, message);
        }
        try {
            return call(chatClient, conversationId, userId, message);
        } catch (RuntimeException e) {
            if (!AiErrors.isRateLimited(e)) {
                throw e;
            }
            log.warn("Job Copilot : 429 sur le modèle principal, rejeu sur le modèle de repli");
            return call(fallbackChatClient, conversationId, userId, message);
        }
    }

    private String call(ChatClient client, String conversationId, String userId, String message) {
        return client
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
     *
     * <p>The stream is throttled before it starts, kept alive with periodic pings while the model is
     * thinking or calling tools, and replayed on the fallback model if the provider answers 429
     * <em>before</em> the first token — once text has been emitted, replaying would duplicate it.
     */
    public Flux<ChatEvent> stream(String conversationId, String userId, String message) {
        String id = UUID.randomUUID().toString();
        AtomicBoolean emitted = new AtomicBoolean(false);

        Flux<ChatEvent> body = rateLimiter
            .acquireReactive()
            .thenMany(tokens(chatClient, conversationId, userId, message, emitted))
            .onErrorResume(e -> {
                if (!AiErrors.isRateLimited(e) || emitted.get()) {
                    return Flux.error(e);
                }
                log.warn("Job Copilot : 429 sur le modèle principal, rejeu du flux sur le modèle de repli");
                return tokens(fallbackChatClient, conversationId, userId, message, emitted);
            })
            .transform(tokenFlux -> toChunk(tokenFlux, id));

        return keepAlive(body, id);
    }

    /**
     * Raw token flux for one turn. {@code emitted} flips on the very first token — before any
     * buffering — so the 429 fallback can tell "failed before answering" from "failed mid-answer",
     * where replaying would duplicate the text already sent to the client.
     */
    private Flux<String> tokens(ChatClient client, String conversationId, String userId, String message, AtomicBoolean emitted) {
        return Flux.defer(() ->
            client
                .prompt()
                .user(message)
                .toolContext(userContext(userId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(token -> emitted.set(true))
        );
    }

    /** Wraps a token flux into start/token/end events, batching tokens to limit SSE chatter. */
    private Flux<ChatEvent> toChunk(Flux<String> tokenFlux, String id) {
        return tokenFlux
            .bufferTimeout(20, Duration.ofMillis(100))
            .map(tokens -> String.join("", tokens))
            .filter(content -> !content.isEmpty())
            .map(content -> new ChatEvent(id, "token", content))
            .startWith(new ChatEvent(id, "start", null))
            .timeout(streamTimeout)
            .concatWithValues(new ChatEvent(id, "end", null))
            .onErrorResume(e -> {
                log.warn("Assistant stream failed for conversation {}: {}", id, e.getMessage());
                return Flux.just(new ChatEvent(id, "error", userMessage(e)), new ChatEvent(id, "end", null));
            });
    }

    /**
     * Merges periodic empty {@code ping} events into the reply so intermediaries (nginx, load
     * balancers) see traffic while the model is thinking or running tools. Pings carry no content and
     * stop as soon as the reply completes.
     */
    private Flux<ChatEvent> keepAlive(Flux<ChatEvent> body, String id) {
        Sinks.Empty<Void> done = Sinks.empty();
        Flux<ChatEvent> pings = Flux.interval(KEEP_ALIVE_INTERVAL, KEEP_ALIVE_INTERVAL)
            .map(i -> new ChatEvent(id, "ping", null))
            .takeUntilOther(done.asMono());
        // doFinally (et non doOnComplete) : les pings doivent aussi s'arrêter si le client se déconnecte.
        return body.doFinally(signal -> done.tryEmitEmpty()).mergeWith(pings);
    }

    /** Turns a provider failure into something the user can act on, without leaking internals. */
    private String userMessage(Throwable e) {
        if (AiErrors.isRateLimited(e)) {
            return "Le service d'IA est momentanément saturé (quota atteint). Merci de réessayer dans quelques instants.";
        }
        return "Une erreur est survenue pendant la génération de la réponse. Merci de réessayer.";
    }

    private Map<String, Object> userContext(String userId) {
        return userId == null || userId.isBlank() ? Map.of() : Map.of("userId", userId);
    }
}
