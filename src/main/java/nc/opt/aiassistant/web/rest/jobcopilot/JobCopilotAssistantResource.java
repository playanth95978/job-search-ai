package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.service.jobcopilot.JobCopilotAgentService;
import nc.opt.aiassistant.service.jobcopilot.dto.AssistantMessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Conversational, tool-enabled Job Copilot assistant. Each turn is scoped to a conversation id used
 * as the chat-memory key; the underlying agent can call the job-copilot tools on the user's behalf.
 */
@RestController
@RequestMapping("/api/job-copilot/assistant")
@RequiredArgsConstructor
public class JobCopilotAssistantResource {

    private final JobCopilotAgentService agentService;

    /** Starts a new assistant conversation and returns its id plus the opening reply. */
    @PostMapping("/start")
    public ResponseEntity<AssistantMessageDto> start(@RequestBody(required = false) String message) {
        String conversationId = UUID.randomUUID().toString();
        String opening = (message == null || message.isBlank())
            ? "Bonjour ! Présente-toi en une phrase et propose 2-3 actions que tu peux faire (rechercher des offres, matcher mon profil, suivre mes candidatures…)."
            : message;
        String reply = agentService.chat(conversationId, opening);
        return ResponseEntity.ok(new AssistantMessageDto(conversationId, reply));
    }

    /** Sends a user message within an existing conversation and returns the assistant's reply. */
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<AssistantMessageDto> message(@PathVariable String conversationId, @RequestBody String message) {
        String reply = agentService.chat(conversationId, message);
        return ResponseEntity.ok(new AssistantMessageDto(conversationId, reply));
    }
}
