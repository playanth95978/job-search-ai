package nc.opt.aiassistant.repository.jobcopilot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import nc.opt.aiassistant.domain.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Requêtes de la purge RGPD : identification des utilisateurs dormants, puis effacement de leurs
 * données personnelles table par table.
 *
 * <p><b>Portée</b> — cette purge efface les données <i>métier</i> rattachées à un utilisateur (profil
 * candidat, CV, candidatures, positionnements, radar, conversations, préférences). Elle ne supprime
 * pas le compte {@code jhi_user} lui-même : la suppression de compte relève d'une demande explicite
 * de l'utilisateur (art. 17 RGPD), pas d'une expiration de durée de conservation.</p>
 *
 * <p><b>Ancrage sur la dernière activité</b> — la durée de conservation court à compter du dernier
 * contact, conformément à la recommandation de la CNIL sur les données de candidature (2 ans). On
 * calcule donc, par utilisateur, l'horodatage le plus récent toutes tables confondues, et on ne
 * retient que ceux dont ce maximum précède la date limite. Un utilisateur actif ne perd jamais son
 * historique, même ancien.</p>
 *
 * <p>Les tables sans horodatage ({@code user_preference}, {@code radar_state}) ne participent pas au
 * calcul d'activité — elles sont purgées en même temps que le reste pour un utilisateur déjà jugé
 * dormant. Conséquence assumée : un compte n'ayant <i>que</i> des préférences n'est jamais purgé,
 * faute de date pour l'évaluer, et ces données ne sont pas identifiantes.</p>
 */
@Repository
public interface PersonalDataRetentionRepository extends JpaRepository<CandidateProfile, java.util.UUID> {
    /**
     * Utilisateurs dont la dernière trace d'activité est antérieure à {@code cutoff}, par lots bornés.
     *
     * <p>{@code GREATEST} ignore les {@code NULL} en PostgreSQL, et {@code COALESCE(updated_at,
     * created_at)} couvre les lignes jamais modifiées. Le {@code HAVING} porte sur le maximum global,
     * donc un seul signe de vie récent suffit à exclure l'utilisateur du lot.</p>
     */
    @Query(
        value = """
        SELECT user_id FROM (
            SELECT user_id, MAX(GREATEST(COALESCE(updated_at, created_at), created_at)) AS last_seen
              FROM candidate_profile GROUP BY user_id
            UNION ALL
            SELECT user_id, MAX(GREATEST(COALESCE(updated_at, created_at), created_at))
              FROM job_application GROUP BY user_id
            UNION ALL
            SELECT user_id, MAX(GREATEST(COALESCE(updated_at, created_at), created_at))
              FROM resume GROUP BY user_id
            UNION ALL
            SELECT user_id, MAX(created_at) FROM offer_positioning GROUP BY user_id
            UNION ALL
            SELECT user_id, MAX(created_at) FROM offer_tailored_resume GROUP BY user_id
            UNION ALL
            SELECT user_id, MAX(created_at) FROM radar_hit GROUP BY user_id
            UNION ALL
            SELECT user_id, MAX(GREATEST(COALESCE(last_message_at, created_at), created_at))
              FROM conversation GROUP BY user_id
        ) activity
        GROUP BY user_id
        HAVING MAX(last_seen) < :cutoff
        ORDER BY user_id
        LIMIT :limit
        """,
        nativeQuery = true
    )
    List<String> findDormantUserIds(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    // --- Effacement, dans l'ordre imposé par les clés étrangères -----------------------------

    @Modifying
    @Query(value = "DELETE FROM offer_tailored_resume WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteTailoredResumes(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM offer_positioning WHERE user_id IN (:userIds)", nativeQuery = true)
    int deletePositionings(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM radar_hit WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteRadarHits(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM radar_state WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteRadarStates(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM user_preference WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteUserPreferences(@Param("userIds") Collection<String> userIds);

    /** Versions de CV d'abord : {@code resume_version.resume_id} référence {@code resume}. */
    @Modifying
    @Query(value = "DELETE FROM resume_version WHERE resume_id IN (SELECT id FROM resume WHERE user_id IN (:userIds))", nativeQuery = true)
    int deleteResumeVersions(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM resume WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteResumes(@Param("userIds") Collection<String> userIds);

    /** Candidatures avant profils : {@code job_application.candidate_profile_id} est une FK. */
    @Modifying
    @Query(value = "DELETE FROM job_application WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteApplications(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM candidate_profile WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteCandidateProfiles(@Param("userIds") Collection<String> userIds);

    @Modifying
    @Query(value = "DELETE FROM conversation WHERE user_id IN (:userIds)", nativeQuery = true)
    int deleteConversations(@Param("userIds") Collection<String> userIds);
}
