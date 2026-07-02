package nc.opt.aiassistant.service.jobcopilot.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.OfferCacheInvalidationService;
import nc.opt.aiassistant.service.jobcopilot.dto.AtsSimulationDto;
import nc.opt.aiassistant.service.jobcopilot.dto.AtsWarningDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CandidateProfileDto;
import nc.opt.aiassistant.service.mapper.CandidateProfileMapper;
import nc.opt.aiassistant.service.pdf.MistralOCRProcessService;
import nc.opt.aiassistant.service.pdf.VectorPDFService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class CvIngestionServiceTest {

    @Mock
    private MistralOCRProcessService ocrService;

    @Mock
    private VectorPDFService vectorPDFService;

    @Mock
    private CandidateProfileRepository profileRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private CandidateProfileMapper candidateProfileMapper;

    @Mock
    private OfferCacheInvalidationService offerCacheInvalidationService;

    private ObjectMapper objectMapper;
    private CvIngestionService cvIngestionService;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cvIngestionService = new CvIngestionService(
            ocrService,
            vectorPDFService,
            profileRepository,
            chatModel,
            objectMapper,
            candidateProfileMapper,
            offerCacheInvalidationService
        );
    }

    @Test
    void shouldSimulateAtsParsingSuccessfully() {
        // GIVEN
        CandidateProfile profile = new CandidateProfile();
        profile.setId(UUID.randomUUID());
        profile.setUserId(USER_ID);
        profile.setRawMarkdown("Raw CV content here with Java and Spring Boot.");

        CandidateProfileDto profileDto = new CandidateProfileDto(
            profile.getId(),
            USER_ID,
            "John Doe",
            "john.doe@example.com",
            "Noumea",
            5,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            profile.getRawMarkdown(),
            "cv.pdf",
            null,
            null
        );

        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(profile));
        when(candidateProfileMapper.toDto(profile)).thenReturn(profileDto);

        // Mock ChatModel and ChatResponse
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = mock(AssistantMessage.class);

        String mockLlmJson = """
            {
              "warnings": [
                {
                  "type": "Formatting",
                  "level": "WARNING",
                  "message": "Double columns might scramble text.",
                  "snippet": "Java Developer"
                }
              ],
              "recommendations": [
                "Use a single column layout."
              ]
            }
            """;

        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // WHEN
        AtsSimulationDto result = cvIngestionService.simulateAtsParsing(USER_ID, "fr");

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.rawText()).isEqualTo(profile.getRawMarkdown());
        assertThat(result.warnings()).hasSize(1);

        AtsWarningDto warning = result.warnings().get(0);
        assertThat(warning.type()).isEqualTo("Formatting");
        assertThat(warning.level()).isEqualTo("WARNING");
        assertThat(warning.message()).isEqualTo("Double columns might scramble text.");
        assertThat(warning.snippet()).isEqualTo("Java Developer");

        assertThat(result.recommendations()).hasSize(1);
        assertThat(result.recommendations().get(0)).isEqualTo("Use a single column layout.");

        verify(profileRepository).findTopByUserIdOrderByCreatedAtDesc(USER_ID);
        verify(candidateProfileMapper).toDto(profile);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldReturnEmptyResultWhenNoCvAvailable() {
        // GIVEN
        when(profileRepository.findTopByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(new CandidateProfile()));

        // WHEN
        AtsSimulationDto result = cvIngestionService.simulateAtsParsing(USER_ID, "fr");

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.rawText()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).level()).isEqualTo("CRITICAL");
        assertThat(result.recommendations()).hasSize(1);
    }
}
