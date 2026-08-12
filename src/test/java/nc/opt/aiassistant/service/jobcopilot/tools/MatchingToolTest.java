package nc.opt.aiassistant.service.jobcopilot.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.JobOfferSearchService;
import nc.opt.aiassistant.service.jobcopilot.dto.JobOfferDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTag;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTagDto;
import nc.opt.aiassistant.service.jobcopilot.matching.JobMatchingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class MatchingToolTest {

    @Mock
    private JobMatchingService matchingService;

    @Mock
    private JobOfferSearchService jobOfferSearchService;

    @Mock
    private CandidateProfileRepository profileRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchingTool matchingTool;

    private static final ToolContext CONTEXT = new ToolContext(java.util.Map.of("userId", "alice"));

    private CandidateProfile profile() {
        CandidateProfile profile = new CandidateProfile();
        profile.setUserId("alice");
        profile.setLocation("Nouméa");
        profile.setPreferredRoles("[\"Développeur back-end\"]");
        profile.setSkills("[\"Java\",\"Spring Boot\"]");
        return profile;
    }

    private JobOffer offer() {
        JobOffer offer = new JobOffer();
        offer.setId(UUID.randomUUID());
        offer.setTitle("Senior Java Developer");
        offer.setCompany("ACME");
        return offer;
    }

    private MatchResultDto match(double score) {
        JobOfferDto dto = new JobOfferDto(
            UUID.randomUUID(),
            "Senior Java Developer",
            "ACME",
            "Brisbane",
            false,
            null,
            List.of(),
            null,
            "https://acme.example/apply",
            110000,
            130000,
            "CDI"
        );
        return new MatchResultDto(dto, score, List.of(MatchTagDto.of(MatchTag.MATCHED_SKILL, "java")), List.of());
    }

    @Test
    void requiresAProfile() {
        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc("alice")).thenReturn(Optional.empty());

        assertThat(matchingTool.matchJobs("java", null, null, CONTEXT)).contains("Upload a CV first");
        verifyNoInteractions(jobOfferSearchService, matchingService);
    }

    @Test
    void retrievesCandidatesThroughTheExistingSearchEngineAndForwardsTheLocation() {
        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc("alice")).thenReturn(Optional.of(profile()));
        when(jobOfferSearchService.searchHybrid(anyString(), anyInt())).thenReturn(List.of(offer()));
        when(matchingService.matchJobs(any(), any(), any())).thenReturn(List.of(match(0.91)));

        String rendered = matchingTool.matchJobs("senior Java Angular", "Brisbane", 5, CONTEXT);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jobOfferSearchService).searchHybrid(query.capture(), anyInt());
        assertThat(query.getValue()).isEqualTo("senior Java Angular Brisbane");
        verify(matchingService).matchJobs(any(), any(), eq("Brisbane"));
        assertThat(rendered).contains("91% Match", "Senior Java Developer", "Brisbane", "https://acme.example/apply");
    }

    @Test
    void fallsBackOnTheProfileWhenNoQueryIsGiven() {
        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc("alice")).thenReturn(Optional.of(profile()));
        when(jobOfferSearchService.searchHybrid(anyString(), anyInt())).thenReturn(List.of(offer()));
        when(matchingService.matchJobs(any(), any(), any())).thenReturn(List.of(match(0.7)));

        matchingTool.matchJobs(null, null, null, CONTEXT);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jobOfferSearchService).searchHybrid(query.capture(), anyInt());
        assertThat(query.getValue()).isEqualTo("Développeur back-end Java Spring Boot");
    }

    @Test
    void capsTheNumberOfReturnedMatches() {
        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc("alice")).thenReturn(Optional.of(profile()));
        when(jobOfferSearchService.searchHybrid(anyString(), anyInt())).thenReturn(List.of(offer()));
        when(matchingService.matchJobs(any(), any(), any())).thenReturn(List.of(match(0.9), match(0.8), match(0.7)));

        String rendered = matchingTool.matchJobs("java", null, 2, CONTEXT);

        assertThat(rendered.split("% Match", -1)).hasSize(3); // 2 matches rendered out of 3 scored
    }

    @Test
    void reportsAnEmptySearchInsteadOfScoringNothing() {
        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc("alice")).thenReturn(Optional.of(profile()));
        when(jobOfferSearchService.searchHybrid(anyString(), anyInt())).thenReturn(List.of());

        assertThat(matchingTool.matchJobs("cobol", null, null, CONTEXT)).contains("No job offer matched");
        verifyNoInteractions(matchingService);
    }
}
