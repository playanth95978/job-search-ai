package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.CvResumeService;
import nc.opt.aiassistant.service.jobcopilot.CvRewriteService;
import nc.opt.aiassistant.service.jobcopilot.dto.*;
import nc.opt.aiassistant.service.mapper.CandidateProfileMapper;
import nc.opt.aiassistant.service.pdf.ResumeDocument;
import nc.opt.aiassistant.service.pdf.ResumeExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final ResumeExportService resumeExportService;

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

    /**
     * Renders the resume carried in the request body — i.e. exactly what the user is looking at in the
     * builder. It is a POST rather than a GET on the stored resume because the editor auto-saves on a
     * 1.5s debounce: exporting server-side state would hand back a CV that is one edit behind.
     */
    @PostMapping(value = "/resume/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@RequestBody ResumeDocument resume) {
        return pdfResponse(resumeExportService.render(resume), resume.slug());
    }

    /** Renders the stored resume, for callers that have no editor state to send (links, integrations). */
    @GetMapping(value = "/resume/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportStoredPdf() {
        return resumeExportService
            .currentUserResume()
            .map(resume -> pdfResponse(resumeExportService.render(resume), resume.slug()))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String fileNameSlug) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(fileNameSlug + ".pdf")
                    .build()
                    .toString()
            )
            .body(pdf);
    }

    @PostMapping("/rewrite")
    public ResponseEntity<RewriteResponseDto> rewrite(@RequestBody RewriteRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.rewrite(request));
    }

    @PostMapping("/review")
    public ResponseEntity<CvReviewDto> review(@RequestBody ReviewRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.reviewResumeAdvanced(request.content(), request.language()));
    }

    @PostMapping("/match")
    public ResponseEntity<CvMatchResultDto> match(@RequestBody CvMatchRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.matchAgainstOffer(request.content(), request.offer(), request.language()));
    }

    @PostMapping("/generate")
    public ResponseEntity<GeneratedResumeDto> generate(@RequestBody GenerateResumeRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.generateFullResume(request));
    }

    /**
     * {@code POST /resume/import-from-cv} : structures the CV the user uploaded into the builder's model.
     *
     * <p>Reads the full text kept in {@code CandidateProfile.rawMarkdown} rather than the profile's typed
     * columns, because those hold no projects, no summary, no phone and no links — seeding the builder
     * from them dropped those sections of a rich CV without saying so.
     *
     * @return the structured resume, or 204 when the user has never uploaded a CV.
     */
    @PostMapping("/resume/import-from-cv")
    public ResponseEntity<GeneratedResumeDto> importFromUploadedCv() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return candidateProfileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .map(CandidateProfile::getRawMarkdown)
            .filter(raw -> !raw.isBlank())
            .map(raw -> ResponseEntity.ok(cvRewriteService.extractResume(raw)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/translate")
    public ResponseEntity<GeneratedResumeDto> translate(@RequestBody GenerateResumeRequestDto request) {
        return ResponseEntity.ok(cvRewriteService.translateResume(request.source(), request.language()));
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
