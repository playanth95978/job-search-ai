package nc.opt.aiassistant.repository;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.Conversation;
import nc.opt.aiassistant.domain.enumeration.TypeChat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Conversation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Page<Conversation> findAllByUserId(String userId, Pageable pageable);

    Page<Conversation> findAllByUserIdAndTypeChat(String userId, TypeChat typeChat, Pageable pageable);

    Optional<Conversation> findConversationByIdAndUserId(UUID id, String userId);

    boolean existsByIdAndUserId(@NotNull UUID id, @NotNull String userId);
}
