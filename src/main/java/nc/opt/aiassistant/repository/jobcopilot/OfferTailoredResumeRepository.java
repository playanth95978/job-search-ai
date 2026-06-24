package nc.opt.aiassistant.repository.jobcopilot;

import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.OfferTailoredResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OfferTailoredResumeRepository extends JpaRepository<OfferTailoredResume, UUID> {
    Optional<OfferTailoredResume> findByUserIdAndJobOfferId(String userId, UUID jobOfferId);

    @Transactional
    void deleteByUserId(String userId);
}
