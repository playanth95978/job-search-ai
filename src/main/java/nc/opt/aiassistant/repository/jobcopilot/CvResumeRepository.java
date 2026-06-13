package nc.opt.aiassistant.repository.jobcopilot;

import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.CvResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CvResumeRepository extends JpaRepository<CvResume, UUID> {
    Optional<CvResume> findByUserId(String userId);
}
