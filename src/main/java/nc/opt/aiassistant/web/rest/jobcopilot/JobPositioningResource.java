package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.service.jobcopilot.JobPositioningService;
import nc.opt.aiassistant.service.jobcopilot.dto.CvMatchResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for the "positioning" feature: how the current user's CV stacks up against a job offer,
 * with a what-if simulation. Result is cached server-side per (user, offer).
 */
@RestController
@RequestMapping("/api/job-copilot/jobs/{id}")
@RequiredArgsConstructor
public class JobPositioningResource {

    private final JobPositioningService jobPositioningService;

    @GetMapping("/positioning")
    public ResponseEntity<CvMatchResultDto> positioning(
        @PathVariable UUID id,
        @RequestParam(name = "refresh", defaultValue = "false") boolean refresh
    ) {
        return ResponseEntity.ok(jobPositioningService.getPositioning(id, refresh));
    }

    @PostMapping("/positioning/simulate")
    public ResponseEntity<CvMatchResultDto> simulate(@PathVariable UUID id, @RequestBody SimulateRequest request) {
        return ResponseEntity.ok(jobPositioningService.simulate(id, request == null ? List.of() : request.additions()));
    }

    /** What-if payload: skills/experience the candidate would add. */
    public record SimulateRequest(List<String> additions) {}
}
