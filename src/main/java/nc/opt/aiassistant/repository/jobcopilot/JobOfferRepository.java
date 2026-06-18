package nc.opt.aiassistant.repository.jobcopilot;

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

    @Query("SELECT j FROM JobOffer j WHERE LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<JobOffer> searchByKeyword(String keyword);

    @Query(value = "SELECT * FROM job_offer WHERE search_text ||| :query ORDER BY paradedb.score(id) DESC LIMIT :limit", nativeQuery = true)
    List<JobOffer> searchBM25(String query, int limit);

    @Query(value = "SELECT * FROM job_offer ORDER BY embedding <=> cast(:embedding as vector) LIMIT :limit", nativeQuery = true)
    List<JobOffer> searchVector(@Param("embedding") Vector embedding, @Param("limit") int limit);

    List<JobOffer> findByEmbeddingStatus(EmbeddingStatus status, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE JobOffer j SET j.embeddingStatus = :status WHERE j.id IN :ids")
    void updateStatus(List<UUID> ids, EmbeddingStatus status);
}
