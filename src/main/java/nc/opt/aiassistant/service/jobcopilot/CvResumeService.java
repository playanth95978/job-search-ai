package nc.opt.aiassistant.service.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CvResume;
import nc.opt.aiassistant.domain.CvResumeVersion;
import nc.opt.aiassistant.repository.jobcopilot.CvResumeRepository;
import nc.opt.aiassistant.repository.jobcopilot.CvResumeVersionRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.dto.ResumeDto;
import nc.opt.aiassistant.service.jobcopilot.dto.ResumeVersionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence + versioning for the CV Builder.
 *
 * <p>{@link #save} is the auto-save path: it upserts the user's single editable resume without
 * creating history. {@link #createVersion} takes an explicit immutable snapshot so version history
 * stays meaningful (no spam from per-keystroke auto-saves).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvResumeService {

    private final CvResumeRepository resumeRepository;
    private final CvResumeVersionRepository versionRepository;

    @Transactional(readOnly = true)
    public Optional<ResumeDto> getForCurrentUser() {
        return resumeRepository.findByUserId(currentUser()).map(this::toDto);
    }

    /** Auto-save: creates or updates the current user's resume. No version snapshot. */
    @Transactional
    public ResumeDto save(ResumeDto dto) {
        String userId = currentUser();
        Instant now = Instant.now();
        CvResume resume = resumeRepository.findByUserId(userId).orElseGet(() -> {
            CvResume created = new CvResume();
            created.setId(UUID.randomUUID());
            created.setUserId(userId);
            created.setVersion(1);
            created.setCreatedAt(now);
            return created;
        });
        resume.setTitle(dto.title());
        resume.setTemplate(dto.template());
        resume.setData(dto.data());
        resume.setUpdatedAt(now);
        return toDto(resumeRepository.save(resume));
    }

    /** Captures an immutable snapshot of the current resume and bumps its version. */
    @Transactional
    public ResumeVersionDto createVersion() {
        CvResume resume = resumeRepository
            .findByUserId(currentUser())
            .orElseThrow(() -> new IllegalStateException("No resume to snapshot"));

        CvResumeVersion snapshot = new CvResumeVersion();
        snapshot.setId(UUID.randomUUID());
        snapshot.setResumeId(resume.getId());
        snapshot.setVersion(resume.getVersion());
        snapshot.setTitle(resume.getTitle());
        snapshot.setTemplate(resume.getTemplate());
        snapshot.setData(resume.getData());
        snapshot.setCreatedAt(Instant.now());
        versionRepository.save(snapshot);

        resume.setVersion(resume.getVersion() + 1);
        resumeRepository.save(resume);

        return toVersionDto(snapshot);
    }

    @Transactional(readOnly = true)
    public List<ResumeVersionDto> listVersions() {
        return resumeRepository
            .findByUserId(currentUser())
            .map(r -> versionRepository.findByResumeIdOrderByVersionDesc(r.getId()).stream().map(this::toVersionDto).toList())
            .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public Optional<ResumeDto> getVersion(UUID versionId) {
        return resumeRepository
            .findByUserId(currentUser())
            .flatMap(r -> versionRepository.findByIdAndResumeId(versionId, r.getId()))
            .map(v -> new ResumeDto(v.getId(), v.getTitle(), v.getTemplate(), v.getData(), v.getVersion(), v.getCreatedAt()));
    }

    /** Restores a past snapshot into the current resume (snapshotting the current state first). */
    @Transactional
    public Optional<ResumeDto> restoreVersion(UUID versionId) {
        CvResume resume = resumeRepository.findByUserId(currentUser()).orElse(null);
        if (resume == null) {
            return Optional.empty();
        }
        Optional<CvResumeVersion> target = versionRepository.findByIdAndResumeId(versionId, resume.getId());
        if (target.isEmpty()) {
            return Optional.empty();
        }
        createVersion(); // keep the current state in history before overwriting
        CvResumeVersion v = target.get();
        resume.setTitle(v.getTitle());
        resume.setTemplate(v.getTemplate());
        resume.setData(v.getData());
        resume.setUpdatedAt(Instant.now());
        return Optional.of(toDto(resumeRepository.save(resume)));
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new IllegalStateException("User not authenticated"));
    }

    private ResumeDto toDto(CvResume r) {
        return new ResumeDto(r.getId(), r.getTitle(), r.getTemplate(), r.getData(), r.getVersion(), r.getUpdatedAt());
    }

    private ResumeVersionDto toVersionDto(CvResumeVersion v) {
        return new ResumeVersionDto(v.getId(), v.getVersion(), v.getTitle(), v.getTemplate(), v.getCreatedAt());
    }
}
