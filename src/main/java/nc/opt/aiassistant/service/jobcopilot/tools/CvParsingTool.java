package nc.opt.aiassistant.service.jobcopilot.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CvParsingTool {

    private final CandidateProfileRepository profileRepository;

    @Tool(description = "Get the candidate profile for the current user. Returns skills, experience, education, etc.")
    public String getCandidateProfile(@ToolParam(description = "User ID of the candidate") String userId) {
        return profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).map(this::formatProfile).orElse("No CV uploaded yet. Please upload your CV first.");
    }

    private String formatProfile(CandidateProfile p) {
        return """
        Name: %s
        Location: %s
        Years of experience: %s
        Skills: %s
        Experiences: %s
        Education: %s
        Languages: %s
        Certifications: %s
        """.formatted(
            p.getFullName(),
            p.getLocation(),
            p.getYearsOfExperience(),
            p.getSkills(),
            p.getExperiences(),
            p.getEducation(),
            p.getLanguages(),
            p.getCertifications()
        );
    }
}
