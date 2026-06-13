package nc.opt.aiassistant.web.rest.jobcopilot;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.cv.CvIngestionService;
import nc.opt.aiassistant.service.jobcopilot.dto.CvReviewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/job-copilot/cv")
@RequiredArgsConstructor
public class JobCopilotCvResource {

    private final CvIngestionService cvIngestionService;
    private final CandidateProfileRepository profileRepository;

    @PostMapping("/upload")
    public ResponseEntity<CandidateProfile> uploadCv(@RequestParam("file") MultipartFile file) throws IOException {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = cvIngestionService.ingest(file, userId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfile> getProfile() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return profileRepository.findByUserId(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/review")
    public ResponseEntity<CvReviewDto> review() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(cvIngestionService.review(userId));
    }
}
