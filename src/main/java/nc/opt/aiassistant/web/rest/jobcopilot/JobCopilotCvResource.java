package nc.opt.aiassistant.web.rest.jobcopilot;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.CandidateProfileEmbeddingService;
import nc.opt.aiassistant.service.jobcopilot.cv.CvIngestionService;
import nc.opt.aiassistant.service.jobcopilot.dto.AtsSimulationDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CandidateProfileDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CvReviewDto;
import nc.opt.aiassistant.service.mapper.CandidateProfileMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/job-copilot/cv")
@RequiredArgsConstructor
public class JobCopilotCvResource {

    private final CvIngestionService cvIngestionService;
    private final CandidateProfileRepository profileRepository;
    private final CandidateProfileMapper candidateProfileMapper;
    private final CandidateProfileEmbeddingService profileEmbeddingService;

    @PostMapping("/upload")
    public ResponseEntity<CandidateProfileDto> uploadCv(@RequestParam("file") MultipartFile file) throws IOException {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = cvIngestionService.ingest(file, userId);
        return ResponseEntity.ok(candidateProfileMapper.toDto(profile));
    }

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileDto> getProfile() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return profileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .map(candidateProfileMapper::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * {@code PUT /profile} : save user edits to the extracted profile (identity, skills, roles,
     * experiences…). Re-embeds asynchronously since roles/skills drive matching, radar and the live
     * opportunities feed. Only the signed-in user's latest profile is updated.
     */
    @PutMapping("/profile")
    public ResponseEntity<CandidateProfileDto> updateProfile(@RequestBody CandidateProfileDto dto) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return profileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .map(profile -> {
                UUID id = profile.getId();
                candidateProfileMapper.partialUpdate(profile, dto);
                // Never let the client payload reassign ownership or identity of the row.
                profile.setId(id);
                profile.setUserId(userId);
                CandidateProfile saved = profileRepository.save(profile);
                profileEmbeddingService.embedAsync(saved.getId());
                return ResponseEntity.ok(candidateProfileMapper.toDto(saved));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/review")
    public ResponseEntity<CvReviewDto> review(@RequestParam(required = false) String language) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(cvIngestionService.review(userId, language));
    }

    @GetMapping("/ats-simulation")
    public ResponseEntity<AtsSimulationDto> simulateAtsParsing(@RequestParam(required = false) String language) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(cvIngestionService.simulateAtsParsing(userId, language));
    }
}
