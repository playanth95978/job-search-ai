package nc.opt.aiassistant.repository.jobcopilot;

import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    Optional<CandidateProfile> findByUserId(String userId);
}
