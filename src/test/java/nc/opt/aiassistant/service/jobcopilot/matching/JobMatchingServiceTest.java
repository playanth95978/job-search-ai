package nc.opt.aiassistant.service.jobcopilot.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.UserPreference;
import nc.opt.aiassistant.repository.jobcopilot.UserPreferenceRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTag;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTagDto;
import nc.opt.aiassistant.service.reranker.RerankerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class JobMatchingServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RerankerService rerankerService;

    @Mock
    private UserPreferenceRepository preferenceRepository;

    private ObjectMapper objectMapper;
    private JobMatchingService jobMatchingService;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jobMatchingService = new JobMatchingService(vectorStore, rerankerService, preferenceRepository, objectMapper);
    }

    @Test
    void shouldMatchJobsWithRealCase() {
        // GIVEN: Un candidat Java à Nouméa
        CandidateProfile profile = new CandidateProfile();
        profile.setUserId(USER_ID);
        profile.setLocation("Nouméa");
        profile.setSkills("[\"Java\", \"Spring Boot\", \"Angular\"]");

        // GIVEN: Des préférences (Salaire min 500k, remote requis)
        UserPreference prefs = new UserPreference();
        prefs.setUserId(USER_ID);
        prefs.setSalaryMin(500000);
        prefs.setRemoteOnly(true);
        prefs.setPreferredLocations("[\"Nouméa\", \"Païta\"]");

        when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        // GIVEN: 3 offres d'emploi
        // 1. Match parfait (Compétences, Lieu, Salaire)
        JobOffer offer1 = new JobOffer();
        offer1.setId(UUID.randomUUID());
        offer1.setTitle("Développeur Fullstack Java/Angular");
        offer1.setLocation("Nouméa, Nouvelle-Calédonie");
        offer1.setSkills("[\"Java\", \"Spring Boot\", \"Angular\"]");
        offer1.setSalaryMax(600000);
        offer1.setRemote(true);

        // 2. Match partiel (Compétences OK, Lieu KO mais dans pref, Salaire KO)
        JobOffer offer2 = new JobOffer();
        offer2.setId(UUID.randomUUID());
        offer2.setTitle("Développeur Java");
        offer2.setLocation("Païta");
        offer2.setSkills("[\"Java\", \"Docker\"]");
        offer2.setSalaryMax(450000);

        // 3. Mauvais match (Compétences KO, Lieu KO)
        JobOffer offer3 = new JobOffer();
        offer3.setId(UUID.randomUUID());
        offer3.setTitle("Comptable");
        offer3.setLocation("Koné");
        offer3.setSkills("[\"Comptabilité\", \"Excel\"]");
        offer3.setSalaryMax(400000);

        List<JobOffer> offers = List.of(offer1, offer2, offer3);

        // WHEN
        List<MatchResultDto> results = jobMatchingService.matchJobs(profile, offers);

        // THEN
        assertThat(results).hasSize(3);

        // Vérification du match parfait (offer1)
        MatchResultDto bestMatch = results.get(0);
        assertThat(bestMatch.jobOffer().title()).isEqualTo(offer1.getTitle());
        assertThat(bestMatch.score()).isGreaterThan(0.8);
        assertThat(bestMatch.strengths())
            .extracting(MatchTagDto::key)
            .contains(MatchTag.MATCHED_SKILL.key(), MatchTag.LOCATION_MATCH.key(), MatchTag.SALARY_COMPATIBLE.key(), MatchTag.REMOTE.key());
        assertThat(bestMatch.strengths()).anySatisfy(tag -> {
            assertThat(tag.key()).isEqualTo(MatchTag.MATCHED_SKILL.key());
            assertThat(tag.skill()).isIn("java", "spring boot", "angular");
        });

        // Vérification du match partiel (offer2)
        MatchResultDto partialMatch = results.get(1);
        assertThat(partialMatch.jobOffer().title()).isEqualTo(offer2.getTitle());
        assertThat(partialMatch.score()).isBetween(0.4, 0.7);
        assertThat(partialMatch.strengths()).extracting(MatchTagDto::key).contains(MatchTag.PREFERRED_LOCATION.key());
        assertThat(partialMatch.weaknesses()).extracting(MatchTagDto::key).contains(MatchTag.SALARY_BELOW.key());

        // Vérification du mauvais match (offer3)
        MatchResultDto poorMatch = results.get(2);
        assertThat(poorMatch.score()).isLessThan(0.4);
    }

    @Test
    void shouldPerformSemanticSearch() {
        // GIVEN
        String query = "Développeur Java";
        int topK = 5;
        List<Document> mockDocuments = List.of(new Document("doc1"), new Document("doc2"));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments);
        when(rerankerService.rerank(eq(query), eq(mockDocuments), eq(topK))).thenReturn(mockDocuments);

        // WHEN
        List<Document> results = jobMatchingService.semanticSearch(query, topK);

        // THEN
        assertThat(results).hasSize(2);
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(rerankerService).rerank(query, mockDocuments, topK);
    }
}
