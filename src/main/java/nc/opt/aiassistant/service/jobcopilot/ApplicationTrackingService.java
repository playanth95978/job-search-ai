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
import nc.opt.aiassistant.service.mapper.JobApplicationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationTrackingService {

    private final JobApplicationRepository applicationRepository;
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
