package nc.opt.aiassistant.service.ai.voxtral;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Speech-to-text through Voxtral, behind Spring AI's vendor-neutral {@link TranscriptionModel}.
 *
 * <p>Callers depend on the interface, never on this class — which is the point: swapping Voxtral for
 * Whisper means changing a bean, not the interview code. The bean only exists when the feature is on,
 * so an app with voice disabled carries no Mistral audio wiring at all.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "application.ai.voxtral", name = "enabled", havingValue = "true")
public class VoxtralTranscriptionModel implements TranscriptionModel {

    private final VoxtralAudioApi api;
    private final VoxtralTranscriptionOptions defaultOptions;

    public VoxtralTranscriptionModel(VoxtralAudioApi api, ApplicationProperties properties) {
        this.api = api;
        var config = properties.getAi().getVoxtral();
        this.defaultOptions = VoxtralTranscriptionOptions.builder().model(config.getTranscriptionModel()).build();
    }

    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
        Assert.notNull(prompt, "prompt must not be null");
        Assert.notNull(prompt.getInstructions(), "audio resource must not be null");

        VoxtralTranscriptionOptions options = merge(prompt.getOptions());
        var result = api.transcribe(prompt.getInstructions(), options.model(), options.language(), options.temperature());

        // A transcription with no text is not an error to propagate as null: callers treat "" as
        // "nothing was said", which is exactly what a silent recording means.
        String text = result == null || result.text() == null ? "" : result.text();
        log.debug("Voxtral transcribed {} chars (language={})", text.length(), result == null ? null : result.language());
        return new AudioTranscriptionResponse(new AudioTranscription(text));
    }

    /** Per-call options win over the configured defaults, field by field. */
    private VoxtralTranscriptionOptions merge(AudioTranscriptionOptions options) {
        if (options == null) {
            return defaultOptions;
        }
        if (options instanceof VoxtralTranscriptionOptions voxtral) {
            return VoxtralTranscriptionOptions.builder()
                .model(voxtral.model() == null ? defaultOptions.model() : voxtral.model())
                .language(voxtral.language())
                .temperature(voxtral.temperature())
                .build();
        }
        // Another provider's options object: honour the one field the neutral contract guarantees.
        return VoxtralTranscriptionOptions.builder()
            .model(options.getModel() == null ? defaultOptions.model() : options.getModel())
            .build();
    }
}
