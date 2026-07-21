package nc.opt.aiassistant.repository.jobcopilot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.RadarHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RadarHitRepository extends JpaRepository<RadarHit, UUID> {
    /** The user's live radar feed: unseen first, then by match strength. */
    List<RadarHit> findByUserIdAndDismissedFalseOrderBySeenAscScoreDesc(String userId);

    long countByUserIdAndSeenFalseAndDismissedFalse(String userId);

    boolean existsByUserIdAndJobOffer_Id(String userId, UUID jobOfferId);

    Optional<RadarHit> findByIdAndUserId(UUID id, String userId);
}
