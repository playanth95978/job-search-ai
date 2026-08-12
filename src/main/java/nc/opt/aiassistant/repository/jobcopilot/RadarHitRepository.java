package nc.opt.aiassistant.repository.jobcopilot;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nc.opt.aiassistant.domain.RadarHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RadarHitRepository extends JpaRepository<RadarHit, UUID> {
    /** The user's live radar feed: unseen first, then by match strength. */
    List<RadarHit> findByUserIdAndDismissedFalseOrderBySeenAscScoreDesc(String userId);

    long countByUserIdAndSeenFalseAndDismissedFalse(String userId);

    boolean existsByUserIdAndJobOffer_Id(String userId, UUID jobOfferId);

    /**
     * Offres déjà signalées à l'utilisateur, en une requête.
     *
     * <p>Le scan examine jusqu'à {@code candidate-limit} offres par utilisateur : les tester une par
     * une avec {@code existsBy…} ferait autant d'allers-retours en base à chaque passage.</p>
     */
    @Query("SELECT h.jobOffer.id FROM RadarHit h WHERE h.userId = :userId")
    Set<UUID> findHitOfferIds(@Param("userId") String userId);

    Optional<RadarHit> findByIdAndUserId(UUID id, String userId);
}
