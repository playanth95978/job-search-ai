package nc.opt.aiassistant.service.jobcopilot.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.ats.JobSearchService;
import nc.opt.aiassistant.service.jobcopilot.career.SkillGapAnalyzer.OfferSkills;
import nc.opt.aiassistant.service.jobcopilot.dto.CareerRoadmapDto;
import nc.opt.aiassistant.service.jobcopilot.dto.JobOfferDto;
import nc.opt.aiassistant.service.jobcopilot.dto.RoadmapStepDto;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillDto;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillGapDto;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Predictive career-path simulator. Plans the candidate's "next move" toward a targeted senior role:
 *
 * <ol>
 *   <li><b>Retrieval (deterministic, vector):</b> semantic search over the offer DB for the target role.</li>
 *   <li><b>Skill gap (deterministic):</b> {@link SkillGapAnalyzer} aggregates the skills demanded by those
 *       offers, diffs them against the candidate and scores readiness — grounded in real offers, no LLM.</li>
 *   <li><b>Roadmap (LLM):</b> the model sequences intermediate roles, certifications and projects to close
 *       the gap. World knowledge the DB doesn't hold; each step is then grounded back to the real offers.</li>
 * </ol>
 */
@Slf4j
@Service
public class CareerPathService {

    /** How many senior offers to retrieve and aggregate the skill gap over. */
    private static final int OFFERS_TO_ANALYZE = 30;
    /** How many real offers to surface to the user for exploration. */
    private static final int SAMPLE_OFFERS = 6;
    /** Cap on missing skills fed to the LLM (keeps the prompt focused on what matters most). */
    private static final int TOP_MISSING_FOR_LLM = 12;
    /** Cap on evidence offers attached to a roadmap step. */
    private static final int EVIDENCE_PER_STEP = 4;

    private final CandidateProfileRepository profileRepository;
    private final JobSearchService jobSearchService;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public CareerPathService(
        CandidateProfileRepository profileRepository,
        JobSearchService jobSearchService,
        ObjectMapper objectMapper,
        @Qualifier("mistralAiChatModel") ChatModel chatModel
    ) {
        this.profileRepository = profileRepository;
        this.jobSearchService = jobSearchService;
        this.objectMapper = objectMapper;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public CareerRoadmapDto simulate(String targetRole, int horizonYears, String language) {
        if (targetRole == null || targetRole.isBlank()) {
            throw new BadRequestAlertException("Target role is required", "career", "targetRoleRequired");
        }
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new BadRequestAlertException("Current user login not found", "career", "nologin")
        );
        CandidateProfile profile = profileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new BadRequestAlertException("No candidate profile found", "career", "profileNotFound"));

        // 1. Retrieval — vector search only (senior targeting handled by the semantic ranking).
        List<JobOffer> offers = jobSearchService.searchSemantic(targetRole, OFFERS_TO_ANALYZE);

