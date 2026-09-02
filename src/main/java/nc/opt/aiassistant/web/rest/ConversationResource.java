package nc.opt.aiassistant.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.enumeration.TypeChat;
import nc.opt.aiassistant.service.ConversationService;
import nc.opt.aiassistant.service.dto.ConversationDTO;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link nc.opt.aiassistant.domain.Conversation}.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationResource {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationResource.class);

    private static final String ENTITY_NAME = "conversation";
    private final ConversationService conversationService;

    @Value("${jhipster.clientApp.name:aiassistant}")
    private String applicationName;

    public ConversationResource(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * {@code POST  /conversations} : Create a new conversation.
     *
     * @param conversationDTO the conversationDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new conversationDTO, or with status {@code 400 (Bad Request)} if the conversation has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("/new")
    public ResponseEntity<ConversationDTO> createNewConversation(@Valid @RequestBody ConversationDTO conversationDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save Conversation : {}", conversationDTO);
        conversationDTO = conversationService.createIfNotExists(conversationDTO);
        return ResponseEntity.created(new URI("/api/conversations/" + conversationDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, conversationDTO.getId().toString()))
            .body(conversationDTO);
    }

    /**
     * {@code POST  /conversations} : Create a new conversation.
     *
     * @param conversationDTO the conversationDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new conversationDTO, or with status {@code 400 (Bad Request)} if the conversation has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ConversationDTO> createConversation(@Valid @RequestBody ConversationDTO conversationDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save Conversation : {}", conversationDTO);
        if (conversationDTO.getId() != null) {
            throw new BadRequestAlertException("A new conversation cannot already have an ID", ENTITY_NAME, "idexists");
        }
        conversationDTO = conversationService.save(conversationDTO);
        return ResponseEntity.created(new URI("/api/conversations/" + conversationDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, conversationDTO.getId().toString()))
            .body(conversationDTO);
    }

    /**
     * {@code PUT  /conversations/:id} : Updates an existing conversation.
     *
     * @param id the id of the conversationDTO to save.
     * @param conversationDTO the conversationDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated conversationDTO,
     * or with status {@code 400 (Bad Request)} if the conversationDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the conversationDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ConversationDTO> updateConversation(
        @PathVariable(value = "id", required = false) final UUID id,
        @Valid @RequestBody ConversationDTO conversationDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Conversation : {}, {}", id, conversationDTO);
        if (conversationDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, conversationDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Propriété, pas seulement existence : `existsById` laissait n'importe quel utilisateur
        // authentifié écraser la conversation d'un autre en connaissant son UUID.
        if (!conversationService.verifyConversationBelongToUerLogged(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        conversationDTO = conversationService.update(conversationDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, conversationDTO.getId().toString()))
            .body(conversationDTO);
    }

    /**
     * {@code PATCH  /conversations/:id} : Partial updates given fields of an existing conversation, field will ignore if it is null
     *
     * @param id the id of the conversationDTO to save.
     * @param conversationDTO the conversationDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated conversationDTO,
     * or with status {@code 400 (Bad Request)} if the conversationDTO is not valid,
     * or with status {@code 404 (Not Found)} if the conversationDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the conversationDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ConversationDTO> partialUpdateConversation(
        @PathVariable(value = "id", required = false) final UUID id,
        @NotNull @RequestBody ConversationDTO conversationDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Conversation partially : {}, {}", id, conversationDTO);
        if (conversationDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, conversationDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Propriété, pas seulement existence : `existsById` laissait n'importe quel utilisateur
        // authentifié écraser la conversation d'un autre en connaissant son UUID.
        if (!conversationService.verifyConversationBelongToUerLogged(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ConversationDTO> result = conversationService.partialUpdate(conversationDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, conversationDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /conversations} : get all the Conversations.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Conversations in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ConversationDTO>> getAllConversations(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "typeChat", required = false) TypeChat typeChat
    ) {
        LOG.debug("REST request to get a page of Conversations (typeChat={})", typeChat);
        Page<ConversationDTO> page = conversationService.findAllByUserLogin(pageable, typeChat);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /conversations/:id} : get the "id" conversation.
     *
     * @param id the id of the conversationDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the conversationDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable("id") UUID id) {
        LOG.debug("REST request to get Conversation : {}", id);
        Optional<ConversationDTO> conversationDTO = conversationService.findOne(id);
        return ResponseUtil.wrapOrNotFound(conversationDTO);
    }

    /**
     * {@code DELETE  /conversations/:id} : delete the "id" conversation.
     *
     * @param id the id of the conversationDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable("id") UUID id) {
        LOG.debug("REST request to delete Conversation : {}", id);
        conversationService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
