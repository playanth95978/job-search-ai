package nc.opt.aiassistant.web.rest.jobcopilot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.ApplicationAutopsyService;
import nc.opt.aiassistant.service.jobcopilot.dto.AutopsyReportDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Insight endpoints derived from the user's own application funnel + the applied-to offers. */
@RestController
@RequestMapping("/api/job-copilot/insights")
@RequiredArgsConstructor
@Slf4j
public class JobCopilotInsightsResource {

    private final ApplicationAutopsyService autopsyService;

    /** {@code GET /insights/autopsy} : why the current user does or doesn't get interviews. */
    @GetMapping("/autopsy")
    public ResponseEntity<AutopsyReportDto> autopsy() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(autopsyService.autopsy(userId));
    }
}
