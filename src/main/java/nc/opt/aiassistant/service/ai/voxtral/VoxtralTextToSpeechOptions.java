package nc.opt.aiassistant.service.ai.voxtral;

import lombok.Builder;
import org.springframework.ai.audio.tts.TextToSpeechOptions;

/**
 * Voxtral-specific speech options. Implements the neutral {@link TextToSpeechOptions} contract
 * (model / voice / format / speed) so a caller holding the interface never needs this class; the
 * concrete type exists for the defaults and for anything Voxtral adds later.
 */
@Builder
public record VoxtralTextToSpeechOptions(String model, String voice, String format, Double speed) implements TextToSpeechOptions {
    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getVoice() {
        return voice;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public Double getSpeed() {
        return speed;
    }
}
