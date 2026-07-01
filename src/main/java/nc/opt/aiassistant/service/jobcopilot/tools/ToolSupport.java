package nc.opt.aiassistant.service.jobcopilot.tools;

import nc.opt.aiassistant.security.SecurityUtils;
import org.springframework.ai.chat.model.ToolContext;

/** Shared helpers for tools. */
final class ToolSupport {

    private ToolSupport() {}

    /**
     * Resolves the current user for a tool call. Prefers the {@code userId} placed in the
     * {@link ToolContext} by the agent (works on reactive/streaming threads where the security
     * ThreadLocal is not propagated), and falls back to {@link SecurityUtils} for the blocking path.
     */
    static String currentUser(ToolContext toolContext) {
        if (toolContext != null) {
            Object uid = toolContext.getContext().get("userId");
            if (uid instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
