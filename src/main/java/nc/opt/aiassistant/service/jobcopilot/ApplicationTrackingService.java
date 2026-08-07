package nc.opt.aiassistant.service.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import nc.opt.aiassistant.repository.jobcopilot.JobApplicationRepository;
import nc.opt.aiassistant.service.dto.JobApplicationDTO;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector;
import nc.opt.aiassistant.service.jobcopilot.dto.ApplyOutcomeDto;
import nc.opt.aiassistant.service.mapper.JobApplicationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationTrackingService {

    private final JobApplicationRepository applicationRepository;
    private final List<AtsConnector> connectors;
    private final JobApplicationMapper jobApplicationMapper;

    @Transactional
    public JobApplication createApplication(
        String userId,
        JobOffer offer,
        CandidateProfile profile,
        String coverLetter,
        Double matchScore
    ) {
        JobApplication app = new JobApplication();
        app.setUserId(userId);
        app.setJobOffer(offer);
        app.setCandidateProfile(profile);
        app.setCoverLetter(coverLetter);
        app.setMatchScore(matchScore);
        app.setStatus(ApplicationStatus.DRAFT);
        return applicationRepository.save(app);
    }

    @Transactional
    public JobApplication updateStatus(UUID applicationId, String userId, ApplicationStatus status) {
        // Scope to the owner (like updateCoverLetter / submitApplication) so a user can only change
        // the status of their own applications, not another user's by id.
        JobApplication app = getOwnedApplication(applicationId, userId);
        app.setStatus(status);
        if (status == ApplicationStatus.APPLIED) {
            app.setAppliedAt(Instant.now());
        }
        return applicationRepository.save(app);
    }

    @Transactional
    public JobApplication updateCoverLetter(UUID applicationId, String userId, String coverLetter) {
        JobApplication app = getOwnedApplication(applicationId, userId);
        app.setCoverLetter(coverLetter);
        return applicationRepository.save(app);
    }

    /** ATS sources for which a direct, in-app application submission is available. */
    public List<String> getAutoApplySources() {
        return connectors
            .stream()
            .filter(AtsConnector::supportsAutoApply)
            .map(c -> c.getSource().name())
            .toList();
    }

    /**
     * Whether any connector can auto-apply to this offer — matched by source OR by apply URL
     * (an aggregator offer whose applyUrl points to a Lever/Greenhouse/SmartRecruiters board).
     */
    @Transactional(readOnly = true)
    public boolean canAutoApply(JobOffer offer) {
        return offer != null && connectors.stream().anyMatch(c -> c.canAutoApply(offer));
    }

    /**
     * Attempts to submit the application directly through the source ATS API.
     * Falls back to {@code autoApplied = false} when the source has no auto-apply connector,
     * letting the caller fall back to opening the offer's apply link instead.
     */
    @Transactional
    public ApplyOutcomeDto submitApplication(UUID applicationId, String userId) {
        JobApplication app = getOwnedApplication(applicationId, userId);
        JobOffer offer = app.getJobOffer();
        // Route by URL first (unlocks aggregator offers that link to an ATS board), then by source.
        AtsConnector connector =
            offer == null
                ? null
                : connectors
                      .stream()
                      .filter(c -> c.canAutoApply(offer))
                      .findFirst()
                      .orElse(null);

        if (connector == null) {
            return new ApplyOutcomeDto(false, false, "Auto-apply not available for this offer. Apply manually via the offer link.", app);
        }

        AtsConnector.ApplyResult result = connector.applyTo(offer, app.getCandidateProfile(), app.getCoverLetter());
        if (result.success()) {
            app.setStatus(ApplicationStatus.APPLIED);
            app.setAppliedAt(Instant.now());
            app = applicationRepository.save(app);
        }
        return new ApplyOutcomeDto(result.success(), true, result.message(), app);
    }

    private JobApplication getOwnedApplication(UUID applicationId, String userId) {
        JobApplication app = applicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!app.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Application not found: " + applicationId);
        }
        return app;
    }

    @Transactional(readOnly = true)
    public List<JobApplicationDTO> getApplications(String userId) {
        return jobApplicationMapper.toDto(applicationRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    public List<JobApplication> getByStatus(String userId, ApplicationStatus status) {
        return applicationRepository.findByUserIdAndStatus(userId, status);
    }

    public Map<ApplicationStatus, Long> getStats(String userId) {
        return List.of(ApplicationStatus.values())
            .stream()
            .collect(Collectors.toMap(s -> s, s -> applicationRepository.countByUserIdAndStatus(userId, s)));
    }
}
