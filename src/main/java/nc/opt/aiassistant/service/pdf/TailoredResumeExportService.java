package nc.opt.aiassistant.service.pdf;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.service.jobcopilot.CvTailoringService;
import nc.opt.aiassistant.service.jobcopilot.dto.GeneratedResumeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the offer-tailored variant of the master resume.
 *
 * <p>Kept apart from {@link ResumeExportService} on purpose: this one pulls in {@link CvTailoringService},
 * which reaches the ATS connectors through offer enrichment — and those connectors export PDFs. Only the
 * REST layer depends on this bean, so the cycle never forms.</p>
 */
@Service
@RequiredArgsConstructor
public class TailoredResumeExportService {

    private final CvTailoringService cvTailoringService;
    private final ResumeExportService resumeExportService;
    private final ResumePdfRenderer renderer;

    /**
     * Tailoring only rewrites content, so the presentation — template, theme, section order — is
     * inherited from the master CV; that is what makes a tailored export recognisably the same document
     * as the one the user designed.
     */
    @Transactional(readOnly = true)
    public byte[] renderTailored(UUID offerId, boolean refresh) {
        GeneratedResumeDto tailored = cvTailoringService.getTailored(offerId, refresh);
        return renderer.render(merge(tailored, resumeExportService.currentUserResume().orElse(null)));
    }

    /** Grafts AI-tailored content onto the master resume's presentation. */
    private ResumeDocument merge(GeneratedResumeDto tailored, ResumeDocument master) {
        return new ResumeDocument(
            master == null ? null : master.title(),
            toPersonal(tailored.personal(), master),
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
            return master == null ? null : master.personal();
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

    private <S, T> List<T> map(List<S> source, Function<S, T> mapper) {
        return source == null ? List.of() : source.stream().map(mapper).toList();
    }
}
