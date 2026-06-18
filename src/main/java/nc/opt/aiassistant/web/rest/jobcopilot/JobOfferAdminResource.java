package nc.opt.aiassistant.web.rest.jobcopilot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.jobcopilot.JobOfferProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing JobOffer reindexing from administration interface.
 */
@RestController
@RequestMapping("/api/admin/job-offers")
@RequiredArgsConstructor
@Slf4j
public class JobOfferAdminResource {

    private final JobOfferProcessingService jobOfferProcessingService;

    /**
     * {@code POST  /reindex} : Reindex all job offers embeddings.
     *
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)}.
     */
    @PostMapping("/reindex")
    public ResponseEntity<Void> reindexAll() {
        log.info("REST request to reindex all job offers embeddings");
        jobOfferProcessingService.reindexAll();
        return ResponseEntity.accepted().build();
    }
}
