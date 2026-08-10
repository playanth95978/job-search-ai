package nc.opt.aiassistant.repository.jobcopilot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Vector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, UUID>, JpaSpecificationExecutor<JobOffer> {
    List<JobOffer> findBySource(JobSource source);

    @Query("SELECT j FROM JobOffer j WHERE j.sourceId = :sourceId AND j.source = :source")
    List<JobOffer> findBySourceIdAndSource(String sourceId, JobSource source);

    /**
     * Batch dedup lookup: loads every existing offer of a given source whose sourceId is in the chunk,
     * in a single query, so mass ingestion avoids the N+1 of one SELECT per offer.
     */
    List<JobOffer> findBySourceAndSourceIdIn(JobSource source, Collection<String> sourceIds);

    // Toutes les recherches excluent les offres expirées : `expires_at IS NULL` = source qui ne
    // fournit pas de date d'expiration, l'offre reste donc visible. Ce filtre couvre aussi les
    // offres expirées volontairement conservées en base (celles liées à une candidature).

    @Query(
        "SELECT j FROM JobOffer j WHERE LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND (j.expiresAt IS NULL OR j.expiresAt > CURRENT_TIMESTAMP)"
    )
    List<JobOffer> searchByKeyword(String keyword);

    @Query(
        value = "SELECT * FROM job_offer WHERE search_text ||| :query AND (expires_at IS NULL OR expires_at > now()) " +
            "ORDER BY paradedb.score(id) DESC LIMIT :limit",
        nativeQuery = true
    )
    List<JobOffer> searchBM25(String query, int limit);

    @Query(
        value = "SELECT * FROM job_offer WHERE embedding IS NOT NULL AND (expires_at IS NULL OR expires_at > now()) " +
            "ORDER BY embedding <=> cast(:embedding as vector) LIMIT :limit",
        nativeQuery = true
    )
    List<JobOffer> searchVector(@Param("embedding") Vector embedding, @Param("limit") int limit);

    /** Nearest offers to an embedding among those ingested after {@code since} — the Opportunity Radar's candidate set. */
    @Query(
        value = "SELECT * FROM job_offer WHERE embedding IS NOT NULL AND created_at > :since " +
            "AND (expires_at IS NULL OR expires_at > now()) ORDER BY embedding <=> cast(:embedding as vector) LIMIT :limit",
        nativeQuery = true
    )
    List<JobOffer> searchVectorSince(
        @Param("embedding") Vector embedding,
        @Param("since") java.time.Instant since,
        @Param("limit") int limit
    );

    List<JobOffer> findByEmbeddingStatus(EmbeddingStatus status, Pageable pageable);

    /** Number of offers whose embedding is COMPLETED, i.e. actually indexed and searchable by the AI. */
    long countByEmbeddingStatus(EmbeddingStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE JobOffer j SET j.embeddingStatus = :status WHERE j.id IN :ids")
    void updateStatus(List<UUID> ids, EmbeddingStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE JobOffer j SET j.embeddingStatus = :status")
    void updateAllStatus(@Param("status") EmbeddingStatus status);

    // --- Purge des offres expirées ----------------------------------------------------------

    /**
     * Ids des offres expirées éligibles à la suppression pour une partition donnée, par lots bornés.
     *
     * <p>Deux garde-fous :</p>
     * <ul>
     *   <li>{@code expires_at IS NOT NULL} : beaucoup de sources ne renseignent pas la date
     *       d'expiration ; une offre sans date n'est jamais considérée comme expirée.</li>
     *   <li>{@code NOT EXISTS (job_application)} : une offre à laquelle l'utilisateur a postulé est
     *       conservée — c'est son historique de candidature, et la FK est en RESTRICT.</li>
     * </ul>
     *
     * <p>Le hachage {@code mod(abs(hashtext(id)), gridSize) = partition} isole la partition du worker.
     * Le cast en {@code bigint} avant {@code abs} évite le débordement sur
     * {@code hashtext = Integer.MIN_VALUE}, dont la valeur absolue n'est pas représentable en int.</p>
     */
    @Query(
        value = """
        SELECT j.id FROM job_offer j
        WHERE j.expires_at IS NOT NULL AND j.expires_at < :now
          AND NOT EXISTS (SELECT 1 FROM job_application a WHERE a.job_offer_id = j.id)
          AND mod(abs(hashtext(j.id::text)::bigint), :gridSize) = :partition
        ORDER BY j.expires_at
        LIMIT :limit
        """,
        nativeQuery = true
    )
    List<UUID> findExpiredOfferIds(
        @Param("now") Instant now,
        @Param("gridSize") int gridSize,
        @Param("partition") int partition,
        @Param("limit") int limit
    );

    /**
     * Supprime les hits du radar pointant sur ces offres. Nécessaire avant la suppression des offres :
     * la FK {@code fk_radar_hit_job_offer} est en RESTRICT. Contrairement aux candidatures, un hit de
     * radar est une donnée dérivée, recalculable — on peut le supprimer sans perte pour l'utilisateur.
     */
    @Modifying
    @Query(value = "DELETE FROM radar_hit WHERE job_offer_id IN (:ids)", nativeQuery = true)
    int deleteRadarHitsByOfferIds(@Param("ids") Collection<UUID> ids);

    /** Supprime un lot d'offres par id. */
    @Modifying
    @Query("DELETE FROM JobOffer j WHERE j.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);

    /** Re-queues offers that previously failed embedding (e.g. Ollama was down) so they are retried. */
    @Modifying
    @Transactional
    @Query(
        "UPDATE JobOffer j SET j.embeddingStatus = nc.opt.aiassistant.domain.enumeration.EmbeddingStatus.PENDING, " +
            "j.retryCount = 0, j.indexingError = null WHERE j.embeddingStatus = nc.opt.aiassistant.domain.enumeration.EmbeddingStatus.FAILED"
    )
    int requeueFailed();

    // --- Vérification de liveness des offres (apply_url encore joignable) --------------------

    /**
     * Offres actives d'une partition, dont l'{@code apply_url} n'a pas été vérifiée depuis
     * {@code staleBefore}, les moins récemment vérifiées d'abord. Renvoie {@code [id, apply_url]}.
     */
    @Query(
        value = """
        SELECT id, apply_url FROM job_offer
        WHERE apply_url IS NOT NULL
          AND (expires_at IS NULL OR expires_at > :now)
          AND (last_checked_at IS NULL OR last_checked_at < :staleBefore)
          AND mod(abs(hashtext(id::text)::bigint), :gridSize) = :partition
        ORDER BY last_checked_at ASC NULLS FIRST
        LIMIT :limit
        """,
        nativeQuery = true
    )
    List<Object[]> findLivenessCandidates(
        @Param("now") Instant now,
        @Param("staleBefore") Instant staleBefore,
        @Param("gridSize") int gridSize,
        @Param("partition") int partition,
        @Param("limit") int limit
    );

    /** Marks offers expired now (dead apply URL) so the daily purge removes them. */
    @Modifying
    @Transactional
    @Query("UPDATE JobOffer j SET j.expiresAt = :now WHERE j.id IN :ids")
    int markExpiredByIds(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    /** Stamps offers as checked now, whatever the liveness outcome, so they rotate to the back. */
    @Modifying
    @Transactional
    @Query("UPDATE JobOffer j SET j.lastCheckedAt = :now WHERE j.id IN :ids")
    int markCheckedByIds(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);
}
