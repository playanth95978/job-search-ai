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
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
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

    private static final String ENTITY_NAME = "conversation";

    private final ConversationRepository conversationRepository;

    private final ConversationMapper conversationMapper;

    @PersistenceContext
    private EntityManager em;

    public ConversationService(ConversationRepository conversationRepository, ConversationMapper conversationMapper) {
        this.conversationRepository = conversationRepository;
        this.conversationMapper = conversationMapper;
    }

    private String currentUserId() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new BadRequestAlertException("Current user login not found", ENTITY_NAME, "nologin")
        );
    }

    /**
     * Barrière de propriété : toute écriture ciblant un id doit passer par ici. On renvoie la même
     * erreur pour « inexistante » et « appartient à quelqu'un d'autre », afin qu'un appelant ne
     * puisse pas sonder l'existence d'un id qui n'est pas le sien.
     */
    private void requireOwned(UUID id, String userId) {
        if (id == null || !conversationRepository.existsByIdAndUserId(id, userId)) {
            throw new BadRequestAlertException("Conversation not found", ENTITY_NAME, "idnotfound");
        }
    }

    @Transactional
    public ConversationDTO createIfNotExists(ConversationDTO conversationDTO) {
        String userId = currentUserId();

        if (conversationDTO.getId() != null) {
            Optional<Conversation> own = conversationRepository.findConversationByIdAndUserId(conversationDTO.getId(), userId);
            if (own.isPresent()) {
                return conversationMapper.toDto(own.get());
            }
            // L'id existe mais appartient à un autre utilisateur. On ne le révèle pas, et surtout on
            // ne retombe pas dans la création : la clé primaire étant fournie par le client, insérer
            // une entité neuve sur cet id écraserait la conversation de l'autre utilisateur.
            if (conversationRepository.existsById(conversationDTO.getId())) {
                throw new BadRequestAlertException("Conversation not found", ENTITY_NAME, "idnotfound");
            }
        }

        Conversation conv = conversationMapper.toEntity(conversationDTO);
        conv.setNew(true);
        // On réinitialise la version pour éviter qu'Hibernate pense qu'elle existe déjà
        conv.setVersion(null);
        // Le userId ne vient jamais du client : il est repositionné depuis le contexte de sécurité.
        conv.setUserId(userId);

        // Si l'id est null (ne devrait pas arriver avec createIfNotExists qui s'attend à un ID client)
        if (conv.getId() == null) {
            conv.setId(UUID.randomUUID());
            conv.setNew(true);
        }

        return conversationMapper.toDto(conversationRepository.save(conv));
    }

    /**
     * Save a conversation.
     *
     * @param conversationDTO the entity to save.
     * @return the persisted entity.
     */
    public ConversationDTO save(ConversationDTO conversationDTO) {
        LOG.debug("Request to save Conversation : {}", conversationDTO);

        String userId = currentUserId();

        Conversation conversation = conversationMapper.toEntity(conversationDTO);
        conversation.setUserId(userId);

        // Si l'id est présent, on vérifie si elle existe pour positionner isNew correctement
        if (conversation.getId() != null) {
            if (conversationRepository.existsById(conversation.getId())) {
                // La ligne existe déjà : on ne la reprend que si elle appartient à l'appelant.
                requireOwned(conversation.getId(), userId);
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
        String userId = currentUserId();
        requireOwned(conversationDTO.getId(), userId);

        Conversation conversation = conversationMapper.toEntity(conversationDTO);
        // Le userId ne vient jamais du client : sans cela, un appelant pourrait s'attribuer la
        // conversation d'un autre en postant son propre login dans le corps de la requête.
        conversation.setUserId(userId);
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
        if (conversationDTO.getId() == null) {
            return Optional.empty();
        }
        String userId = currentUserId();

        return conversationRepository
            .findConversationByIdAndUserId(conversationDTO.getId(), userId)
            .map(existingConversation -> {
                conversationMapper.partialUpdate(existingConversation, conversationDTO);
                // Le userId ne vient jamais du client : le patch ne doit pas pouvoir réattribuer
                // la conversation à un autre utilisateur.
                existingConversation.setUserId(userId);
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
        return conversationRepository.findConversationByIdAndUserId(id, currentUserId()).map(conversationMapper::toDto);
    }

    /**
     * Delete the conversation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(UUID id) {
        LOG.debug("Request to delete Conversation : {}", id);
        requireOwned(id, currentUserId());
        conversationRepository.deleteById(id);
    }

    public boolean verifyConversationBelongToUerLogged(UUID conversationId) {
        return conversationId != null && conversationRepository.existsByIdAndUserId(conversationId, currentUserId());
    }
}
