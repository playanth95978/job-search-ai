package nc.opt.aiassistant.repository.jobcopilot;

import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.SearchImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchImpressionRepository extends JpaRepository<SearchImpression, UUID> {
    /**
     * Retrouve l'impression a marquer cliquee quand l'utilisateur ouvre une offre.
     *
     * <p>On prend la plus recente : le front n'a pas besoin de transporter l'identifiant de la
     * recherche, et une offre ouverte apres plusieurs recherches est attribuee a la derniere, qui
     * est celle affichee a l'ecran.
     */
    @Query(
        """
        SELECT i FROM SearchImpression i
         WHERE i.jobOffer.id = :offerId
           AND (:userId IS NULL OR i.searchEvent.userId = :userId)
           AND i.simulated = false
         ORDER BY i.createdAt DESC
        LIMIT 1
        """
    )
    Optional<SearchImpression> findLatestForOffer(@Param("offerId") UUID offerId, @Param("userId") String userId);
}
