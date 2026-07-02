package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.dto.PanelMessageDto;
import nc.opt.aiassistant.service.jobcopilot.dto.PanelReportDto;
import nc.opt.aiassistant.service.jobcopilot.panel.ShadowPanelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Shadow Interview Panel: a 3-agent (HR / Tech Lead / Manager) interview simulator for a given job
 * offer, with a moderator routing the turns and a final cross deliberation.
 */
@RestController
@RequestMapping("/api/job-copilot/panel")
@RequiredArgsConstructor
public class JobCopilotPanelResource {

    private final ShadowPanelService panelService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;

    /** Starts a panel session for a job offer; returns the opening turn. */
    @PostMapping("/start")
    public ResponseEntity<PanelMessageDto> start(@RequestParam UUID jobOfferId, @RequestParam(required = false) String language) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();

        String conversationId = UUID.randomUUID().toString();
        return ResponseEntity.ok(panelService.startSession(profile, offer, conversationId, language));
    }

    /** Sends the candidate's answer; the moderator routes and a juror replies. */
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<PanelMessageDto> message(
        @PathVariable String conversationId,
        @RequestParam UUID jobOfferId,
        @RequestBody String answer,
        @RequestParam(required = false) String language
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(panelService.respond(profile, offer, conversationId, answer, language));
    }

    /** Ends the interview and returns the cross deliberation (per-juror verdicts + synthesis). */
    @PostMapping("/{conversationId}/report")
    public ResponseEntity<PanelReportDto> report(
        @PathVariable String conversationId,
        @RequestParam UUID jobOfferId,
        @RequestParam(required = false) String language
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(panelService.report(profile, offer, conversationId, language));
    }
}
