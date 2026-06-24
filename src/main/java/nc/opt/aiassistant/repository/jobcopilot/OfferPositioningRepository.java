package nc.opt.aiassistant.repository.jobcopilot;

import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.OfferPositioning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OfferPositioningRepository extends JpaRepository<OfferPositioning, UUID> {
    Optional<OfferPositioning> findByUserIdAndJobOfferId(String userId, UUID jobOfferId);

    @Transactional
    void deleteByUserId(String userId);
}
