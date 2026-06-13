package nc.opt.aiassistant.service.jobcopilot.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.UserPreference;
import nc.opt.aiassistant.repository.jobcopilot.UserPreferenceRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.JobOfferDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillDto;
import nc.opt.aiassistant.service.reranker.RerankerService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobMatchingService {

    private final VectorStore vectorStore;
    private final RerankerService rerankerService;
    private final UserPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;

    public JobMatchingService(
        @Qualifier("pdfVectorStore") VectorStore pdfVectorStore,
        RerankerService rerankerService,
        UserPreferenceRepository preferenceRepository,
        ObjectMapper objectMapper
    ) {
        this.vectorStore = pdfVectorStore;
        this.rerankerService = rerankerService;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
    }

    /** Matches a candidate profile against a list of job offers using hybrid scoring. */
    public List<MatchResultDto> matchJobs(CandidateProfile profile, List<JobOffer> offers) {
        Set<String> candidateSkills = parseSkills(profile.getSkills());
        UserPreferenceRepository preferenceRepository1 = preferenceRepository;
        UserPreference prefs = preferenceRepository1.findByUserId(profile.getUserId()).orElse(null);

        return offers
            .stream()
            .map(offer -> scoreOffer(profile, candidateSkills, offer, prefs))
            .sorted(Comparator.comparingDouble(MatchResultDto::score).reversed())
            .toList();
    }

    /** Performs semantic search for job offers matching the candidate CV chunks. */
    public List<Document> semanticSearch(String query, int topK) {
        SearchRequest request = SearchRequest.builder().query(query).topK(topK).build();
        List<Document> results = vectorStore.similaritySearch(request);
        return rerankerService.rerank(query, results, topK);
    }

    MatchResultDto scoreOffer(CandidateProfile profile, Set<String> candidateSkills, JobOffer offer, UserPreference prefs) {
        Set<String> jobSkills = parseSkills(offer.getSkills());
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();

        double skillScore = computeSkillScore(candidateSkills, jobSkills, strengths, weaknesses);
        double locationScore = computeLocationScore(profile, offer, prefs, strengths, weaknesses);
        double remoteScore = computeRemoteScore(offer, prefs, strengths, weaknesses);
        double salaryScore = computeSalaryScore(offer, prefs, strengths, weaknesses);

        double finalScore = skillScore * 0.50 + locationScore * 0.20 + remoteScore * 0.15 + salaryScore * 0.15;

        return new MatchResultDto(toDto(offer), Math.round(finalScore * 100.0) / 100.0, strengths, weaknesses);
    }

    private double computeSkillScore(Set<String> candidate, Set<String> job, List<String> s, List<String> w) {
        if (job.isEmpty()) return 0.5;
        long matched = job.stream().filter(candidate::contains).count();
        job.stream()
            .filter(candidate::contains)
            .forEach(sk -> s.add("+ " + sk));
        job.stream()
            .filter(sk -> !candidate.contains(sk))
            .forEach(sk -> w.add("- Missing " + sk));
        return (double) matched / job.size();
    }

    private double computeLocationScore(CandidateProfile profile, JobOffer offer, UserPreference prefs, List<String> s, List<String> w) {
        if (offer.getLocation() == null || profile.getLocation() == null) return 0.5;
        if (offer.getLocation().toLowerCase().contains(profile.getLocation().toLowerCase())) {
            s.add("+ Location match");
            return 1.0;
        }
        if (prefs != null && prefs.getPreferredLocations() != null) {
            List<String> locs = parseList(prefs.getPreferredLocations());
            boolean match = locs.stream().anyMatch(l -> offer.getLocation().toLowerCase().contains(l.toLowerCase()));
            if (match) {
                s.add("+ Preferred location");
                return 0.8;
            }
        }
        w.add("- Location mismatch");
        return 0.2;
    }

    private double computeRemoteScore(JobOffer offer, UserPreference prefs, List<String> s, List<String> w) {
        if (prefs == null || prefs.getRemoteOnly() == null || !prefs.getRemoteOnly()) return 0.5;
        if (Boolean.TRUE.equals(offer.getRemote())) {
            s.add("+ Remote");
            return 1.0;
        }
        w.add("- Not remote");
        return 0.0;
    }

    private double computeSalaryScore(JobOffer offer, UserPreference prefs, List<String> s, List<String> w) {
        if (prefs == null || prefs.getSalaryMin() == null || offer.getSalaryMax() == null) return 0.5;
        if (offer.getSalaryMax() >= prefs.getSalaryMin()) {
            s.add("+ Salary compatible");
            return 1.0;
        }
        w.add("- Salary below expectations");
        return 0.2;
    }

    private Set<String> parseSkills(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            // Check if it's the new structured format or the old string list
            if (json.trim().startsWith("[{\"")) {
                List<SkillDto> list = objectMapper.readValue(json, new TypeReference<>() {});
                return list.stream()
                    .map(SkillDto::name)
                    .filter(java.util.Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            } else {
                List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
                return list.stream().map(String::toLowerCase).collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("Failed to parse skills JSON: {}", json);
            return Set.of();
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private JobOfferDto toDto(JobOffer o) {
        return new JobOfferDto(
            o.getId(),
            o.getTitle(),
            o.getCompany(),
            o.getLocation(),
            Boolean.TRUE.equals(o.getRemote()),
            o.getDescription(),
            parseList(o.getSkills()),
            o.getSource() != null ? o.getSource().name() : null,
            o.getApplyUrl(),
            o.getSalaryMin(),
            o.getSalaryMax(),
            o.getContractType()
        );
    }
}
