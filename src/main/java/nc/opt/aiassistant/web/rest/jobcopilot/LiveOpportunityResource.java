package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.LiveOpportunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home-page "latest opportunities for you" feed, matched to the signed-in user's CV profile and
 * fetched live from the external job boards. Authenticated by default (falls under /api/**).
 */
@RestController
@RequestMapping("/api/job-copilot/opportunities")
@RequiredArgsConstructor
@Slf4j
public class LiveOpportunityResource {

    private final LiveOpportunityService liveOpportunityService;

    /**
     * {@code GET /opportunities/live} : latest board offers matching the current user's profile.
     *
     * @param refresh when true, bypass the per-user cache and query the boards again.
     */
    @GetMapping("/live")
    public ResponseEntity<List<JobOffer>> live(@RequestParam(defaultValue = "false") boolean refresh) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        log.debug("REST request for live opportunities (refresh={}) for user {}", refresh, userId);
        return ResponseEntity.ok(liveOpportunityService.forUser(userId, refresh));
    }
}
