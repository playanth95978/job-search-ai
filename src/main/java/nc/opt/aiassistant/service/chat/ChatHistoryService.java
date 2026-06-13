package nc.opt.aiassistant.service.chat;

import java.util.UUID;
import lombok.AllArgsConstructor;
import nc.opt.aiassistant.service.ConversationService;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ChatHistoryService {

    private final ConversationService conversationService;

    public boolean verifyConversationBelongToUerLogged(UUID conversationId) {
        return conversationService.verifyConversationBelongToUerLogged(conversationId);
    }
}
