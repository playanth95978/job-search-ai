package nc.opt.aiassistant.service.jobcopilot.tools;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.CoverLetterService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoverLetterTool {

    private final CoverLetterService coverLetterService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;

    @Tool(description = "Generate a personalized cover letter for a specific job offer")
    public String generateCoverLetter(
        @ToolParam(description = "User ID") String userId,
        @ToolParam(description = "Job offer ID (UUID)") String jobOfferId
    ) {
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) return "No candidate profile found. Upload a CV first.";

        JobOffer offer = jobOfferRepository.findById(UUID.fromString(jobOfferId)).orElse(null);
        if (offer == null) return "Job offer not found: " + jobOfferId;

        return coverLetterService.generate(profile, offer);
    }
}
