package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.RadarHit;
import nc.opt.aiassistant.domain.RadarState;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.repository.jobcopilot.RadarHitRepository;
import nc.opt.aiassistant.repository.jobcopilot.RadarStateRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTagDto;
import nc.opt.aiassistant.service.jobcopilot.dto.RadarHitDto;
import nc.opt.aiassistant.service.jobcopilot.matching.JobMatchingService;
import nc.opt.aiassistant.service.mapper.JobOfferMapper;
import org.springframework.data.domain.Vector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opportunity Radar — the market that looks for you.
 *
 * <p>On a schedule, for each user with a profile embedding, it takes the nearest offers ingested since
 * that user's last scan and scores them with the deterministic {@link JobMatchingService}. Matches
 * above threshold become {@link RadarHit}s (deduplicated per offer), each carrying the "why you"
 * strengths. A per-user watermark ({@link RadarState}) ensures every scan only looks at what is new.</p>
 *
 * <p>P2 does this directly (users × recent offers), which is fine for a modest user base; the inverted
 * pgvector matching (offer → nearest user profiles) is the P3 scaling step.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadarService {

    private final ApplicationProperties applicationProperties;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;
    private final RadarHitRepository radarHitRepository;
    private final RadarStateRepository radarStateRepository;
    private final JobMatchingService matchingService;
    private final JobOfferMapper jobOfferMapper;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${application.job-copilot.radar.scan-interval-ms:900000}", initialDelay = 120000)
    public void scan() {
        ApplicationProperties.Radar cfg = applicationProperties.getJobCopilot().getRadar();
        if (!cfg.isEnabled()) {
            return;
        }
        Instant scanStart = Instant.now();
        int totalHits = 0;
        int withoutEmbedding = 0;
        for (CandidateProfile profile : profileRepository.findAll()) {
            if (profile.getEmbedding() == null || profile.getEmbedding().length == 0) {
                // Sans embedding, aucune recherche vectorielle n'est possible. C'était jusqu'ici un
                // abandon totalement muet : un profil importé avant le service d'embedding, ou dont
                // l'embedding a échoué, restait invisible au radar sans la moindre trace.
                withoutEmbedding++;
                continue;
            }
            try {
                totalHits += scanForUser(profile, cfg, scanStart);
            } catch (Exception e) {
                log.error("Radar scan failed for user {}: {}", profile.getUserId(), e.getMessage());
            }
        }
        if (totalHits > 0) {
            log.info("Opportunity Radar created {} new hit(s)", totalHits);
        }
        if (withoutEmbedding > 0) {
            log.warn(
                "Opportunity Radar : {} profil(s) sans embedding, ignoré(s) — réimporter le CV pour déclencher le calcul",
                withoutEmbedding
            );
        }
    }

    private int scanForUser(CandidateProfile profile, ApplicationProperties.Radar cfg, Instant scanStart) {
        String userId = profile.getUserId();
        // Fenêtre d'ancienneté fixe, rejouée à chaque scan. Le curseur précédent (« depuis le dernier
        // scan ») avançait même quand le scan ne produisait rien : avec une ingestion quotidienne et
        // des scans au quart d'heure, il ne restait qu'une poignée de minutes par jour pendant
        // lesquelles une offre était éligible, et une seule chance de dépasser le seuil.
        Instant since = scanStart.minus(cfg.getLookbackDays(), ChronoUnit.DAYS);

        List<JobOffer> candidates = jobOfferRepository.searchVectorSince(Vector.of(profile.getEmbedding()), since, cfg.getCandidateLimit());

        int made = 0;
        if (candidates.isEmpty()) {
            log.debug("Radar {} : aucune offre indexée depuis {} jours", userId, cfg.getLookbackDays());
        } else {
            // Un seul chargement des offres déjà signalées : les tester une par une ferait autant de
            // requêtes que de candidates, à chaque scan et pour chaque utilisateur.
            Set<UUID> alreadyHit = radarHitRepository.findHitOfferIds(userId);

            // Deterministic scoring (skills, location, remote, salary) + "why you" strengths, sorted desc.
            List<MatchResultDto> scored = matchingService.matchJobs(profile, candidates, null);
            for (MatchResultDto m : scored) {
                if (m.score() < cfg.getThreshold() || made >= cfg.getMaxPerScan()) {
                    break; // list is sorted by score desc
                }
                if (!alreadyHit.contains(m.jobOffer().id())) {
                    radarHitRepository.save(toHit(userId, m, scanStart));
                    made++;
                }
            }
            if (made == 0) {
                double best = scored.isEmpty() ? 0 : scored.getFirst().score();
                log.debug(
                    "Radar {} : {} offre(s) examinée(s), aucun nouveau hit (meilleur score {}, seuil {})",
                    userId,
                    candidates.size(),
                    best,
                    cfg.getThreshold()
                );
            }
        }

        // Horodatage informatif : il ne filtre plus rien, mais sert à voir quand l'utilisateur a été
        // scanné pour la dernière fois.
        RadarState state = radarStateRepository.findById(userId).orElseGet(RadarState::new);
        state.setUserId(userId);
        state.setLastOfferAt(scanStart);
        radarStateRepository.save(state);
        return made;
    }

    private RadarHit toHit(String userId, MatchResultDto m, Instant now) {
        RadarHit hit = new RadarHit();
        hit.setUserId(userId);
        hit.setJobOffer(jobOfferRepository.getReferenceById(m.jobOffer().id()));
        hit.setScore(m.score());
        hit.setWhyYou(writeWhyYou(m));
        hit.setCreatedAt(now);
        return hit;
    }

    private String writeWhyYou(MatchResultDto m) {
        try {
            return objectMapper.writeValueAsString(m.strengths());
        } catch (Exception e) {
            return "[]";
        }
    }

    // --- feed / actions -----------------------------------------------------------------------

    /** The user's live radar feed (unseen first, then by score). Mapped in-session to avoid lazy issues. */
    @Transactional(readOnly = true)
    public List<RadarHitDto> feed(String userId) {
        return radarHitRepository.findByUserIdAndDismissedFalseOrderBySeenAscScoreDesc(userId).stream().map(this::toDto).toList();
    }

    public long unreadCount(String userId) {
        return radarHitRepository.countByUserIdAndSeenFalseAndDismissedFalse(userId);
    }

    @Transactional
    public void markSeen(java.util.UUID id, String userId) {
        radarHitRepository.findByIdAndUserId(id, userId).ifPresent(h -> h.setSeen(true));
    }

    @Transactional
    public void dismiss(java.util.UUID id, String userId) {
        radarHitRepository.findByIdAndUserId(id, userId).ifPresent(h -> h.setDismissed(true));
    }

    private RadarHitDto toDto(RadarHit hit) {
        return new RadarHitDto(
            hit.getId(),
            jobOfferMapper.toDto(hit.getJobOffer()),
            hit.getScore(),
            parseWhyYou(hit.getWhyYou()),
            hit.isSeen(),
            hit.isDismissed(),
            hit.getCreatedAt()
        );
    }

    private List<MatchTagDto> parseWhyYou(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
