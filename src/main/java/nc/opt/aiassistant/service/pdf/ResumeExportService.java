package nc.opt.aiassistant.service.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CvResumeRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.CvTailoringService;
import nc.opt.aiassistant.service.jobcopilot.dto.GeneratedResumeDto;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for every "give me this CV as a PDF" use case: the builder's export button, the
 * offer-tailored variant, and the file attached to an auto-applied ATS application.
 *
 * <p>All three go through the same {@link ResumePdfRenderer}, so a CV looks identical whether the user
 * downloaded it or a connector uploaded it on their behalf.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeExportService {

    private final CvResumeRepository resumeRepository;
    private final CvTailoringService cvTailoringService;
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
     * Renders the offer-tailored variant of the master resume. The tailoring only rewrites content, so
     * the presentation — template, theme, section order — is inherited from the master CV; that is what
     * makes a tailored export recognisably the same document as the one the user designed.
     */
    @Transactional(readOnly = true)
    public byte[] renderTailored(UUID offerId, boolean refresh) {
        GeneratedResumeDto tailored = cvTailoringService.getTailored(offerId, refresh);
        return renderer.render(merge(tailored, currentUserResume().orElse(null)));
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

    /** The current user's stored resume, or empty when they have none or it fails to parse. */
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

    /** Grafts AI-tailored content onto the master resume's presentation. */
    private ResumeDocument merge(GeneratedResumeDto tailored, ResumeDocument master) {
        ResumeDocument.Personal personal =
            master != null && tailored.personal() == null ? master.personal() : toPersonal(tailored.personal(), master);

        return new ResumeDocument(
            master == null ? null : master.title(),
            personal,
            tailored.summary(),
            map(tailored.experiences(), this::toExperience),
            map(tailored.education(), this::toEducation),
            map(tailored.projects(), this::toProject),
            map(tailored.certifications(), this::toCertification),
            map(tailored.skills(), this::toSkill),
            map(tailored.languages(), this::toLanguage),
            // Tailoring never touches referees; carry the master's through so an Australian export keeps them.
            master == null ? List.of() : master.referees(),
            master == null ? null : master.template(),
            master == null ? null : master.theme(),
            master == null ? null : master.sectionOrder(),
            master == null ? List.of() : master.hiddenSections()
        );
    }

    private ResumeDocument.Personal toPersonal(GeneratedResumeDto.GeneratedPersonalDto p, ResumeDocument master) {
        if (p == null) {
            return null;
        }
        // `workingRights` has no counterpart in the generated DTO; it is a stable fact about the
        // candidate rather than something to re-derive per offer, so it comes from the master resume.
        String workingRights = master == null ? null : master.personal().workingRights();
        return new ResumeDocument.Personal(
            p.firstName(),
            p.lastName(),
            p.title(),
            p.email(),
            p.phone(),
            p.location(),
            p.linkedin(),
            p.github(),
            p.website(),
            workingRights
        );
    }

    private ResumeDocument.Experience toExperience(GeneratedResumeDto.GeneratedExperienceDto e) {
        return new ResumeDocument.Experience(
            e.jobTitle(),
            e.company(),
            e.startDate(),
            e.endDate(),
            e.current(),
            e.description(),
            e.achievements()
        );
    }

    private ResumeDocument.Education toEducation(GeneratedResumeDto.GeneratedEducationDto e) {
        return new ResumeDocument.Education(e.degree(), e.school(), e.startDate(), e.endDate(), e.description());
    }

    private ResumeDocument.Project toProject(GeneratedResumeDto.GeneratedProjectDto p) {
        return new ResumeDocument.Project(p.name(), p.description(), p.url(), p.technologies());
    }

    private ResumeDocument.Certification toCertification(GeneratedResumeDto.GeneratedCertificationDto c) {
        return new ResumeDocument.Certification(c.name(), c.issuer(), c.date());
    }

    private ResumeDocument.Skill toSkill(GeneratedResumeDto.GeneratedSkillDto s) {
        return new ResumeDocument.Skill(s.name(), s.level());
    }

    private ResumeDocument.Language toLanguage(GeneratedResumeDto.GeneratedLanguageDto l) {
        return new ResumeDocument.Language(l.name(), l.level());
    }

    private <S, T> List<T> map(List<S> source, java.util.function.Function<S, T> mapper) {
        return source == null ? List.of() : source.stream().map(mapper).toList();
    }
}
