package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.RadarService;
import nc.opt.aiassistant.service.jobcopilot.dto.RadarHitDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opportunity Radar: the user's feed of proactively matched new offers. */
@RestController
@RequestMapping("/api/job-copilot/radar")
@RequiredArgsConstructor
public class JobCopilotRadarResource {

    private final RadarService radarService;

    @GetMapping("")
    public ResponseEntity<List<RadarHitDto>> feed() {
        return ResponseEntity.ok(radarService.feed(currentUser()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", radarService.unreadCount(currentUser())));
    }

    @PostMapping("/{id}/seen")
    public ResponseEntity<Void> markSeen(@PathVariable UUID id) {
        radarService.markSeen(id, currentUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id) {
        radarService.dismiss(id, currentUser());
        return ResponseEntity.noContent().build();
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow();
    }
}