        if (offers.isEmpty()) {
            log.info("Career simulation for '{}': no offers found in the DB", targetRole);
            return new CareerRoadmapDto(targetRole, horizonYears, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        // 2. Skill gap — deterministic aggregation over the retrieved offers.
        Set<String> candidateSkills = parseSkills(profile.getSkills());
        List<OfferSkills> offerSkills = offers
            .stream()
            .map(o -> new OfferSkills(o.getId(), parseSkills(o.getSkills())))
            .toList();
        SkillGapAnalyzer.Result gap = SkillGapAnalyzer.analyze(candidateSkills, offerSkills);

        // 3. Roadmap — LLM sequences the learning path, then we ground each step to real offers.
        List<RoadmapStepDto> steps = new ArrayList<>();
        List<String> intermediateRoles = List.of();
        try {
            RoadmapLlm roadmap = generateRoadmap(targetRole, horizonYears, candidateSkills, gap.gaps(), offers, language);
            if (roadmap != null) {
                intermediateRoles = roadmap.intermediateRoles() == null ? List.of() : roadmap.intermediateRoles();
                steps = groundSteps(roadmap.steps(), gap.evidenceBySkill());
            }
        } catch (Exception e) {
            log.error("Roadmap generation failed for '{}': {}", targetRole, e.getMessage());
        }

        List<JobOfferDto> sampleOffers = offers.stream().limit(SAMPLE_OFFERS).map(this::toDto).toList();

        return new CareerRoadmapDto(
            targetRole,
            horizonYears,
            gap.readinessScore(),
            gap.offersAnalyzed(),
            gap.gaps(),
            intermediateRoles,
            steps,
            sampleOffers
        );
    }

    // ---- LLM roadmap ----

    private RoadmapLlm generateRoadmap(
        String targetRole,
        int horizonYears,
        Set<String> ownedSkills,
        List<SkillGapDto> gaps,
        List<JobOffer> offers,
        String language
    ) {
        String missing = gaps
            .stream()
            .filter(g -> !g.owned())
            .limit(TOP_MISSING_FOR_LLM)
            .map(g -> "%s (demanded by %d%% of offers)".formatted(g.skill(), g.demandPct()))
            .collect(Collectors.joining(", "));
        String owned = String.join(", ", ownedSkills);
        String sampleTitles = offers
            .stream()
            .limit(SAMPLE_OFFERS)
            .map(o -> "- %s%s".formatted(o.getTitle(), o.getSkills() == null ? "" : " [" + o.getSkills() + "]"))
            .collect(Collectors.joining("\n"));
        String languageInstruction = (language == null || language.isBlank())
            ? "Write all text in the same language as the target role."
            : "Write all text (titles, rationales, roles) in " + language + ".";

        String prompt = """
            You are a senior career coach for tech professionals. A candidate wants to reach the role of "%s" within %d years.

            CANDIDATE'S CURRENT SKILLS: %s

            MOST DEMANDED SKILLS THEY ARE MISSING (from real job offers): %s

            SAMPLE REAL TARGET OFFERS:
            %s

            Build a realistic, ordered learning roadmap to close the gap and reach the target role.
            %s
            Rules:
            - Order steps from what to do first to last.
            - Prefer concrete, named items: real certifications (e.g. "AWS Certified Solutions Architect – Associate"),
              hands-on projects, or intermediate job roles to hold along the way.
            - Each step must target one or more of the MISSING skills listed above; put those skill names verbatim in "relatedSkills".
            - Keep 4 to 7 steps. Keep rationales to one sentence.

            Return ONLY valid JSON with this exact structure:
            {
              "intermediateRoles": ["role between now and the target", "..."],
              "steps": [
                {
                  "type": "ROLE | CERTIFICATION | PROJECT | SKILL",
                  "title": "short label of the step",
                  "rationale": "one sentence on why it matters for the target role",
                  "relatedSkills": ["one of the missing skills", "..."]
                }
              ]
            }
            """.formatted(
            targetRole,
            horizonYears,
            owned.isBlank() ? "(none extracted)" : owned,
            missing.isBlank() ? "(candidate already covers the common skills)" : missing,
            sampleTitles,
            languageInstruction
        );

        return chatClient.prompt().user(prompt).call().entity(RoadmapLlm.class);
    }

    /** Turns raw LLM steps into ordered DTOs, attaching the real offers that demand each step's skills. */
    private List<RoadmapStepDto> groundSteps(List<StepLlm> rawSteps, java.util.Map<String, List<UUID>> evidenceBySkill) {
        if (rawSteps == null) {
            return List.of();
        }
        List<RoadmapStepDto> steps = new ArrayList<>();
        int order = 1;
        for (StepLlm raw : rawSteps) {
            if (raw == null || raw.title() == null || raw.title().isBlank()) {
                continue;
            }
            List<String> related = raw.relatedSkills() == null ? List.of() : raw.relatedSkills();
            Set<UUID> evidence = new LinkedHashSet<>();
            for (String skill : related) {
                List<UUID> ids = evidenceBySkill.get(SkillGapAnalyzer.normalize(skill));
                if (ids != null) {
                    evidence.addAll(ids);
                }
            }
            List<UUID> evidenceIds = evidence.stream().limit(EVIDENCE_PER_STEP).toList();
            steps.add(new RoadmapStepDto(order++, parseType(raw.type()), raw.title().trim(), raw.rationale(), related, evidenceIds));
        }
        return steps;
    }

    private RoadmapStepDto.StepType parseType(String type) {
        if (type == null) {
            return RoadmapStepDto.StepType.SKILL;
        }
        try {
            return RoadmapStepDto.StepType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RoadmapStepDto.StepType.SKILL;
        }
    }

    // ---- skill / offer parsing (mirrors JobMatchingService: supports old string-list and new [{name}] formats) ----

    private Set<String> parseSkills(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            if (json.trim().startsWith("[{\"")) {
                List<SkillDto> list = objectMapper.readValue(json, new TypeReference<>() {});
                return list.stream().map(SkillDto::name).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            }
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            log.warn("Failed to parse skills JSON: {}", json);
            return Set.of();
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            if (json.trim().startsWith("[{\"")) {
                List<SkillDto> list = objectMapper.readValue(json, new TypeReference<>() {});
                return list.stream().map(SkillDto::name).filter(java.util.Objects::nonNull).toList();
            }
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

    // ---- LLM response shapes ----

    record RoadmapLlm(List<String> intermediateRoles, List<StepLlm> steps) {}

    record StepLlm(String type, String title, String rationale, List<String> relatedSkills) {}
}
