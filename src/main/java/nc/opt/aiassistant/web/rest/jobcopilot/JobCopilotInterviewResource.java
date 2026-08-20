package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.InterviewCoachService;
import nc.opt.aiassistant.service.jobcopilot.InterviewVoiceService;
import nc.opt.aiassistant.service.jobcopilot.dto.InterviewMessageDto;
import nc.opt.aiassistant.service.jobcopilot.dto.InterviewVoiceTurnDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/job-copilot/interview")
@RequiredArgsConstructor
public class JobCopilotInterviewResource {

    private final InterviewCoachService interviewCoachService;
    private final InterviewVoiceService interviewVoiceService;
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

    /** Whether the spoken mode can be offered at all, so the UI hides the mic instead of failing on click. */
    @GetMapping("/voice/available")
    public ResponseEntity<Boolean> voiceAvailable() {
        return ResponseEntity.ok(interviewVoiceService.isAvailable());
    }

    /** Starts a session and returns the recruiter's first question, spoken. */
    @PostMapping("/voice/start")
    public ResponseEntity<InterviewVoiceTurnDto> startVoice(
        @RequestParam UUID jobOfferId,
        @RequestParam(required = false) String language
    ) {
        Context context = context(jobOfferId);
        if (context == null) return ResponseEntity.badRequest().build();

        String conversationId = UUID.randomUUID().toString();
        return ResponseEntity.ok(interviewVoiceService.start(context.profile(), context.offer(), conversationId, language));
    }

    /**
     * One spoken turn: the candidate's recording in, the recruiter's spoken reply out.
     *
     * <p>Multipart in / JSON out: the upload is a browser recording, and the response carries the audio
     * base64-encoded next to the transcript and the reply text, which the UI displays alongside it.
     */
    @PostMapping(path = "/{conversationId}/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterviewVoiceTurnDto> voiceTurn(
        @PathVariable String conversationId,
        @RequestParam UUID jobOfferId,
        @RequestPart("audio") MultipartFile audio,
        @RequestParam(required = false) String language
    ) {
        Context context = context(jobOfferId);
        if (context == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(interviewVoiceService.turn(context.profile(), context.offer(), conversationId, audio, language));
    }

    /** The profile + offer pair every endpoint needs, or null when either is missing. */
    private Context context(UUID jobOfferId) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        JobOffer offer = jobOfferRepository.findById(jobOfferId).orElse(null);
        return profile == null || offer == null ? null : new Context(profile, offer);
    }

    private record Context(CandidateProfile profile, JobOffer offer) {}
}
