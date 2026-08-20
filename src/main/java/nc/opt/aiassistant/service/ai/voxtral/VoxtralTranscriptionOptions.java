package nc.opt.aiassistant.service.ai.voxtral;

import lombok.Builder;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;

/**
 * Voxtral-specific transcription options.
 *
 * <p>Spring AI's pattern for provider extras: the neutral {@link AudioTranscriptionOptions} contract is
 * just {@code getModel()}, and everything a provider adds — here the language hint and temperature —
 * hangs off its own options class, which callers use only when they need it.
 *
 * @param language ISO code ("fr") passed to boost accuracy; null lets Voxtral detect it.
 */
@Builder
public record VoxtralTranscriptionOptions(String model, String language, Double temperature) implements AudioTranscriptionOptions {
    @Override
    public String getModel() {
        return model;
    }
}
