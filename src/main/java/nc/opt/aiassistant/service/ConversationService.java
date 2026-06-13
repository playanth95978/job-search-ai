package nc.opt.aiassistant.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.Conversation;
import nc.opt.aiassistant.domain.enumeration.TypeChat;
import nc.opt.aiassistant.repository.ConversationRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.dto.ConversationDTO;
import nc.opt.aiassistant.service.mapper.ConversationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link Conversation}.
 */
@Service
@Transactional
@Slf4j
public class ConversationService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;

    private final ConversationMapper conversationMapper;

    @PersistenceContext
    private EntityManager em;

    public ConversationService(ConversationRepository conversationRepository, ConversationMapper conversationMapper) {
        this.conversationRepository = conversationRepository;
        this.conversationMapper = conversationMapper;
    }

    @Transactional
    public ConversationDTO createIfNotExists(ConversationDTO conversationDTO) {
        return conversationMapper.toDto(
            conversationRepository.findById(conversationDTO.getId()).orElseGet(() -> {
                Conversation conv = conversationMapper.toEntity(conversationDTO);
                conv.setNew(true);
                // On réinitialise la version pour éviter qu'Hibernate pense qu'elle existe déjà
                conv.setVersion(null);
                conv.setUserId(SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("User not found")));

                // Si l'id est null (ne devrait pas arriver avec createIfNotExists qui s'attend à un ID client)
                if (conv.getId() == null) {
                    conv.setId(UUID.randomUUID());
                    conv.setNew(true);
                }

                return conversationRepository.save(conv);
            })
        );
    }

    /**
     * Save a conversation.
     *
     * @param conversationDTO the entity to save.
     * @return the persisted entity.
     */
    public ConversationDTO save(ConversationDTO conversationDTO) {
        LOG.debug("Request to save Conversation : {}", conversationDTO);

        Conversation conversation = conversationMapper.toEntity(conversationDTO);
        conversation.setUserId(SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("User not found")));

        // Si l'id est présent, on vérifie si elle existe pour positionner isNew correctement
        if (conversation.getId() != null) {
            if (conversationRepository.existsById(conversation.getId())) {
                conversation.markNotNew();
            } else {
                conversation.setNew(true);
            }
        }

        conversation = conversationRepository.save(conversation);
        return conversationMapper.toDto(conversation);
    }

    /**
     * Update a conversation.
     *
     * @param conversationDTO the entity to save.
     * @return the persisted entity.
     */
    public ConversationDTO update(ConversationDTO conversationDTO) {
        LOG.debug("Request to update Conversation : {}", conversationDTO);
        Conversation conversation = conversationMapper.toEntity(conversationDTO);
        conversation.markNotNew(); // Pour un update, on s'attend à ce qu'elle existe
        conversation = conversationRepository.save(conversation);
        return conversationMapper.toDto(conversation);
    }

    /**
     * Partially update a conversation.
     *
     * @param conversationDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ConversationDTO> partialUpdate(ConversationDTO conversationDTO) {
        LOG.debug("Request to partially update Conversation : {}", conversationDTO);

        return conversationRepository
            .findById(conversationDTO.getId())
            .map(existingConversation -> {
                conversationMapper.partialUpdate(existingConversation, conversationDTO);
                existingConversation.markNotNew(); // On marque comme non nouveau car chargé depuis la base
                return existingConversation;
            })
            .map(conversationRepository::save)
            .map(conversationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ConversationDTO> findAllByUserLogin(Pageable pageable) {
        return findAllByUserLogin(pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<ConversationDTO> findAllByUserLogin(Pageable pageable, TypeChat typeChat) {
        LOG.debug("Request to get all Conversations (typeChat={})", typeChat);
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("User not found"));
        Page<Conversation> page =
            typeChat == null
                ? conversationRepository.findAllByUserId(userId, pageable)
                : conversationRepository.findAllByUserIdAndTypeChat(userId, typeChat, pageable);
        return page.map(conversationMapper::toDto);
    }

    /**
     * Get one conversation by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDTO> findOne(UUID id) {
        LOG.debug("Request to get Conversation : {}", id);
        return conversationRepository
            .findConversationByIdAndUserId(
                id,
                SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("User not found"))
            )
            .map(conversationMapper::toDto);
    }

    /**
     * Delete the conversation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(UUID id) {
        LOG.debug("Request to delete Conversation : {}", id);
        conversationRepository.deleteById(id);
    }

    public boolean verifyConversationBelongToUerLogged(UUID conversationId) {
        return conversationRepository.existsByIdAndUserId(
            conversationId,
            SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("User not found"))
        );
    }
}
