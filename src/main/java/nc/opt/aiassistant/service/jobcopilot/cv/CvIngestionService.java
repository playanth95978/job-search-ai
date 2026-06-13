package nc.opt.aiassistant.service.jobcopilot.cv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.CandidateProfileDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CvReviewDto;
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

    public CvIngestionService(
        MistralOCRProcessService ocrService,
        VectorPDFService vectorPDFService,
        CandidateProfileRepository profileRepository,
        @Qualifier("mistralAiChatModel") ChatModel chatModel,
        ObjectMapper objectMapper
    ) {
        this.ocrService = ocrService;
        this.vectorPDFService = vectorPDFService;
        this.profileRepository = profileRepository;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    /** Ingests a CV file: OCR → markdown → chunks → embeddings → profile extraction. */
    public CandidateProfile ingest(MultipartFile file, String userId) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("cv");
        log.info("CV ingestion start: user={}, file={}", userId, filename);

        String markdown = extractMarkdown(file);
        List<Document> chunks = chunkMarkdown(markdown);
        indexChunks(chunks, userId, filename);
        CandidateProfileDto extracted = extractProfile(markdown);

        return saveProfile(extracted, markdown, filename, userId);
    }

    /**
     * Runs an LLM review of the user's stored CV and returns a qualitative assessment
     * (overall score, summary, strengths and actionable improvements).
     */
    public CvReviewDto review(String userId) {
        CandidateProfile profile = profileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("No CV profile found for user " + userId));

        String markdown = profile.getRawMarkdown();
        if (markdown == null || markdown.isBlank()) {
            return new CvReviewDto(0, "No CV content available to review.", List.of(), List.of("Upload a CV first."));
        }

        String prompt =
            """
            You are an expert technical recruiter and ATS specialist. Review the following CV and give concise, \
            actionable feedback to help the candidate improve it. Be specific and reference what is (or is not) in the CV.
            Return ONLY valid JSON matching this exact structure:
            {
              "score": number between 0 and 100,
              "summary": "one or two sentence overall assessment",
              "strengths": ["short bullet", "..."],
              "improvements": ["short actionable suggestion, e.g. 'quantify impact with metrics'", "..."]
            }

            CV content:
            """ + markdown;

        String json = cleanJsonResponse(chatClient.prompt().user(prompt).call().content());

        try {
            return objectMapper.readValue(json, CvReviewDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse CV review: {}", e.getMessage());
            return new CvReviewDto(0, "The AI review could not be generated. Please try again.", List.of(), List.of());
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

    CandidateProfileDto extractProfile(String markdown) {
        String prompt =
            """
            Extract the candidate profile from this CV in JSON format.
            Return ONLY valid JSON matching this structure:
            {
              "fullName": "string",
              "email": "string or null",
              "location": "string or null",
              "yearsOfExperience": number or null,
              "skills": ["string"],
              "experiences": [{"company":"string","role":"string","startDate":"string","endDate":"string or null","description":"string","technologies":["string"]}],
              "preferredRoles": ["string"],
              "languages": ["string"],
              "education": ["string"],
              "certifications": ["string"]
            }

            CV content:
            """ + markdown;

        String json = chatClient.prompt().user(prompt).call().content();
        json = cleanJsonResponse(json);

        try {
            return objectMapper.readValue(json, CandidateProfileDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse profile extraction: {}", e.getMessage());
            return new CandidateProfileDto(null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        raw = raw.strip();
        if (raw.startsWith("```json")) raw = raw.substring(7);
        if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        return raw.strip();
    }

    CandidateProfile saveProfile(CandidateProfileDto dto, String markdown, String filename, String userId) {
        CandidateProfile profile = profileRepository.findByUserId(userId).orElse(new CandidateProfile());
        profile.setUserId(userId);
        profile.setFullName(dto.fullName());
        profile.setEmail(dto.email());
        profile.setLocation(dto.location());
        profile.setYearsOfExperience(dto.yearsOfExperience());
        profile.setRawMarkdown(markdown);
        profile.setCvFilename(filename);

        try {
            profile.setSkills(objectMapper.writeValueAsString(dto.skills()));
            profile.setExperiences(objectMapper.writeValueAsString(dto.experiences()));
            profile.setPreferredRoles(objectMapper.writeValueAsString(dto.preferredRoles()));
            profile.setLanguages(objectMapper.writeValueAsString(dto.languages()));
            profile.setEducation(objectMapper.writeValueAsString(dto.education()));
            profile.setCertifications(objectMapper.writeValueAsString(dto.certifications()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize profile fields: {}", e.getMessage());
        }

        return profileRepository.save(profile);
    }
}
