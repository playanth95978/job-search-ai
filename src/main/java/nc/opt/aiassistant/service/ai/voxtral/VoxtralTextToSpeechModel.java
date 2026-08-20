package nc.opt.aiassistant.service.ai.voxtral;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

/**
 * Text-to-speech through Voxtral TTS, behind Spring AI's vendor-neutral {@link TextToSpeechModel}.
 *
 * <p>{@code TextToSpeechModel} extends {@code StreamingTextToSpeechModel}, so {@link #stream} has to be
 * implemented. It is answered as a single element here on purpose: this is the batch turn-by-turn mode —
 * the whole reply is synthesised, then played. Real chunked streaming needs Voxtral's realtime API and a
 * WebSocket carrying pcm frames, and it belongs in a separate implementation of the same interface
 * rather than a half-streaming one pretending otherwise.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "application.ai.voxtral", name = "enabled", havingValue = "true")
public class VoxtralTextToSpeechModel implements TextToSpeechModel {

    private final VoxtralAudioApi api;
    private final VoxtralTextToSpeechOptions defaultOptions;

    public VoxtralTextToSpeechModel(VoxtralAudioApi api, ApplicationProperties properties) {
        this.api = api;
        var config = properties.getAi().getVoxtral();
        this.defaultOptions = VoxtralTextToSpeechOptions.builder()
            .model(config.getTtsModel())
            .voice(config.getVoice())
            .format(config.getResponseFormat())
            .build();
    }

    @Override
    public @NonNull TextToSpeechResponse call(@NonNull TextToSpeechPrompt prompt) {
        Assert.notNull(prompt, "prompt must not be null");
        String text = prompt.getInstructions().getText();
        Assert.hasText(text, "text to synthesise must not be empty");

        VoxtralTextToSpeechOptions options = merge(prompt.getOptions());
        Assert.hasText(options.voice(), "a Voxtral voice id is required (application.ai.voxtral.voice)");

        byte[] audio = api.speech(text, options.model(), options.voice(), options.format(), options.speed());
        log.debug("Voxtral synthesised {} bytes of {} for {} chars", audio == null ? 0 : audio.length, options.format(), text.length());
        return new TextToSpeechResponse(List.of(new Speech(audio == null ? new byte[0] : audio)));
    }

    @Override
    public @NonNull Flux<TextToSpeechResponse> stream(@NonNull TextToSpeechPrompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public @NonNull TextToSpeechOptions getOptions() {
        return defaultOptions;
    }

    /** Per-call options win over the configured defaults, field by field. */
    private VoxtralTextToSpeechOptions merge(TextToSpeechOptions options) {
        if (options == null) {
            return defaultOptions;
        }
        return VoxtralTextToSpeechOptions.builder()
            .model(options.getModel() == null ? defaultOptions.model() : options.getModel())
            .voice(options.getVoice() == null ? defaultOptions.voice() : options.getVoice())
            .format(options.getFormat() == null ? defaultOptions.format() : options.getFormat())
            .speed(options.getSpeed())
            .build();
    }
}
