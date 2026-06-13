package nc.opt.aiassistant.web.rest;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import nc.opt.aiassistant.repository.ChatHistoryRepository;
import nc.opt.aiassistant.service.chat.ChatHistoryDto;
import nc.opt.aiassistant.service.chat.ChatHistoryService;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-history")
@Validated
@AllArgsConstructor
public class ChatHistoryResource {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatHistoryService chatHistoryService;

    @GetMapping(value = "/history")
    public List<ChatHistoryDto> getChatHistory() {
        return chatHistoryRepository.getChatHistory();
    }

    @GetMapping(value = "/history/{id}")
    public List<ChatHistoryDto> getChatHistory(@PathVariable String id) {
        if (chatHistoryService.verifyConversationBelongToUerLogged(UUID.fromString(id))) {
            return chatHistoryRepository.getChatHistoryByConversationId(id);
        } else {
            throw new BadRequestAlertException("Conversation not found", "conversation", "conversation.notfound");
        }
    }
}
