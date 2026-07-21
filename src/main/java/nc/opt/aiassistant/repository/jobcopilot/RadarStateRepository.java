package nc.opt.aiassistant.repository.jobcopilot;

import nc.opt.aiassistant.domain.RadarState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RadarStateRepository extends JpaRepository<RadarState, String> {}
