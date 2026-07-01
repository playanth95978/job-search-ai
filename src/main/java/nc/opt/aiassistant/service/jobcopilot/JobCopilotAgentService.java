package nc.opt.aiassistant.service.jobcopilot;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.jobcopilot.tools.ApplicationTrackingTool;
import nc.opt.aiassistant.service.jobcopilot.tools.CoverLetterTool;
import nc.opt.aiassistant.service.jobcopilot.tools.CvParsingTool;
import nc.opt.aiassistant.service.jobcopilot.tools.GeoLocationTool;
import nc.opt.aiassistant.service.jobcopilot.tools.JobSearchTool;
import nc.opt.aiassistant.service.jobcopilot.tools.MatchingTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
        The tools already act on behalf of the signed-in user — never ask for or invent a user id.
        Be concise and practical. Answer in the user's language (French by default for local queries).
        If the user has no CV/profile yet, invite them to upload one before profile-based actions.
        """;

    private final ChatClient chatClient;

    public JobCopilotAgentService(
        @Qualifier("mistralAiChatModel") ChatModel chatModel,
        ChatMemoryRepository chatMemoryRepository,
        JobSearchTool jobSearchTool,
        MatchingTool matchingTool,
        CvParsingTool cvParsingTool,
        ApplicationTrackingTool applicationTrackingTool,
        CoverLetterTool coverLetterTool,
        GeoLocationTool geoLocationTool
    ) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(20).build();
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(jobSearchTool, matchingTool, cvParsingTool, applicationTrackingTool, coverLetterTool, geoLocationTool)
            .build();
    }

    /** Runs one turn of the conversation, letting the model call tools as needed. */
    public String chat(String conversationId, String message) {
        return chatClient
            .prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    }
}
