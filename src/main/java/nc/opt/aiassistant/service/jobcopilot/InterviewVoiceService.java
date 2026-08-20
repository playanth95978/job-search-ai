package nc.opt.aiassistant.service.jobcopilot;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.ai.voxtral.VoxtralTextToSpeechOptions;
import nc.opt.aiassistant.service.ai.voxtral.VoxtralTranscriptionOptions;
import nc.opt.aiassistant.service.jobcopilot.dto.InterviewVoiceTurnDto;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Voice layer around {@link InterviewCoachService}: the candidate speaks, the recruiter answers out loud.
 *
 * <p>The roleplay itself is untouched — it still reasons over text. This class only converts at the
 * edges, and it does so through Spring AI's neutral {@code TranscriptionModel} / {@code TextToSpeechModel}
 * interfaces, so which provider does the work is a wiring decision (today Voxtral, see
 * {@code nc.opt.aiassistant.service.ai.voxtral}).
 *
 * <p>Both models are optional beans: with voice disabled the endpoints answer a clear error instead of
 * the app failing to start.
 */
@Slf4j
@Service
public class InterviewVoiceService {

    /** The interview language arrives as a display name ("French"); Voxtral wants an ISO code. */
    private static final Map<String, String> LANGUAGE_CODES = Map.of(
        "french",
        "fr",
        "français",
        "fr",
        "english",
        "en",
        "anglais",
        "en",
        "spanish",
        "es",
        "espagnol",
        "es",
        "german",
        "de",
        "allemand",
        "de"
    );

    private final Optional<TranscriptionModel> transcriptionModel;
    private final Optional<TextToSpeechModel> textToSpeechModel;
    private final InterviewCoachService interviewCoachService;
    private final ApplicationProperties.Ai.Voxtral config;

    public InterviewVoiceService(
        Optional<TranscriptionModel> transcriptionModel,
        Optional<TextToSpeechModel> textToSpeechModel,
        InterviewCoachService interviewCoachService,
        ApplicationProperties properties
    ) {
        this.transcriptionModel = transcriptionModel;
        this.textToSpeechModel = textToSpeechModel;
        this.interviewCoachService = interviewCoachService;
        this.config = properties.getAi().getVoxtral();
    }

    public boolean isAvailable() {
        return config.isEnabled() && transcriptionModel.isPresent() && textToSpeechModel.isPresent();
    }

    /** Opens a session and returns the recruiter's first question, spoken. */
    public InterviewVoiceTurnDto start(CandidateProfile profile, JobOffer offer, String conversationId, String language) {
        requireAvailable();
        String reply = interviewCoachService.startSession(profile, offer, conversationId, language);
        return speak(conversationId, "", reply, language);
    }

    /**
     * One spoken turn: transcribe the candidate's answer, run it through the roleplay, speak the reply.
     *
     * <p>A recording that transcribes to nothing short-circuits: sending an empty answer to the recruiter
     * would earn a confused reply, when the useful outcome is telling the candidate they were not heard.
     */
    public InterviewVoiceTurnDto turn(
        CandidateProfile profile,
        JobOffer offer,
        String conversationId,
        MultipartFile audio,
        String language
    ) {
        requireAvailable();
        String transcript = transcribe(audio, language);
        if (transcript.isBlank()) {
            return new InterviewVoiceTurnDto(conversationId, "", null, null, null);
        }
        String reply = interviewCoachService.respond(profile, offer, conversationId, transcript, language);
        return speak(conversationId, transcript, reply, language);
    }

    private String transcribe(MultipartFile audio, String language) {
        if (audio == null || audio.isEmpty()) {
            throw new BadRequestAlertException("No audio provided", "interviewVoice", "noAudio");
        }
        if (audio.getSize() > config.getMaxUploadBytes()) {
            throw new BadRequestAlertException("Recording too large", "interviewVoice", "audioTooLarge");
        }

        Resource resource = toResource(audio);
        var options = VoxtralTranscriptionOptions.builder().language(languageCode(language)).build();
        var response = transcriptionModel.orElseThrow().call(new AudioTranscriptionPrompt(resource, options));
        return response.getResult().getOutput().trim();
    }

    private InterviewVoiceTurnDto speak(String conversationId, String transcript, String reply, String language) {
        var options = VoxtralTextToSpeechOptions.builder().build();
        var response = textToSpeechModel.orElseThrow().call(new TextToSpeechPrompt(reply, options));
        byte[] audio = response.getResult().getOutput();
        log.debug("Interview voice turn for conversation {} ({} language)", conversationId, language);
        return new InterviewVoiceTurnDto(
            conversationId,
            transcript,
            reply,
            Base64.getEncoder().encodeToString(audio),
            config.getResponseFormat()
        );
    }

    /**
     * Wraps the upload with its filename kept: Voxtral infers the container (webm, mp4, wav) from the
     * multipart part name, and a nameless part is rejected as an unsupported format.
     */
    private Resource toResource(MultipartFile audio) {
        String filename = audio.getOriginalFilename() == null ? "answer.webm" : audio.getOriginalFilename();
        try {
            return new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException e) {
            throw new BadRequestAlertException("Unreadable audio upload", "interviewVoice", "audioUnreadable");
        }
    }

    private String languageCode(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return LANGUAGE_CODES.get(language.trim().toLowerCase(Locale.ROOT));
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new BadRequestAlertException("Voice interview is disabled", "interviewVoice", "disabled");
        }
    }
}
