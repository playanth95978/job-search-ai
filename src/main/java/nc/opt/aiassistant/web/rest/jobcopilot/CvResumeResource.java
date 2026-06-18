package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.CvResumeService;
import nc.opt.aiassistant.service.jobcopilot.CvRewriteService;
import nc.opt.aiassistant.service.jobcopilot.dto.*;
import nc.opt.aiassistant.service.mapper.CandidateProfileMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST API for the CV Builder: resume storage, retrieval and versioning. */
@RestController
@RequestMapping("/api/cv-builder")
@RequiredArgsConstructor
public class CvResumeResource {

    private final CvResumeService cvResumeService;
    private final CvRewriteService cvRewriteService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileMapper candidateProfileMapper;

    @GetMapping("/resume")
    public ResponseEntity<ResumeDto> getResume() {
        return cvResumeService
            .getForCurrentUser()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/resume")
    public ResponseEntity<ResumeDto> saveResume(@RequestBody ResumeDto resume) {
        return ResponseEntity.ok(cvResumeService.save(resume));
    }

    @PostMapping("/resume/versions")
    public ResponseEntity<ResumeVersionDto> createVersion() {
        return ResponseEntity.ok(cvResumeService.createVersion());
    }

    @GetMapping("/resume/versions")
    public ResponseEntity<List<ResumeVersionDto>> listVersions() {
        return ResponseEntity.ok(cvResumeService.listVersions());
    }

    @GetMapping("/resume/versions/{id}")
    public ResponseEntity<ResumeDto> getVersion(@PathVariable UUID id) {
        return cvResumeService
            .getVersion(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/resume/versions/{id}/restore")
    public ResponseEntity<ResumeDto> restoreVersion(@PathVariable UUID id) {
        return cvResumeService
            .restoreVersion(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/rewrite")
    public ResponseEntity<RewriteResponseDto> rewrite(@RequestBody RewriteRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.rewrite(request));
    }

    @PostMapping("/review")
    public ResponseEntity<CvReviewDto> review(@RequestBody ReviewRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.reviewResume(request.content()));
    }

    @PostMapping("/match")
    public ResponseEntity<CvMatchResultDto> match(@RequestBody CvMatchRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.matchAgainstOffer(request.content(), request.offer()));
    }

    @GetMapping("/resume/candidate-profile")
    public ResponseEntity<CandidateProfileDto> getCandidateProfile() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return candidateProfileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .map(candidateProfileMapper::toDto)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
