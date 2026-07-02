package nc.opt.aiassistant.service.jobcopilot.tools;

import org.springframework.stereotype.Component;

/**
 * Aggregator passed to {@code ChatClient#prompt().tools(...)} for the Confluence agent.
 */
@Component
public class JobAgentToolbox {

    private final Object[] tools;

    public JobAgentToolbox(
        JobSearchTool jobSearchTool,
        MatchingTool matchingTool,
        CvParsingTool cvParsingTool,
        ApplicationTrackingTool applicationTrackingTool,
        CoverLetterTool coverLetterTool,
        GeoLocationTool geoLocationTool
    ) {
        this.tools = new Object[] { jobSearchTool, matchingTool, cvParsingTool, applicationTrackingTool, coverLetterTool, geoLocationTool };
    }

    public Object[] tools() {
        return tools;
    }
}
