package nc.opt.aiassistant.service.jobcopilot.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
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
            offerCacheInvalidationService,
            null
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

    @Test
    void testIsPalindrome() {
        assertThat(isPalindrome(121)).isTrue();
        assertThat(isPalindrome(-121)).isFalse();
        assertThat(isPalindrome(10)).isFalse();
        assertThat(isPalindrome(0)).isTrue();
    }

    public boolean isPalindrome(int x) {
        if (x < 0) return false;
        String stringValue = String.valueOf(x);
        int n = stringValue.length();
        for (int i = 0; i < n / 2; i++) {
            if (stringValue.charAt(i) != stringValue.charAt(n - 1 - i)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void testRomanToInt() {
        assertThat(romanToInt("III")).isEqualTo(3);
        assertThat(romanToInt("LVIII")).isEqualTo(58);
        assertThat(romanToInt("MCMXCIV")).isEqualTo(1994);
    }

    public int romanToInt(String s) {
        Map<Character, Integer> map = new HashMap<>();
        map.put('I', 1);
        map.put('V', 5);
        map.put('X', 10);
        map.put('L', 50);
        map.put('C', 100);
        map.put('D', 500);
        map.put('M', 1000);

        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            int currentVal = map.get(s.charAt(i));
            if (i + 1 < s.length()) {
                int nextVal = map.get(s.charAt(i + 1));
                if (currentVal < nextVal) {
                    result -= currentVal;
                } else {
                    result += currentVal;
                }
            } else {
                result += currentVal;
            }
        }
        return result;
    }

    public int strStr(String haystack, String needle) {
        if (haystack.length() < needle.length()) {
            return -1;
        } else if (haystack.equals(needle)) {
            return 0;
        } else if (haystack.contains(needle)) {
            return haystack.indexOf(needle);
        }
        return -1;
    }

    public int lengthOfLastWord(String s) {
        String[] words = s.split(" ");
        return words[words.length - 1].length();
    }

    @Test
    void sort() {
        int[] nums1 = { 2, 0, 2, 1, 1, 0 };
        sortColors(nums1);
        assertArrayEquals(new int[] { 0, 0, 1, 1, 2, 2 }, nums1);

        int[] nums2 = { 2, 0, 1 };
        sortColors(nums2);
        assertArrayEquals(new int[] { 0, 1, 2 }, nums2);
    }

    public void sortColors(int[] nums) {
        int low = 0;
        int mid = 0;
        int high = nums.length - 1;

        while (mid <= high) {
            if (nums[mid] == 0) {
                swap(nums, low, mid);
                low++;
                mid++;
            } else if (nums[mid] == 1) {
                mid++;
            } else {
                swap(nums, mid, high);
                high--;
            }
        }
    }

    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }

    public String multiply(String num1, String num2) {
        return Integer.valueOf(new BigDecimal(num1).multiply(new BigDecimal(num2)).intValue()).toString();
    }
}
