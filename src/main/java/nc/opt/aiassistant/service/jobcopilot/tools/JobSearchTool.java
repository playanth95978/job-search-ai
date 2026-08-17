package nc.opt.aiassistant.service.jobcopilot.tools;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.jobcopilot.ats.JobSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobSearchTool {

    private final JobSearchService jobSearchService;

    @Tool(description = "Search job offers across multiple ATS platforms and job boards. Returns matching job listings.")
    public String searchJobs(
        @ToolParam(description = "Keywords to search for (e.g. 'Java Spring Boot')") String keywords,
        @ToolParam(description = "Location filter (e.g. 'Paris', 'Remote')") String location
    ) {
        List<JobOffer> offers = jobSearchService.searchAll(keywords, location);
        if (offers.isEmpty()) return "No job offers found for: " + keywords;
        return offers.stream().limit(20).map(this::formatOffer).collect(Collectors.joining("\n---\n"));
    }

    @Tool(
        description = "Fetch the open positions of a company from its NAME (e.g. 'Hugging Face', 'Canva'). " +
            "Use this whenever the user names a company, or when you target companies that fit their profile — " +
            "never ask them for a careers URL. The ATS hosting the board is resolved automatically."
    )
    public String fetchCompanyJobs(@ToolParam(description = "Company name, as the user would say it") String companyName) {
        return jobSearchService
            .fetchFromCompanyName(companyName)
            .map(board ->
                "%d open position(s) for %s (via %s):%n%s".formatted(
                    board.offers().size(),
                    companyName,
                    board.source(),
                    board.offers().stream().limit(20).map(this::formatOffer).collect(Collectors.joining("\n---\n"))
                )
            )
            .orElse(
                "No public job board found for \"%s\". The company may not use a supported ATS (Greenhouse, Lever, SmartRecruiters, Ashby, Workable, Recruitee), or it publishes under a different name.".formatted(
                    companyName
                )
            );
    }

    @Tool(description = "Fetch job offers from a specific ATS company page URL — only when the user provides the URL themselves")
    public String fetchFromUrl(@ToolParam(description = "ATS company page URL") String url) {
        List<JobOffer> offers = jobSearchService.fetchFromUrl(url);
        if (offers.isEmpty()) return "No jobs found at: " + url;
        return offers.stream().limit(20).map(this::formatOffer).collect(Collectors.joining("\n---\n"));
    }

    private String formatOffer(JobOffer o) {
        return "%s at %s | %s | %s | %s".formatted(
            o.getTitle(),
            o.getCompany(),
            o.getLocation() != null ? o.getLocation() : "N/A",
            Boolean.TRUE.equals(o.getRemote()) ? "Remote" : "On-site",
            o.getApplyUrl() != null ? o.getApplyUrl() : ""
        );
    }
}
