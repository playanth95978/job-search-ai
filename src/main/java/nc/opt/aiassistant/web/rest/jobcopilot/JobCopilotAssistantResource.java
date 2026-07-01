package nc.opt.aiassistant.web.rest.jobcopilot;

import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.JobCopilotAgentService;
import nc.opt.aiassistant.service.jobcopilot.dto.AssistantMessageDto;
import nc.opt.aiassistant.service.jobcopilot.dto.ChatEvent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Conversational, tool-enabled Job Copilot assistant. Each turn is scoped to a conversation id used
 * as the chat-memory key; the underlying agent can call the job-copilot tools on the user's behalf.
 */
@RestController
@RequestMapping("/api/job-copilot/assistant")
@RequiredArgsConstructor
public class JobCopilotAssistantResource {

    private final JobCopilotAgentService agentService;

    /** Sends a user message within an existing conversation and returns the assistant's reply (blocking). */
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<AssistantMessageDto> message(@PathVariable String conversationId, @RequestBody String message) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        String reply = agentService.chat(conversationId, userId, message);
        return ResponseEntity.ok(new AssistantMessageDto(conversationId, reply));
    }

    /**
     * Streams the assistant's reply as Server-Sent Events (start / token* / end). The current user is
     * resolved here on the request thread and forwarded to the agent's tool context.
     */
    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEvent> stream(@PathVariable String conversationId, @RequestBody String message) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return agentService.stream(conversationId, userId, message);
    }
}
