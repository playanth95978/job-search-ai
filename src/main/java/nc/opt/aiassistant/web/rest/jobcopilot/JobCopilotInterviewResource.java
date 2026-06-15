package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.InterviewCoachService;
import nc.opt.aiassistant.service.jobcopilot.dto.InterviewMessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/job-copilot/interview")
@RequiredArgsConstructor
public class JobCopilotInterviewResource {

    private final InterviewCoachService interviewCoachService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;

    /** Starts a new interview roleplay session for the given job offer. */
    @PostMapping("/start")
    public ResponseEntity<InterviewMessageDto> start(@RequestParam UUID jobOfferId, @RequestParam(required = false) String language) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();

        String conversationId = UUID.randomUUID().toString();
        String message = interviewCoachService.startSession(profile, offer, conversationId, language);
        return ResponseEntity.ok(new InterviewMessageDto(conversationId, message));
    }

    /** Sends the candidate's answer and returns the recruiter's feedback plus the next question. */
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<InterviewMessageDto> message(
        @PathVariable String conversationId,
        @RequestParam UUID jobOfferId,
        @RequestBody String answer,
        @RequestParam(required = false) String language
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        if (profile == null || offer == null) return ResponseEntity.badRequest().build();

        String message = interviewCoachService.respond(profile, offer, conversationId, answer, language);
        return ResponseEntity.ok(new InterviewMessageDto(conversationId, message));
    }
}
