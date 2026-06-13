package nc.opt.aiassistant.service.mapper;

import nc.opt.aiassistant.domain.Conversation;
import nc.opt.aiassistant.service.dto.ConversationDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link Conversation} and its DTO {@link ConversationDTO}.
 */
@Mapper(componentModel = "spring")
public interface ConversationMapper extends EntityMapper<ConversationDTO, Conversation> {
    ConversationDTO toDto(Conversation s);

    Conversation toEntity(ConversationDTO s);
}
