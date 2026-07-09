package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.batch.JobOfferBatchScheduler;
import nc.opt.aiassistant.security.AuthoritiesConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint to trigger the job-offer ingestion batch on demand (all sources: France Travail,
 * Emploi-NC, RemoteOK, Adzuna) instead of waiting for the daily 2 AM schedule. Useful right after
 * configuring new API credentials.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/job-offers")
@RequiredArgsConstructor
public class JobIngestionAdminResource {

    private final JobOfferBatchScheduler scheduler;

    @PostMapping("/ingest")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Map<String, String>> triggerIngestion() {
        log.info("Manual job-offer ingestion triggered via admin endpoint");
        // Run in the background: a full multi-source ingestion can take a while, so return immediately.
        CompletableFuture.runAsync(scheduler::runIngestionJob);
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
