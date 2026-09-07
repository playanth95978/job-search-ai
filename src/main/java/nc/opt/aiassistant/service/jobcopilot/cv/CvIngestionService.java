package nc.opt.aiassistant.service.jobcopilot.cv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.CandidateProfileEmbeddingService;
import nc.opt.aiassistant.service.jobcopilot.OfferCacheInvalidationService;
import nc.opt.aiassistant.service.jobcopilot.dto.AtsSimulationDto;
import nc.opt.aiassistant.service.jobcopilot.dto.AtsWarningDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CandidateProfileDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CvReviewDto;
import nc.opt.aiassistant.service.jobcopilot.dto.ExtractedProfileDto;
import nc.opt.aiassistant.service.mapper.CandidateProfileMapper;
import nc.opt.aiassistant.service.pdf.MistralOCRProcessService;
import nc.opt.aiassistant.service.pdf.VectorPDFService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class CvIngestionService {

    private final MistralOCRProcessService ocrService;
    private final VectorPDFService vectorPDFService;
    private final CandidateProfileRepository profileRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final CandidateProfileMapper candidateProfileMapper;
    private final OfferCacheInvalidationService offerCacheInvalidationService;
    private final CandidateProfileEmbeddingService profileEmbeddingService;

    public CvIngestionService(
        MistralOCRProcessService ocrService,
        VectorPDFService vectorPDFService,
        CandidateProfileRepository profileRepository,
        @Qualifier("mistralAiChatModel") ChatModel chatModel,
        ObjectMapper objectMapper,
        CandidateProfileMapper candidateProfileMapper,
        OfferCacheInvalidationService offerCacheInvalidationService,
        CandidateProfileEmbeddingService profileEmbeddingService
    ) {
        this.ocrService = ocrService;
        this.vectorPDFService = vectorPDFService;
        this.profileRepository = profileRepository;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
        this.candidateProfileMapper = candidateProfileMapper;
        this.offerCacheInvalidationService = offerCacheInvalidationService;
        this.profileEmbeddingService = profileEmbeddingService;
    }

    /**
     * Ingests a CV file: OCR → markdown → chunks → embeddings → profile extraction.
     */
    public CandidateProfile ingest(MultipartFile file, String userId) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("cv");
        log.info("CV ingestion start: user={}, file={}", userId, filename);

        String markdown = extractMarkdown(file);
        List<Document> chunks = chunkMarkdown(markdown);
        indexChunks(chunks, userId, filename);
        ExtractedProfileDto extracted = extractProfile(markdown);

        return saveProfile(extracted, markdown, filename, userId);
    }

    /**
     * Runs an LLM review of the user's stored CV and returns a qualitative assessment
     * (overall score, summary, strengths and actionable improvements).
     */
    public CvReviewDto review(String userId, String language) {
        CandidateProfile profile = profileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new IllegalStateException("No CV profile found for user " + userId));

        String markdown = profile.getRawMarkdown();
        if (markdown == null || markdown.isBlank()) {
            return new CvReviewDto(0, "No CV content available to review.", List.of(), List.of("Upload a CV first."));
        }

        String lang = (language == null || language.isBlank()) ? "en" : language.trim();
        String prompt =
            """
            You are an expert technical recruiter and ATS specialist. Review the following CV and give concise, \
            actionable feedback to help the candidate improve it. Be specific and reference what is (or is not) in the CV.
            Write ALL text fields (summary, strengths, improvements) in the language with ISO 639-1 code "%s".
            Return ONLY valid JSON matching this exact structure:
            {
              "score": number between 0 and 100,
              "summary": "one or two sentence overall assessment",
              "strengths": ["short bullet", "..."],
              "improvements": ["short actionable suggestion, e.g. 'quantify impact with metrics'", "..."]
            }

            CV content:
            """.formatted(lang) + markdown;

        String json = cleanJsonResponse(chatClient.prompt().user(prompt).call().content());

        try {
            return objectMapper.readValue(json, CvReviewDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse CV review: {}", e.getMessage());
            return new CvReviewDto(0, "The AI review could not be generated. Please try again.", List.of(), List.of());
        }
    }

    /**
     * Simulates ATS parsing of the stored CV: compares raw text (what the parser saw)
     * with the structured CandidateProfile, highlights issues and provides recommendations.
     */
    public AtsSimulationDto simulateAtsParsing(String userId, String language) {
        CandidateProfile profile = profileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new IllegalStateException("No CV profile found for user " + userId));

        String markdown = profile.getRawMarkdown();
        if (markdown == null || markdown.isBlank()) {
            return new AtsSimulationDto(
                "",
                List.of(new AtsWarningDto("Content", "CRITICAL", "No CV content available. Please upload a CV first.", null)),
                List.of("Upload your CV to start the ATS parsing simulation.")
            );
        }

        // Serialize structured profile to JSON so the LLM can compare what was extracted.
        // rawMarkdown is dropped: it is already sent verbatim as the RAW TEXT block, and leaving it in
        // would duplicate the whole CV inside the "structured" block the model is asked to compare against.
        String structuredJson;
        try {
            ObjectNode structured = objectMapper.valueToTree(candidateProfileMapper.toDto(profile));
            structured.remove("rawMarkdown");
            structuredJson = objectMapper.writeValueAsString(structured);
        } catch (Exception e) {
            log.error("Failed to serialize candidate profile for ATS simulation: {}", e.getMessage());
            structuredJson = "{}";
        }

        String lang = (language == null || language.isBlank()) ? "fr" : language.trim();
        String prompt = """
            You are an expert ATS (Applicant Tracking System) parser simulator.
            We have extracted raw text from a candidate's CV (using OCR or standard text extraction), and then we extracted a structured profile JSON from it.

            Compare the raw text with the structured JSON to detect layout, scanning, parsing, or extraction problems that a standard ATS parser would encounter.
            Identify issues such as:
            - Scrambled layout: multi-column text read in the wrong order or headers/footers merged into body text.
            - Experience dates/duration issues: dates placed in columns that make it look like the candidate has much less experience than actual (e.g. 1 year instead of 6 years).
            - Unextractable contact details: emails, phone numbers, or social links hidden in images or complex graphic shapes.
            - Unreadable skills/technologies: important technical skills present in the text but missing from the structured JSON due to bad layout, images, or poor formatting.
            - Unrecognized standard section headings.

            Write all fields ("message", "recommendations") in the language with ISO 639-1 code "%s".
            Return ONLY valid JSON matching this exact structure (do not include markdown wrapping or any other text):
            {
              "warnings": [
                {
                  "type": "Formatting | Content | Dates | Contact | Skills",
                  "level": "CRITICAL | WARNING | INFO",
                  "message": "Specific explanation of the layout, formatting or parsing warning",
                  "snippet": "Raw snippet of text where the issue is located, if applicable, or null"
                }
              ],
              "recommendations": [
                "Concrete, actionable advice to restructure the CV (e.g. 'Use a single-column layout', 'Ensure dates are placed next to job titles')"
              ]
            }

            RAW EXTRACTED CV TEXT:
            ===
            %s
            ===

            EXTRACTED STRUCTURED PROFILE JSON:
            ===
            %s
            ===
            """.formatted(lang, markdown, structuredJson);

        String json = cleanJsonResponse(chatClient.prompt().user(prompt).call().content());

        try {
            AtsSimulationDto response = objectMapper.readValue(json, AtsSimulationDto.class);
            return new AtsSimulationDto(markdown, response.warnings(), response.recommendations());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ATS simulation response: {}", e.getMessage());
            log.debug("Raw JSON was: {}", json);
            return new AtsSimulationDto(
                markdown,
                List.of(new AtsWarningDto("Formatting", "WARNING", "Failed to parse the detailed ATS simulation. Please try again.", null)),
                List.of("Try re-running the ATS simulation.")
            );
        }
    }

    String extractMarkdown(MultipartFile file) throws IOException {
        String contentType = Optional.ofNullable(file.getContentType()).orElse("");

        if (contentType.contains("pdf")) {
            try {
                var ocrResult = ocrService.process(file);
                if (ocrResult.rawText() != null && !ocrResult.rawText().isBlank()) {
                    return ocrResult.rawText();
                }
            } catch (Exception e) {
                log.warn("Mistral OCR failed, falling back to Tika: {}", e.getMessage());
            }
        }

        return tikaExtract(file);
    }

    private String tikaExtract(MultipartFile file) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes());
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> docs = reader.get();
        StringBuilder sb = new StringBuilder();
        docs.forEach(d -> sb.append(d.getText()).append("\n"));
        return sb.toString();
    }

    List<Document> chunkMarkdown(String markdown) {
        ByteArrayResource resource = new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8));
        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig());
        return reader.get();
    }

    void indexChunks(List<Document> chunks, String userId, String filename) {
        for (Document doc : chunks) {
            doc.getMetadata().put("user_id", userId);
            doc.getMetadata().put("filename", filename);
            doc.getMetadata().put("type", "cv");
        }
        vectorPDFService.put(chunks);
        log.info("Indexed {} CV chunks for user={}", chunks.size(), userId);
    }

    /**
     * Turns the raw CV text into a structured profile. Uses Spring AI's {@code entity(...)} (schema-guided
     * output + deserialization) rather than a hand-written JSON contract, as everywhere else in the app.
     *
     * @throws CvExtractionException when the model fails or returns nothing usable — better a failed upload
     *     than a profile silently saved empty.
     */
    ExtractedProfileDto extractProfile(String markdown) {
        String prompt =
            """
            Extract the candidate profile from this CV.
            Use the exact wording of the CV; never invent skills, employers or dates that are not written in it.
            Rules for the fields:
            - every "id" is a lowercase slug derived from the item name (e.g. "spring-boot");
            - a skill "level" is an integer from 1 (basic) to 5 (expert), inferred from how the CV presents it;
            - dates stay as written in the CV (e.g. "2025-04"); "endDate" is null and "current" is true for the ongoing role;
            - use null for anything the CV does not state, and an empty list for a section it does not have.

            CV content:
            """ + markdown;

        ExtractedProfileDto extracted;
        try {
            extracted = chatClient.prompt().user(prompt).call().entity(ExtractedProfileDto.class);
        } catch (RuntimeException e) {
            log.error("CV profile extraction failed: {}", e.getMessage());
            throw new CvExtractionException("The AI could not read this CV. Please try again in a moment.", e);
        }

        if (extracted == null || extracted.isEmpty()) {
            log.error("CV profile extraction returned an empty profile ({} characters of CV text)", markdown.length());
            throw new CvExtractionException(
                "No profile could be extracted from this CV. Check that the file contains selectable text, then try again.",
                null
            );
        }
        return extracted;
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        raw = raw.strip();
        if (raw.startsWith("```json")) raw = raw.substring(7);
        if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        return raw.strip();
    }

    CandidateProfile saveProfile(ExtractedProfileDto extracted, String markdown, String filename, String userId) {
        CandidateProfile profile = candidateProfileMapper.toEntity(toProfileDto(extracted));
        profile.setUserId(userId);
        profile.setRawMarkdown(markdown);
        profile.setCvFilename(filename);
        profileRepository.findByUserId(userId).ifPresent(existing -> {
            profile.setId(existing.getId());
        });
        CandidateProfile saved = profileRepository.save(profile);
        // The CV changed → drop any per-offer AI results computed from the previous CV.
        offerCacheInvalidationService.evictAll(userId);
        // Recompute the profile embedding off-thread (powers autopsy percentile + opportunity radar).
        profileEmbeddingService.embedAsync(saved.getId());
        return saved;
    }

    /** Widens the extraction result into the persisted profile shape, tolerating null lists from the model. */
    private CandidateProfileDto toProfileDto(ExtractedProfileDto e) {
        return new CandidateProfileDto(
            null,
            null,
            e.fullName(),
            e.email(),
            e.location(),
            e.yearsOfExperience(),
            orEmpty(e.skills()),
            orEmpty(e.experiences()),
            orEmpty(e.preferredRoles()),
            orEmpty(e.languages()),
            orEmpty(e.education()),
            orEmpty(e.certifications()),
            null,
            null,
            null,
            null
        );
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
