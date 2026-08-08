package nc.opt.aiassistant.domain.enumeration;

/**
 * How the auto-apply agent handles prepared applications.
 */
public enum AutoApplyMode {
    /** Prepare applications as drafts (CV + cover letter) and wait for the user to approve/submit. */
    VALIDATION,
    /** Submit automatically via the ATS API within the configured guardrails. */
    AUTO,
}
