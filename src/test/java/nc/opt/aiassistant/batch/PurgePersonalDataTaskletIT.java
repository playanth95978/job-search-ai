package nc.opt.aiassistant.batch;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import nc.opt.aiassistant.IntegrationTest;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.PersonalDataRetentionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vérifie la purge RGPD contre un vrai PostgreSQL — l'intérêt du test est la requête native
 * {@code findDormantUserIds} : agrégats imbriqués, {@code UNION ALL} et {@code GREATEST}, qu'aucune
 * vérification à la compilation ne couvre.
 */
@Transactional
@IntegrationTest
class PurgePersonalDataTaskletIT {

    private static final int RETENTION_DAYS = 730;

    @Autowired
    private PersonalDataRetentionRepository retentionRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findDormantUserIds_returnsOnlyUsersInactiveBeyondRetention() {
        String dormant = "gdpr-dormant-" + UUID.randomUUID();
        String active = "gdpr-active-" + UUID.randomUUID();

        persistProfile(dormant, Instant.now().minus(RETENTION_DAYS + 30L, ChronoUnit.DAYS));
        persistProfile(active, Instant.now().minus(10, ChronoUnit.DAYS));
        flush();

        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        assertThat(retentionRepository.findDormantUserIds(cutoff, 100)).contains(dormant).doesNotContain(active);
    }

    /** Une trace récente sur une seule table suffit à conserver tout l'historique de l'utilisateur. */
    @Test
    void findDormantUserIds_ignoresUserWithOneRecentTrace() {
        String userId = "gdpr-mixed-" + UUID.randomUUID();

        persistProfile(userId, Instant.now().minus(RETENTION_DAYS + 60L, ChronoUnit.DAYS));
        // Conversation récente : l'utilisateur est revenu, la conservation repart de cette date.
        entityManager
            .createNativeQuery("INSERT INTO conversation (id, user_id, title, created_at) VALUES (?, ?, ?, ?)")
            .setParameter(1, UUID.randomUUID())
            .setParameter(2, userId)
            .setParameter(3, "retention-test")
            .setParameter(4, Instant.now().minus(5, ChronoUnit.DAYS))
            .executeUpdate();
        flush();

        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        assertThat(retentionRepository.findDormantUserIds(cutoff, 100)).doesNotContain(userId);
    }

    @Test
    void deleteCandidateProfiles_erasesTheProfile() {
        String userId = "gdpr-erase-" + UUID.randomUUID();
        persistProfile(userId, Instant.now().minus(RETENTION_DAYS + 30L, ChronoUnit.DAYS));
        flush();

        assertThat(retentionRepository.deleteApplications(java.util.List.of(userId))).isZero();
        assertThat(retentionRepository.deleteCandidateProfiles(java.util.List.of(userId))).isEqualTo(1);
        flush();

        assertThat(candidateProfileRepository.findTopByUserIdOrderByCreatedAtDesc(userId)).isEmpty();
    }

    private void persistProfile(String userId, Instant at) {
        CandidateProfile profile = new CandidateProfile();
        profile.setUserId(userId);
        profile.setFullName("Retention Test");
        profile.setCreatedAt(at);
        profile.setUpdatedAt(at);
        candidateProfileRepository.save(profile);
    }

    /** Les requêtes natives ne voient pas le contexte de persistance : il faut le vider d'abord. */
    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
