package nc.opt.aiassistant.service.ai.voxtral;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Thin HTTP client over Mistral's audio endpoints — the only place in the codebase that knows Voxtral's
 * wire format. The Spring AI model implementations sit on top and expose nothing of it, so a change in
 * Mistral's payloads stops here.
 *
 * <p>Kept hand-written because Spring AI ships no Mistral audio module: {@code spring-ai-mistral-ai}
 * contains chat, embedding and moderation only, and the vendor-neutral {@code TranscriptionModel} /
 * {@code TextToSpeechModel} interfaces are implemented by OpenAI and Azure OpenAI. Writing the provider
 * behind those interfaces is the extension point Spring documents, not a workaround.
 */
@Slf4j
@Component
public class VoxtralAudioApi {

    private final RestClient restClient;
    private final ApplicationProperties.Ai.Voxtral config;

    public VoxtralAudioApi(RestClient.Builder builder, ApplicationProperties properties, VoxtralApiKey apiKey) {
        this.config = properties.getAi().getVoxtral();
        HttpClientSettings settings = HttpClientSettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(10))
            .withReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        this.restClient = builder
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .baseUrl(config.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.value())
            .build();
    }

    /**
     * {@code POST /v1/audio/transcriptions}, multipart: the audio arrives as an upload from the browser,
     * so streaming the part avoids buffering the whole recording a second time.
     */
    public TranscriptionResult transcribe(Resource audio, String model, String language, Double temperature) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", audio);
        parts.add("model", model);
        if (language != null && !language.isBlank()) {
            parts.add("language", language);
        }
        if (temperature != null) {
            parts.add("temperature", temperature);
        }

        return restClient
            .post()
            .uri("/v1/audio/transcriptions")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .body(TranscriptionResult.class);
    }

    /**
     * {@code POST /v1/audio/speech}, returning the encoded audio bytes.
     *
     * @param voice a preset Voxtral voice or a cloned profile id; one is required, there is no default.
     */
    public byte[] speech(String text, String model, String voice, String format, Double speed) {
        SpeechRequest request = new SpeechRequest(model, text, voice, format, speed);
        return restClient
            .post()
            .uri("/v1/audio/speech")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(byte[].class);
    }

    /** Subset of Voxtral's transcription response the app uses; unknown fields are ignored by the mapper. */
    public record TranscriptionResult(String model, String text, String language, List<Segment> segments, Usage usage) {
        public record Segment(String text, Double start, Double end) {}

        public record Usage(
            @JsonProperty("prompt_audio_seconds") Double promptAudioSeconds,
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens
        ) {}
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpeechRequest(
        String model,
        String input,
        @JsonProperty("voice_id") String voiceId,
        @JsonProperty("response_format") String responseFormat,
        Double speed
    ) {}
}
