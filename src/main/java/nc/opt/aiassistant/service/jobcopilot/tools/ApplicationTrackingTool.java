package nc.opt.aiassistant.service.jobcopilot.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.dto.JobApplicationDTO;
import nc.opt.aiassistant.service.jobcopilot.ApplicationTrackingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationTrackingTool {

    private final ApplicationTrackingService trackingService;

    @Tool(description = "Get all job applications for the current signed-in user with their status")
    public String getApplications() {
        String userId = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (userId == null) return "You must be signed in.";
        List<JobApplicationDTO> apps = trackingService.getApplications(userId);
        if (apps.isEmpty()) return "No applications yet.";
        return apps.stream().map(this::formatApp).collect(Collectors.joining("\n"));
    }

    @Tool(
        description = "Update the status of one of the current user's job applications (DRAFT, APPLIED, INTERVIEW, REJECTED, GHOSTED, OFFER, WITHDRAWN)"
    )
    public String updateStatus(
        @ToolParam(description = "Application ID (UUID)") String applicationId,
        @ToolParam(description = "New status") String status
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (userId == null) return "You must be signed in.";
        try {
            UUID id = UUID.fromString(applicationId);
            // Ownership guard: only let the user change their own applications.
            boolean owned = trackingService
                .getApplications(userId)
                .stream()
                .anyMatch(a -> id.equals(a.getId()));
            if (!owned) return "Application not found for the current user.";
            ApplicationStatus s = ApplicationStatus.valueOf(status.toUpperCase());
            trackingService.updateStatus(id, s);
            return "Application status updated to " + s;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get application statistics (count by status) for the current signed-in user")
    public String getStats() {
        String userId = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (userId == null) return "You must be signed in.";
        Map<ApplicationStatus, Long> stats = trackingService.getStats(userId);
        return stats
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 0)
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining(", "));
    }

    private String formatApp(JobApplicationDTO app) {
        String title = app.getJobOffer() != null ? app.getJobOffer().getTitle() : "Unknown";
        String company = app.getJobOffer() != null ? app.getJobOffer().getCompany() : "Unknown";
        return "[%s] %s at %s — score: %.0f%%".formatted(
            app.getStatus(),
            title,
            company,
            app.getMatchScore() != null ? app.getMatchScore() * 100 : 0.0
        );
    }
}
