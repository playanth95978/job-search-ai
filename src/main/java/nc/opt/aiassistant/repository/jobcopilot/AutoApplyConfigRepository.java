package nc.opt.aiassistant.repository.jobcopilot;

import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.AutoApplyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutoApplyConfigRepository extends JpaRepository<AutoApplyConfig, UUID> {
    Optional<AutoApplyConfig> findByUserId(String userId);
}
