package nc.opt.aiassistant.service.jobcopilot.tools;

import java.util.List;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.dto.ToolOutcome;
import org.springframework.ai.chat.model.ToolContext;

/** Shared helpers for tools. */
final class ToolSupport {

    private ToolSupport() {}

    /**
     * Resolves the current user for a tool call. Prefers the {@code userId} placed in the
     * {@link ToolContext} by the agent (works on reactive/streaming threads where the security
     * ThreadLocal is not propagated), and falls back to {@link SecurityUtils} for the blocking path.
     */
    /**
     * Records what a tool just produced, for the end-of-turn action buttons.
     *
     * <p>The collector travels in the {@link ToolContext} rather than in a field, because tools are
     * singleton beans shared by every concurrent conversation — a field would mix two users' turns.
     *
     * <p>A missing or unusable collector is ignored: losing a button is harmless, whereas throwing
     * here would fail a conversation turn that otherwise succeeded.</p>
     */
    @SuppressWarnings("unchecked")
    static void record(ToolContext toolContext, ToolOutcome outcome) {
        if (toolContext == null) {
            return;
        }
        Object collector = toolContext.getContext().get(ToolOutcome.CONTEXT_KEY);
        if (collector instanceof List<?> list) {
            ((List<ToolOutcome>) list).add(outcome);
        }
    }

    /** Cites at most {@code limit} offers, reduced to what a button needs to display. */
    static List<ToolOutcome.CitedOffer> cited(List<JobOffer> offers, int limit) {
        return offers
            .stream()
            .limit(limit)
            .map(offer -> new ToolOutcome.CitedOffer(offer.getId(), offer.getTitle()))
            .toList();
    }

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
