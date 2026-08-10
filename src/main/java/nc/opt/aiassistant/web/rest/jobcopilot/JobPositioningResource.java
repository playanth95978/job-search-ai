package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.service.jobcopilot.CvTailoringService;
import nc.opt.aiassistant.service.jobcopilot.JobPositioningService;
import nc.opt.aiassistant.service.jobcopilot.dto.CvMatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.GeneratedResumeDto;
import nc.opt.aiassistant.service.pdf.TailoredResumeExportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for the "positioning" feature: how the current user's CV stacks up against a job offer,
 * with a what-if simulation, plus the offer-tailored resume variant. Cached server-side per (user, offer).
 */
@RestController
@RequestMapping("/api/job-copilot/jobs/{id}")
@RequiredArgsConstructor
public class JobPositioningResource {

    private final JobPositioningService jobPositioningService;
    private final CvTailoringService cvTailoringService;
    private final TailoredResumeExportService tailoredResumeExportService;

    @GetMapping("/positioning")
    public ResponseEntity<CvMatchResultDto> positioning(
        @PathVariable UUID id,
        @RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
        @RequestParam(name = "language", required = false) String language
    ) {
        return ResponseEntity.ok(jobPositioningService.getPositioning(id, refresh, language));
    }

    @PostMapping("/positioning/simulate")
    public ResponseEntity<CvMatchResultDto> simulate(
        @PathVariable UUID id,
        @RequestBody SimulateRequest request,
        @RequestParam(name = "language", required = false) String language
    ) {
        return ResponseEntity.ok(jobPositioningService.simulate(id, request == null ? List.of() : request.additions(), language));
    }

    @GetMapping("/tailored")
    public ResponseEntity<GeneratedResumeDto> tailored(
        @PathVariable UUID id,
        @RequestParam(name = "refresh", defaultValue = "false") boolean refresh
    ) {
        return ResponseEntity.ok(cvTailoringService.getTailored(id, refresh));
    }

    /** The same tailored variant as a PDF, laid out with the master resume's template and theme. */
    @GetMapping(value = "/tailored/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> tailoredPdf(
        @PathVariable UUID id,
        @RequestParam(name = "refresh", defaultValue = "false") boolean refresh
    ) {
        return CvResumeResource.pdfResponse(tailoredResumeExportService.renderTailored(id, refresh), "cv-" + id);
    }

    /** What-if payload: skills/experience the candidate would add. */
    public record SimulateRequest(List<String> additions) {}
}
