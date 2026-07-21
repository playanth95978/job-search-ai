package nc.opt.aiassistant.service.jobcopilot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobApplicationRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.AutopsyReportDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Autopsy — closes the loop after applying.
 *
 * <p>It contrasts the user's applications that reached a <em>positive</em> outcome (interview or offer)
 * against those that ended <em>negatively</em> (rejected or ghosted), and surfaces what differs:
 * match-score, target salary, and which of the candidate's own skills recur in the offers where they
 * progressed. Everything is computed from tracked data + the applied-to offers — no LLM, reproducible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationAutopsyService {

    /** Below this many real applications, the contrast is statistical noise — we withhold the analysis. */
    private static final int MIN_SAMPLE = 5;

    /** Statuses that mean the user actually applied (DRAFT and WITHDRAWN are excluded from the funnel). */
    private static final Set<ApplicationStatus> APPLIED_STATES = EnumSet.of(
        ApplicationStatus.APPLIED,
        ApplicationStatus.INTERVIEW,
        ApplicationStatus.REJECTED,
        ApplicationStatus.GHOSTED,
        ApplicationStatus.OFFER
    );

    private static final Set<ApplicationStatus> POSITIVE_STATES = EnumSet.of(ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    private static final Set<ApplicationStatus> NEGATIVE_STATES = EnumSet.of(ApplicationStatus.REJECTED, ApplicationStatus.GHOSTED);

    private final JobApplicationRepository applicationRepository;
    private final CandidateProfileRepository profileRepository;
    private final CandidateProfileEmbeddingService profileEmbeddingService;

    @Transactional(readOnly = true)
    public AutopsyReportDto autopsy(String userId) {
        List<JobApplication> applied = applicationRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .filter(a -> a.getStatus() != null && APPLIED_STATES.contains(a.getStatus()) && a.getJobOffer() != null)
            .toList();

        List<JobApplication> positive = applied
            .stream()
            .filter(a -> POSITIVE_STATES.contains(a.getStatus()))
            .toList();
        List<JobApplication> negative = applied
            .stream()
            .filter(a -> NEGATIVE_STATES.contains(a.getStatus()))
            .toList();

        AutopsyReportDto.Funnel funnel = buildFunnel(applied, positive, negative);

        if (applied.size() < MIN_SAMPLE) {
            return new AutopsyReportDto(applied.size(), false, funnel, null, null, List.of(), List.of());
        }

        AutopsyReportDto.ScoreInsight score = scoreContrast(positive, negative);
        AutopsyReportDto.SalaryInsight salary = salaryContrast(positive, negative);
        List<AutopsyReportDto.SkillSignal> drivers = skillDrivers(userId, positive, negative);
        List<String> recommendations = recommendations(funnel, score, salary, drivers);

        return new AutopsyReportDto(applied.size(), true, funnel, score, salary, drivers, recommendations);
    }

    private AutopsyReportDto.Funnel buildFunnel(
        List<JobApplication> applied,
        List<JobApplication> positive,
        List<JobApplication> negative
    ) {
        long total = applied.size();
        long interview = applied
            .stream()
            .filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW)
            .count();
        long offer = applied
            .stream()
            .filter(a -> a.getStatus() == ApplicationStatus.OFFER)
            .count();
        long rejected = applied
            .stream()
            .filter(a -> a.getStatus() == ApplicationStatus.REJECTED)
            .count();
        long ghosted = applied
            .stream()
            .filter(a -> a.getStatus() == ApplicationStatus.GHOSTED)
            .count();
        // "Reached interview" counts both current INTERVIEW and those that progressed to OFFER.
        long reachedInterview = interview + offer;
        double rate = total == 0 ? 0 : (double) reachedInterview / total;
        double offerRate = total == 0 ? 0 : (double) offer / total;
        double ghostRate = total == 0 ? 0 : (double) ghosted / total;
        return new AutopsyReportDto.Funnel(total, reachedInterview, offer, rejected, ghosted, rate, offerRate, ghostRate);
    }

    private AutopsyReportDto.ScoreInsight scoreContrast(List<JobApplication> positive, List<JobApplication> negative) {
        Double avgPos = averageScore(positive);
        Double avgNeg = averageScore(negative);
        Double threshold = null;
        // A useful floor only when interviews score higher than rejections — the midpoint between the two.
        if (avgPos != null && avgNeg != null && avgPos > avgNeg) {
            threshold = round2(avgNeg + (avgPos - avgNeg) / 2);
        }
        return new AutopsyReportDto.ScoreInsight(avgPos, avgNeg, threshold);
    }

    private Double averageScore(List<JobApplication> apps) {
        double[] scores = apps
            .stream()
            .map(JobApplication::getMatchScore)
            .filter(java.util.Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray();
        if (scores.length == 0) return null;
        double sum = 0;
        for (double s : scores) sum += s;
        return round2(sum / scores.length);
    }

    private AutopsyReportDto.SalaryInsight salaryContrast(List<JobApplication> positive, List<JobApplication> negative) {
        Integer medPos = medianSalary(positive);
        Integer medNeg = medianSalary(negative);
        // Only worth surfacing when both exist and differ by more than 10%.
        boolean signal = medPos != null && medNeg != null && Math.abs(medPos - medNeg) > 0.10 * Math.max(medPos, medNeg);
        return new AutopsyReportDto.SalaryInsight(medPos, medNeg, signal);
    }

    private Integer medianSalary(List<JobApplication> apps) {
        List<Integer> values = apps
            .stream()
            .map(JobApplication::getJobOffer)
            .map(this::offerSalary)
            .filter(java.util.Objects::nonNull)
            .sorted()
            .toList();
        if (values.isEmpty()) return null;
        return values.get(values.size() / 2);
    }

    /** A single salary figure per offer: the midpoint when a range exists, else whichever bound is set. */
    private Integer offerSalary(JobOffer offer) {
        Integer min = offer.getSalaryMin();
        Integer max = offer.getSalaryMax();
        if (min != null && max != null) return (min + max) / 2;
        return min != null ? min : max;
    }

    /**
     * Candidate skills that recur more in positive-outcome offers than negative ones. Offer-side skills
     * are sparse in the corpus, so we detect a skill by its presence in the offer's {@code searchText},
     * which is always populated.
     */
    private List<AutopsyReportDto.SkillSignal> skillDrivers(String userId, List<JobApplication> positive, List<JobApplication> negative) {
        List<String> skills = profileRepository.findByUserId(userId).map(this::candidateSkills).orElseGet(List::of);
        if (skills.isEmpty() || positive.isEmpty()) return List.of();

        List<String> posText = searchTexts(positive);
        List<String> negText = searchTexts(negative);
        if (posText.isEmpty()) return List.of();

        List<AutopsyReportDto.SkillSignal> signals = new ArrayList<>();
        for (String skill : skills) {
            String needle = skill.toLowerCase(Locale.ROOT);
            long pos = posText
                .stream()
                .filter(t -> t.contains(needle))
                .count();
            long neg = negText
                .stream()
                .filter(t -> t.contains(needle))
                .count();
            if (pos < 2) continue; // require the skill to actually recur in wins
            double posShare = (double) pos / posText.size();
            double negShare = negText.isEmpty() ? 0 : (double) neg / negText.size();
            double lift = posShare / (negShare + 0.05); // smoothing avoids divide-by-zero
            signals.add(new AutopsyReportDto.SkillSignal(skill, pos, neg, round2(lift)));
        }
        return signals.stream().sorted(Comparator.comparingDouble(AutopsyReportDto.SkillSignal::lift).reversed()).limit(5).toList();
    }

    private List<String> candidateSkills(CandidateProfile profile) {
        return profileEmbeddingService
            .extractSkillNames(profile)
            .stream()
            .filter(s -> s.length() > 1)
            .toList();
    }

    private List<String> searchTexts(List<JobApplication> apps) {
        return apps
            .stream()
            .map(a -> a.getJobOffer().getSearchText())
            .filter(t -> t != null && !t.isBlank())
            .map(t -> t.toLowerCase(Locale.ROOT))
            .toList();
    }

    /** Rule-based, evidence-derived one-liners; only emitted when the underlying signal is present. */
    private List<String> recommendations(
        AutopsyReportDto.Funnel funnel,
        AutopsyReportDto.ScoreInsight score,
        AutopsyReportDto.SalaryInsight salary,
        List<AutopsyReportDto.SkillSignal> drivers
    ) {
        List<String> out = new ArrayList<>();
        if (score.suggestedThreshold() != null) {
            out.add(
                "Vos entretiens se concentrent au-dessus d'un score de match d'environ " +
                    Math.round(score.suggestedThreshold()) +
                    " — priorisez les offres au-dessus de ce seuil."
            );
        }
        if (funnel.ghostRate() > 0.5 && funnel.applied() >= MIN_SAMPLE) {
            out.add(
                "Plus de la moitié de vos candidatures restent sans réponse — resserrez le ciblage (pertinence, localisation, séniorité)."
            );
        }
        if (!drivers.isEmpty() && drivers.getFirst().lift() > 1.3) {
            out.add(
                "« " + drivers.getFirst().skill() + " » revient souvent dans les offres où vous avancez — mettez-la davantage en avant."
            );
        }
        if (
            salary.signal() &&
            salary.medianNegative() != null &&
            salary.medianPositive() != null &&
            salary.medianNegative() > salary.medianPositive()
        ) {
            out.add(
                "Les offres qui ne répondent pas visent un salaire plus élevé — vérifiez que votre séniorité est cohérente avec la cible."
            );
        }
        return out;
    }

    private static Double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
