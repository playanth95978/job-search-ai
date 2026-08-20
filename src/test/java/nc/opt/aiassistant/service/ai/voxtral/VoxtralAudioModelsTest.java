package nc.opt.aiassistant.service.ai.voxtral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nc.opt.aiassistant.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Covers the contract the Spring AI interfaces impose on our Voxtral implementations: the configured
 * defaults are applied, per-call options override them field by field, and the degenerate responses a
 * provider can return do not leak out as nulls.
 */
@ExtendWith(MockitoExtension.class)
class VoxtralAudioModelsTest {

    private static final Resource AUDIO = new ByteArrayResource("fake-audio".getBytes());

    @Mock
    private VoxtralAudioApi api;

    private ApplicationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        ApplicationProperties.Ai.Voxtral config = properties.getAi().getVoxtral();
        config.setEnabled(true);
        config.setVoice("recruiter-voice");
    }

    // --- transcription -------------------------------------------------------------------------

    @Test
    void transcriptionUsesTheConfiguredModelByDefault() {
        var model = new VoxtralTranscriptionModel(api, properties);
        when(api.transcribe(any(), eq("voxtral-mini-latest"), isNull(), isNull())).thenReturn(
            new VoxtralAudioApi.TranscriptionResult("voxtral-mini-latest", "bonjour", "fr", null, null)
        );

        var response = model.call(new AudioTranscriptionPrompt(AUDIO));

        assertThat(response.getResult().getOutput()).isEqualTo("bonjour");
    }

    @Test
    void transcriptionPassesTheLanguageHintThrough() {
        var model = new VoxtralTranscriptionModel(api, properties);
        var options = VoxtralTranscriptionOptions.builder().language("fr").temperature(0.2).build();
        when(api.transcribe(any(), eq("voxtral-mini-latest"), eq("fr"), eq(0.2))).thenReturn(
            new VoxtralAudioApi.TranscriptionResult("voxtral-mini-latest", "salut", "fr", null, null)
        );

        assertThat(model.call(new AudioTranscriptionPrompt(AUDIO, options)).getResult().getOutput()).isEqualTo("salut");
    }

    @Test
    void aSilentRecordingTranscribesToEmptyRatherThanNull() {
        var model = new VoxtralTranscriptionModel(api, properties);
        when(api.transcribe(any(), any(), isNull(), isNull())).thenReturn(
            new VoxtralAudioApi.TranscriptionResult("voxtral-mini-latest", null, null, null, null)
        );

        assertThat(model.call(new AudioTranscriptionPrompt(AUDIO)).getResult().getOutput()).isEmpty();
    }

    // --- text to speech ------------------------------------------------------------------------

    @Test
    void speechUsesTheConfiguredModelVoiceAndFormat() {
        var model = new VoxtralTextToSpeechModel(api, properties);
        when(api.speech(eq("Bonjour"), eq("voxtral-mini-tts-2603"), eq("recruiter-voice"), eq("mp3"), isNull())).thenReturn(new byte[] {
            1,
            2,
            3,
        });

        var response = model.call(new TextToSpeechPrompt("Bonjour"));

        assertThat(response.getResult().getOutput()).containsExactly(1, 2, 3);
    }

    @Test
    void perCallOptionsOverrideTheDefaultsFieldByField() {
        var model = new VoxtralTextToSpeechModel(api, properties);
        var options = VoxtralTextToSpeechOptions.builder().voice("other-voice").speed(1.2).build();
        when(api.speech(any(), any(), any(), any(), any())).thenReturn(new byte[] { 9 });

        model.call(new TextToSpeechPrompt("Bonjour", options));

        // Voice and speed come from the call; model and format still fall back to the configuration.
        verify(api).speech("Bonjour", "voxtral-mini-tts-2603", "other-voice", "mp3", 1.2);
    }

    @Test
    void aMissingVoiceIsRejectedBeforeCallingTheProvider() {
        properties.getAi().getVoxtral().setVoice(null);
        var model = new VoxtralTextToSpeechModel(api, properties);

        assertThatThrownBy(() -> model.call(new TextToSpeechPrompt("Bonjour")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("voice");
    }

    @Test
    void emptyTextIsRejected() {
        var model = new VoxtralTextToSpeechModel(api, properties);

        assertThatThrownBy(() -> model.call(new TextToSpeechPrompt("  "))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streamAnswersTheWholeReplyAsOneChunkInBatchMode() {
        var model = new VoxtralTextToSpeechModel(api, properties);
        when(api.speech(any(), any(), any(), any(), any())).thenReturn(new byte[] { 7 });

        var chunks = model.stream(new TextToSpeechPrompt("Bonjour")).collectList().block();

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getResult().getOutput()).containsExactly(7);
    }
}
