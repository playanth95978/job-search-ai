package nc.opt.aiassistant.batch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.repository.jobcopilot.PersonalDataRetentionRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * Purge RGPD : efface, lot par lot, les données personnelles des utilisateurs dormants.
 *
 * <p><b>Pourquoi un tasklet plutôt qu'un step chunk-oriented</b> : comme pour la purge des offres
 * ({@link PurgeExpiredOffersTasklet}), un {@code ItemReader} paginé lirait une cible mouvante — les
 * lignes disparaissent au fil des suppressions et les offsets sautent des utilisateurs. Le tasklet
 * relit toujours la tête du jeu restant et renvoie {@link RepeatStatus#CONTINUABLE} tant qu'il reste
 * du travail : Spring Batch ouvre une transaction par invocation, ce qui donne le découpage
 * transactionnel, la reprise et les compteurs sans code supplémentaire.</p>
 *
 * <p><b>Atomicité par lot</b> : l'ensemble des tables d'un lot est effacé dans la même transaction.
 * Un échec en cours de route laisse le lot intact plutôt qu'un utilisateur à moitié effacé — état
 * qui serait à la fois incohérent fonctionnellement et impossible à justifier vis-à-vis du RGPD.</p>
 *
 * <p>L'ordre des suppressions suit les clés étrangères : enfants avant parents.</p>
 */
@Slf4j
public class PurgePersonalDataTasklet implements Tasklet {

    private final PersonalDataRetentionRepository repository;
    private final int retentionDays;
    private final int batchSize;

    public PurgePersonalDataTasklet(PersonalDataRetentionRepository repository, int retentionDays, int batchSize) {
        this.repository = repository;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<String> dormant = repository.findDormantUserIds(cutoff, batchSize);

        if (dormant.isEmpty()) {
            return RepeatStatus.FINISHED;
        }

        // Enfants avant parents : resume_version -> resume, job_application -> candidate_profile.
        int tailored = repository.deleteTailoredResumes(dormant);
        int positionings = repository.deletePositionings(dormant);
        int radarHits = repository.deleteRadarHits(dormant);
        int radarStates = repository.deleteRadarStates(dormant);
        int preferences = repository.deleteUserPreferences(dormant);
        int resumeVersions = repository.deleteResumeVersions(dormant);
        int resumes = repository.deleteResumes(dormant);
        int applications = repository.deleteApplications(dormant);
        int profiles = repository.deleteCandidateProfiles(dormant);
        int conversations = repository.deleteConversations(dormant);

        int rows =
            tailored +
            positionings +
            radarHits +
            radarStates +
            preferences +
            resumeVersions +
            resumes +
            applications +
            profiles +
            conversations;
        contribution.incrementWriteCount(rows);

        // Trace de conformité : on journalise les volumes, jamais les identifiants — un log de purge
        // qui listerait les utilisateurs recréerait la donnée personnelle qu'on vient d'effacer.
        log.info(
            "Purge RGPD : {} utilisateur(s) dormant(s) au-delà de {} jours, {} ligne(s) effacée(s) " +
                "(profils={}, candidatures={}, CV={}, versions CV={}, positionnements={}, CV adaptés={}, " +
                "radar={}, préférences={}, conversations={})",
            dormant.size(),
            retentionDays,
            rows,
            profiles,
            applications,
            resumes,
            resumeVersions,
            positionings,
            tailored,
            radarHits + radarStates,
            preferences,
            conversations
        );

        // Lot plein : il reste probablement des utilisateurs, on redemande un tour (nouvelle transaction).
        return dormant.size() < batchSize ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
    }
}
