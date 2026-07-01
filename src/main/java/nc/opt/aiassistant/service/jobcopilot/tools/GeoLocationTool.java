package nc.opt.aiassistant.service.jobcopilot.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoLocation;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoLocationService;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoScore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes the GeoLocation agent to the chat LLM so it can reason about how well two locations match
 * (e.g. when a user asks whether an offer is near where they live).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoLocationTool {

    private final GeoLocationService geoLocationService;

    @Tool(
        description = "Compare a candidate/search location with a job offer location and return a 0..1 " +
            "geographic relevance score with an explanation (same city, agglomeration, province, country, remote or mismatch)."
    )
    public String matchLocation(
        @ToolParam(description = "Candidate or searched location (e.g. 'Nouméa', 'Province Sud', 'Remote')") String candidateLocation,
        @ToolParam(description = "Job offer location (e.g. 'Païta, Nouvelle-Calédonie')") String offerLocation
    ) {
        GeoLocation ref = geoLocationService.resolve(candidateLocation, true);
        GeoLocation off = geoLocationService.resolve(offerLocation, true);
        GeoScore score = geoLocationService.score(ref, off);
        return "score=%.2f level=%s (candidate=%s, offer=%s)".formatted(score.score(), score.level(), describe(ref), describe(off));
    }

    @Tool(description = "Normalise a free-text location string into structured city/region/country/remote components.")
    public String normalizeLocation(@ToolParam(description = "Free-text location string") String location) {
        return describe(geoLocationService.resolve(location, true));
    }

    private String describe(GeoLocation g) {
        if (g.remote() && !g.known()) return "Remote";
        if (!g.known()) return "unknown(" + g.raw() + ")";
        StringBuilder sb = new StringBuilder();
        if (g.city() != null) sb.append("city=").append(g.city()).append(' ');
        if (g.region() != null) sb.append("region=").append(g.region()).append(' ');
        if (g.country() != null) sb.append("country=").append(g.country());
        if (g.remote()) sb.append(" (remote)");
        return sb.toString().trim();
    }
}
