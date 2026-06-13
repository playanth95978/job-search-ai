package nc.opt.aiassistant.repository.jobcopilot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.CvResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CvResumeVersionRepository extends JpaRepository<CvResumeVersion, UUID> {
    List<CvResumeVersion> findByResumeIdOrderByVersionDesc(UUID resumeId);

    Optional<CvResumeVersion> findByIdAndResumeId(UUID id, UUID resumeId);
}
