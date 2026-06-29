package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * Catalogue of match strength/weakness tags produced by the heuristic scorer. Each carries the
 * i18n key the frontend resolves via ngx-translate; the display text lives in the Angular i18n
 * bundles (jobCopilot.match.tag.*), never hardcoded server-side.
 */
public enum MatchTag {
    MATCHED_SKILL("jobCopilot.match.tag.matchedSkill"),
    MISSING_SKILL("jobCopilot.match.tag.missingSkill"),
    LOCATION_MATCH("jobCopilot.match.tag.locationMatch"),
    PREFERRED_LOCATION("jobCopilot.match.tag.preferredLocation"),
    LOCATION_MISMATCH("jobCopilot.match.tag.locationMismatch"),
    REMOTE("jobCopilot.match.tag.remote"),
    NOT_REMOTE("jobCopilot.match.tag.notRemote"),
    SALARY_COMPATIBLE("jobCopilot.match.tag.salaryCompatible"),
    SALARY_BELOW("jobCopilot.match.tag.salaryBelow"),
    STRONG_MATCH("jobCopilot.match.tag.strongMatch");

    private final String key;

    MatchTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
