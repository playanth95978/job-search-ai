package nc.opt.aiassistant.repository.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nc.opt.aiassistant.domain.SearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchEventRepository extends JpaRepository<SearchEvent, UUID> {
    List<SearchEvent> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Purge des evenements plus vieux que {@code before}.
     *
     * <p>Ce journal grossit a chaque recherche et n'a de valeur que recente : un clic d'il y a un an
     * refletait un autre corpus et un autre classement. Les impressions partent en cascade.
     */
    @Modifying
    @Query("DELETE FROM SearchEvent e WHERE e.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
