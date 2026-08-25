package nc.opt.aiassistant.service.jobcopilot.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.jobcopilot.dto.ToolOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Mirrors the Rust collector tests in {@code job_agent_tools}. The collector is what lets the
 * controller know a search returned twelve offers — without it the turn's buttons cannot be built.
 */
class ToolOutcomeCollectorTest {

    private static ToolContext contextWithCollector(List<ToolOutcome> collector) {
        return new ToolContext(Map.of("userId", "alice", ToolOutcome.CONTEXT_KEY, collector));
    }

    private static JobOffer offer(String title) {
        JobOffer offer = new JobOffer();
        offer.setId(UUID.randomUUID());
        offer.setTitle(title);
        return offer;
    }

    @Test
    void recordedOutcomesKeepTheirOrder() {
        // Order matters: ChatActionFactory builds the buttons in outcome order, and display order
        // follows from it.
        List<ToolOutcome> collector = Collections.synchronizedList(new ArrayList<>());
        ToolContext context = contextWithCollector(collector);

        ToolSupport.record(context, new ToolOutcome.ProfileMissing());
        ToolSupport.record(context, new ToolOutcome.ApplicationsListed(2));

        assertThat(collector).containsExactly(new ToolOutcome.ProfileMissing(), new ToolOutcome.ApplicationsListed(2));
    }

    @Test
    void aNullContextIsIgnored() {
        // Losing a button is harmless; throwing here would fail a turn that otherwise succeeded.
        assertThatCode(() -> ToolSupport.record(null, new ToolOutcome.ProfileMissing())).doesNotThrowAnyException();
    }

    @Test
    void aContextWithoutCollectorIsIgnored() {
        ToolContext context = new ToolContext(Map.of("userId", "alice"));

        assertThatCode(() -> ToolSupport.record(context, new ToolOutcome.ProfileMissing())).doesNotThrowAnyException();
    }

    @Test
    void citedOffersAreTruncatedAtCollectionTime() {
        // No point cloning fifty titles when ChatActionFactory keeps only three.
        List<JobOffer> offers = List.of(offer("a"), offer("b"), offer("c"), offer("d"), offer("e"));

        List<ToolOutcome.CitedOffer> cited = ToolSupport.cited(offers, 3);

        assertThat(cited).hasSize(3);
        assertThat(cited.get(0).title()).isEqualTo("a");
    }

    @Test
    void citingAnEmptyListYieldsAnEmptyList() {
        // The empty-search path records an outcome with no offer: it is what makes the front offer
        // "widen the search".
        assertThat(ToolSupport.cited(List.of(), 3)).isEmpty();
    }
}
