package nc.opt.aiassistant.repository.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    List<JobApplication> findByUserIdOrderByCreatedAtDesc(String userId);

    List<JobApplication> findByUserIdAndStatus(String userId, ApplicationStatus status);

    long countByUserIdAndStatus(String userId, ApplicationStatus status);

    /** Dedup guard for the auto-apply agent: has the user already applied to this offer? */
    boolean existsByUserIdAndJobOffer_Id(String userId, UUID jobOfferId);

    /** Daily quota for the auto-apply agent: applications the user created since {@code since}. */
    long countByUserIdAndCreatedAtAfter(String userId, Instant since);
}
