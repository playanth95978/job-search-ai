package nc.opt.aiassistant.service.jobcopilot;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.SearchEvent;
import nc.opt.aiassistant.domain.SearchImpression;
import nc.opt.aiassistant.repository.jobcopilot.SearchEventRepository;
import nc.opt.aiassistant.repository.jobcopilot.SearchImpressionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journalise ce que la recherche a montre, pour rendre le classement apprenable.
 *
 * <p>Tout est de l'observabilite : une recherche ne doit jamais echouer parce que son journal n'a
 * pas pu s'ecrire. D'ou l'ecriture asynchrone, dans sa propre transaction, et l'absorption des
 * exceptions — au pire on perd des lignes d'entrainement, ce qui est sans consequence pour
 * l'utilisateur.
 */
@Service
public class SearchEventLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchEventLogService.class);

    private final SearchEventRepository searchEventRepository;
    private final SearchImpressionRepository searchImpressionRepository;
    private final EntityManager entityManager;

    public SearchEventLogService(
        SearchEventRepository searchEventRepository,
        SearchImpressionRepository searchImpressionRepository,
        EntityManager entityManager
    ) {
        this.searchEventRepository = searchEventRepository;
        this.searchImpressionRepository = searchImpressionRepository;
        this.entityManager = entityManager;
    }

    /**
     * Les features d'une offre au moment ou elle a ete classee.
     *
     * @param vectorRank rang dans le bras vectoriel, ou {@code null} si absente de ce bras — une
     *                   information en soi, pas une donnee manquante.
     */
    public record Features(Integer vectorRank, Integer bm25Rank, Double vectorScore, Double bm25Score, Double fusedScore) {}

    /**
     * Enregistre une recherche et ses resultats.
     *
     * @param orderedIds les offres dans l'ordre affiche ; l'index porte le biais de position.
     * @param features   features par offre ; une entree absente donne une impression sans features.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        String query,
        String location,
        String mode,
        String origin,
        String userId,
        List<UUID> orderedIds,
        Map<UUID, Features> features
    ) {
        try {
            SearchEvent event = new SearchEvent();
            event.setQuery(truncate(query));
            event.setLocation(location);
            event.setMode(mode);
            event.setOrigin(origin);
            event.setUserId(userId);
            event.setResultCount(orderedIds.size());
            event.setCreatedAt(Instant.now());

            int rank = 1;
            for (UUID offerId : orderedIds) {
                SearchImpression impression = new SearchImpression();
                // getReference plutot que findById : on n'a besoin que de la cle etrangere, inutile
                // de charger 30 offres completes pour ecrire un journal.
                impression.setJobOffer(entityManager.getReference(JobOffer.class, offerId));
                impression.setRank(rank++);
                Features f = features.get(offerId);
                if (f != null) {
                    impression.setVectorRank(f.vectorRank());
                    impression.setBm25Rank(f.bm25Rank());
                    impression.setVectorScore(f.vectorScore());
                    impression.setBm25Score(f.bm25Score());
                    impression.setFusedScore(f.fusedScore());
                }
                event.addImpression(impression);
            }

            searchEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Search event logging failed, search itself unaffected: {}", e.getMessage());
        }
    }

    /** Marque une offre comme ouverte, sur la derniere recherche qui l'a montree. */
    @Transactional
    public void recordClick(UUID offerId, String userId) {
        try {
            searchImpressionRepository.findLatestForOffer(offerId, userId).ifPresent(impression -> {
                if (impression.getClickedAt() == null) {
                    impression.setClickedAt(Instant.now());
                }
            });
        } catch (Exception e) {
            log.warn("Click logging failed for offer {}: {}", offerId, e.getMessage());
        }
    }

    /** Marque une offre comme ayant abouti a une candidature — le signal positif le plus fort. */
    @Transactional
    public void recordApplication(UUID offerId, String userId) {
        try {
            searchImpressionRepository.findLatestForOffer(offerId, userId).ifPresent(impression -> {
                impression.setApplied(true);
                if (impression.getClickedAt() == null) {
                    impression.setClickedAt(Instant.now());
                }
            });
        } catch (Exception e) {
            log.warn("Application logging failed for offer {}: {}", offerId, e.getMessage());
        }
    }

    private static String truncate(String query) {
        if (query == null) {
            return "";
        }
        return query.length() > 1000 ? query.substring(0, 1000) : query;
    }
}
