package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.jobcopilot.dto.CvMatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.CvReviewDto;
import nc.opt.aiassistant.service.jobcopilot.dto.GenerateResumeRequestDto;
import nc.opt.aiassistant.service.jobcopilot.dto.GeneratedResumeDto;
import nc.opt.aiassistant.service.jobcopilot.dto.RewriteRequestDto;
import nc.opt.aiassistant.service.jobcopilot.dto.RewriteResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Rewrites a single CV free-text field into three styles (professional, concise, ATS-optimized)
 * in one LLM call — cheaper given the Mistral client rate limit.
 */
@Slf4j
@Service
public class CvRewriteService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public CvRewriteService(@Qualifier("mistralAiChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    /** Reviews the whole resume and returns a score + strengths + actionable improvements. */
    public CvReviewDto reviewResume(String content, String language) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return new CvReviewDto(0, "No resume content to review.", List.of(), List.of("Add content to your resume first."));
        }
        String languageInstruction = (language == null || language.isBlank())
            ? "Answer in the SAME LANGUAGE as the resume."
            : "Answer in " + language + ".";

        String prompt =
            """
            You are an expert technical recruiter and ATS specialist. Review the resume below and give
            concise, actionable feedback. Be specific and reference what is (or is not) present.
            %s
            Return ONLY valid JSON with exactly these keys:
            {
              "score": number between 0 and 100,
              "summary": "one or two sentence overall assessment",
              "strengths": ["short bullet", "..."],
              "improvements": ["short actionable suggestion", "..."]
            }

            Resume:
            """.formatted(languageInstruction) + text;

        String json = cleanJsonResponse(chatClient.prompt().user(prompt).call().content());
        try {
            return objectMapper.readValue(json, CvReviewDto.class);
        } catch (Exception e) {
            log.error("Failed to parse resume review: {}", e.getMessage());
            return new CvReviewDto(0, "The AI review could not be generated. Please try again.", List.of(), List.of());
        }
    }

    /** Matches the resume against a pasted job offer: scores + missing skills/keywords + suggestions. */
    public CvMatchResultDto matchAgainstOffer(String content, String offer, String language) {
        String cv = content == null ? "" : content.trim();
        String job = offer == null ? "" : offer.trim();
        if (cv.isEmpty() || job.isEmpty()) {
            return new CvMatchResultDto(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        String languageInstruction = (language == null || language.isBlank())
            ? "Answer in the SAME LANGUAGE as the resume."
            : "Answer in " + language + ".";
        String prompt = """
            You are an expert technical recruiter. Compare the RESUME against the JOB OFFER and assess the fit.
            %s Be specific and base everything on the two texts only.
            Return ONLY valid JSON with exactly these keys (all scores are integers 0-100):
            {
              "globalScore": number,
              "skillsScore": number,
              "experienceScore": number,
              "keywordScore": number,
              "missingSkills": ["skills required by the offer but absent from the resume"],
              "missingKeywords": ["important keywords from the offer missing in the resume"],
              "strengths": ["where the resume matches the offer well"],
              "weaknesses": ["gaps vs the offer"],
              "suggestions": ["concrete actions to better match this offer"]
            }

            ===== RESUME =====
            %s

            ===== JOB OFFER =====
            %s

            """.formatted(languageInstruction, cv, job);

        CvMatchResultDto cvMatchResultDto = chatClient.prompt().user(prompt).call().entity(CvMatchResultDto.class);
        try {
            return cvMatchResultDto;
        } catch (Exception e) {
            log.error("Failed to parse CV match result: {}", e.getMessage());
            return new CvMatchResultDto(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * Generates a complete, structured resume from free-form input (an old CV, LinkedIn export,
     * raw notes…), optionally tailored toward a target job offer. Returns a {@link GeneratedResumeDto}
     * ready to auto-fill the CV builder.
     */
    public GeneratedResumeDto generateFullResume(GenerateResumeRequestDto request) {
        String source = request.source() == null ? "" : request.source().trim();
        if (source.isEmpty()) {
            return emptyGeneratedResume();
        }
        String offer = request.offer() == null ? "" : request.offer().trim();
        String tailoring = offer.isEmpty()
            ? ""
            : """

              Tailor the resume toward the TARGET JOB OFFER below: surface the most relevant
              experience, skills and keywords, and phrase the summary for this role.
              Never invent experience the source does not support.

              ===== TARGET JOB OFFER =====
              %s
              """.formatted(offer);

        String languageInstruction = (request.language() == null || request.language().isBlank())
            ? "Use the SAME LANGUAGE as the source."
            : "Write the resume in " + request.language() + ".";

        String prompt = """
            You are an expert CV/resume writer and ATS specialist. From the SOURCE below, produce a
            complete, well-structured resume. %s Extract and organize
            every relevant fact; improve wording with strong action verbs, but DO NOT invent facts
            (employers, dates, diplomas, figures). Leave a field empty ("") when the source does not
            provide it. Write 3-5 concise, impact-oriented achievements per experience when possible.
            Skill "level" is an integer 1-5 (use 3 when unknown). Dates are short strings like "2021"
            or "Jan 2021"; set "current": true for an ongoing role and leave its endDate empty.
            %s
            Return ONLY valid JSON with exactly this shape (no markdown, no comments):
            {
              "personal": { "firstName": "", "lastName": "", "title": "", "email": "", "phone": "",
                            "location": "", "linkedin": "", "github": "", "website": "" },
              "summary": "",
              "experiences": [ { "jobTitle": "", "company": "", "startDate": "", "endDate": "",
                                 "current": false, "description": "", "achievements": [""] } ],
              "education": [ { "degree": "", "school": "", "startDate": "", "endDate": "", "description": "" } ],
              "projects": [ { "name": "", "description": "", "url": "", "technologies": [""] } ],
              "certifications": [ { "name": "", "issuer": "", "date": "" } ],
              "skills": [ { "name": "", "level": 3 } ],
              "languages": [ { "name": "", "level": "" } ]
            }

            ===== SOURCE =====
            %s
            """.formatted(languageInstruction, tailoring, source);

        try {
            GeneratedResumeDto result = chatClient.prompt().user(prompt).call().entity(GeneratedResumeDto.class);
            return result == null ? emptyGeneratedResume() : result;
        } catch (Exception e) {
            log.error("Failed to generate full resume: {}", e.getMessage());
            return emptyGeneratedResume();
        }
    }

    /**
     * Produces an ATS-optimized variant of an existing resume tailored to a specific job offer:
     * reorders skills/experiences by relevance, rewrites bullets with the offer's keywords, drops
     * off-topic content — without inventing any fact. Returns a {@link GeneratedResumeDto}.
     */
    public GeneratedResumeDto tailorResume(String resumeJson, String offerText) {
        String resume = resumeJson == null ? "" : resumeJson.trim();
        String offer = offerText == null ? "" : offerText.trim();
        if (resume.isEmpty() || offer.isEmpty()) {
            return emptyGeneratedResume();
        }

        String prompt = """
            You are an expert resume writer and ATS specialist. Rewrite the RESUME (given as JSON) into
            a variant TAILORED to the JOB OFFER below. Keep the SAME LANGUAGE as the resume.
            Rules:
            - Reorder skills and experiences so the most relevant to this offer come first.
            - Rephrase the summary and the experience bullets to surface the offer's keywords naturally.
            - Drop or downplay content irrelevant to this offer (but keep the work history truthful).
            - DO NOT invent facts (employers, dates, diplomas, skills the candidate doesn't have).
            - Skill "level" is an integer 1-5 (keep the original when known, else 3).
            Return ONLY valid JSON with exactly this shape (no markdown, no comments):
            {
              "personal": { "firstName": "", "lastName": "", "title": "", "email": "", "phone": "",
                            "location": "", "linkedin": "", "github": "", "website": "" },
              "summary": "",
              "experiences": [ { "jobTitle": "", "company": "", "startDate": "", "endDate": "",
                                 "current": false, "description": "", "achievements": [""] } ],
              "education": [ { "degree": "", "school": "", "startDate": "", "endDate": "", "description": "" } ],
              "projects": [ { "name": "", "description": "", "url": "", "technologies": [""] } ],
              "certifications": [ { "name": "", "issuer": "", "date": "" } ],
              "skills": [ { "name": "", "level": 3 } ],
              "languages": [ { "name": "", "level": "" } ]
            }

            ===== RESUME (JSON) =====
            %s

            ===== JOB OFFER =====
            %s
            """.formatted(resume, offer);

        try {
            GeneratedResumeDto result = chatClient.prompt().user(prompt).call().entity(GeneratedResumeDto.class);
            return result == null ? emptyGeneratedResume() : result;
        } catch (Exception e) {
            log.error("Failed to tailor resume: {}", e.getMessage());
            return emptyGeneratedResume();
        }
    }

    private GeneratedResumeDto emptyGeneratedResume() {
        return new GeneratedResumeDto(
            new GeneratedResumeDto.GeneratedPersonalDto("", "", "", "", "", "", "", "", ""),
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    public RewriteResponseDto rewrite(RewriteRequestDto request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            return new RewriteResponseDto("", "", "");
        }

        String context = buildContext(request);
        String languageInstruction = (request.language() == null || request.language().isBlank())
            ? "Keep the SAME LANGUAGE as the input text."
            : "Write in " + request.language() + ".";

        String prompt = """
            You are an expert CV/resume writer. Rewrite the %s text below in THREE variants.
            %s Do not invent facts; only rephrase and improve.

            - "professional": polished, impactful, strong action verbs.
            - "concise": shorter, straight to the point, no filler.
            - "ats": optimized for Applicant Tracking Systems (relevant keywords, plain phrasing).
            %s
            Return ONLY valid JSON with exactly these keys:
            { "professional": "string", "concise": "string", "ats": "string" }

            Text to rewrite:
            %s
            """.formatted(safeKind(request.kind()), languageInstruction, context, text);

        String json = cleanJsonResponse(chatClient.prompt().user(prompt).call().content());
        try {
            return objectMapper.readValue(json, RewriteResponseDto.class);
        } catch (Exception e) {
            log.error("Failed to parse rewrite response: {}", e.getMessage());
            // Fallback: return the original text so the UI still shows something usable.
            return new RewriteResponseDto(text, text, text);
        }
    }

    private String buildContext(RewriteRequestDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.jobTitle() != null && !r.jobTitle().isBlank()) {
            sb.append("Target role: ").append(r.jobTitle()).append(". ");
        }
        if (r.company() != null && !r.company().isBlank()) {
            sb.append("Company: ").append(r.company()).append(". ");
        }
        return sb.isEmpty() ? "" : "Context: " + sb + "\n";
    }

    private String safeKind(String kind) {
        return switch (kind == null ? "" : kind) {
            case "summary" -> "professional summary";
            case "experience" -> "work experience description";
            case "project" -> "project description";
            default -> "CV";
        };
    }

    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        raw = raw.strip();
        if (raw.startsWith("```json")) raw = raw.substring(7);
        if (raw.startsWith("```")) raw = raw.substring(3);
        if (raw.endsWith("```")) raw = raw.substring(0, raw.length() - 3);
        return raw.strip();
    }
}
