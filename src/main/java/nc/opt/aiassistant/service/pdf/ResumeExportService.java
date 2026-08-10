package nc.opt.aiassistant.service.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CvResumeRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the user's own CV to PDF — the builder's export button, and the file an ATS connector
 * attaches to an auto-applied application.
 *
 * <p>Deliberately knows nothing about job offers or AI tailoring. The ATS connectors depend on this
 * service, and tailoring depends (transitively, through offer enrichment) back on the connectors, so
 * folding both concerns into one bean closes a dependency cycle. The offer-specific variant therefore
 * lives one level up, in {@link TailoredResumeExportService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeExportService {

    private final CvResumeRepository resumeRepository;
    private final ResumePdfRenderer renderer;
    private final ResumePdfService plainTextFallback;
    private final ObjectMapper objectMapper;

    /** Renders an already-parsed resume — the path used when the front-end posts unsaved editor state. */
    public byte[] render(ResumeDocument resume) {
        return renderer.render(resume);
    }

    /** Renders the current user's stored resume. */
    @Transactional(readOnly = true)
    public byte[] renderCurrentUser() {
        return renderer.render(
            currentUserResume().orElseThrow(() -> new BadRequestAlertException("No resume to export", "resume", "noResume"))
        );
    }

    /**
     * The PDF attached to an ATS auto-application.
     *
     * <p>Prefers the user's designed CV, since that is the document they actually reviewed. Falls back
     * to the plain-text rendering of the extracted candidate profile only when no resume has been built
     * yet — the profile stores its sections as free text, so there is nothing structured to lay out.</p>
     */
    @Transactional(readOnly = true)
    public byte[] renderForAts(CandidateProfile profile) {
        return currentUserResume()
            .map(renderer::render)
            .orElseGet(() -> plainTextFallback.fromProfile(profile));
    }

    /** The current user's stored resume, or empty when they have none. */
    @Transactional(readOnly = true)
    public Optional<ResumeDocument> currentUserResume() {
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(resumeRepository::findByUserId)
            .map(entity -> parse(entity.getData()));
    }

    private ResumeDocument parse(String json) {
        try {
            return objectMapper.readValue(json, ResumeDocument.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Stored resume is not readable: " + e.getMessage(), "resume", "unreadable");
        }
    }
}
