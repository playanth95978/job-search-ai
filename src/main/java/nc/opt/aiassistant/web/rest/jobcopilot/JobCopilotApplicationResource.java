package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.dto.JobApplicationDTO;
import nc.opt.aiassistant.service.jobcopilot.ApplicationTrackingService;
import nc.opt.aiassistant.service.jobcopilot.CoverLetterService;
import nc.opt.aiassistant.service.jobcopilot.dto.ApplyOutcomeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/job-copilot/applications")
@RequiredArgsConstructor
public class JobCopilotApplicationResource {

    private final ApplicationTrackingService trackingService;
    private final CoverLetterService coverLetterService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;

    @GetMapping
    public ResponseEntity<List<JobApplicationDTO>> getApplications() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        List<JobApplicationDTO> entityList = trackingService.getApplications(userId);
        return ResponseEntity.ok().body(entityList);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<ApplicationStatus, Long>> getStats() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(trackingService.getStats(userId));
    }

    @PostMapping("/create")
    public ResponseEntity<JobApplication> createApplication(
        @RequestParam UUID jobOfferId,
        @RequestParam(defaultValue = "true") boolean generateCoverLetter,
        @RequestParam(required = false) String language
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();

        String coverLetter = generateCoverLetter ? coverLetterService.generate(profile, offer, language) : null;
        JobApplication app = trackingService.createApplication(userId, offer, profile, coverLetter, null);
        return ResponseEntity.ok(app);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<JobApplication> updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(trackingService.updateStatus(id, ApplicationStatus.valueOf(status.toUpperCase())));
    }

    @PutMapping("/{id}/cover-letter")
    public ResponseEntity<JobApplication> updateCoverLetter(@PathVariable UUID id, @RequestBody String coverLetter) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(trackingService.updateCoverLetter(id, userId, coverLetter));
    }

    /** Sources for which a direct, in-app application submission is available. */
    @GetMapping("/auto-apply-sources")
    public ResponseEntity<List<String>> getAutoApplySources() {
        return ResponseEntity.ok(trackingService.getAutoApplySources());
    }

    /** Submits the application directly via the source ATS API when supported, else leaves it for manual submission. */
    @PostMapping("/{id}/apply")
    public ResponseEntity<ApplyOutcomeDto> apply(@PathVariable UUID id) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(trackingService.submitApplication(id, userId));
    }

    @PostMapping("/{jobOfferId}/cover-letter")
    public ResponseEntity<String> generateCoverLetter(@PathVariable UUID jobOfferId, @RequestParam(required = false) String language) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(coverLetterService.generate(profile, offer, language));
    }
}
