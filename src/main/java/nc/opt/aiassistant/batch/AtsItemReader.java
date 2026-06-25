package nc.opt.aiassistant.batch;

import java.util.Iterator;
import java.util.function.Supplier;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector;
import org.springframework.batch.infrastructure.item.ItemReader;
import reactor.core.publisher.Flux;

/**
 * ItemReader that iterates over job offers from an AtsConnector using its paginated Flux.
 *
 * <p>The Flux is subscribed <b>lazily</b> on the first {@link #read()} call (within the step thread),
 * not in the constructor — otherwise a reader instantiated at bean-creation time would start scraping
 * the remote API at application startup.</p>
 */
public class AtsItemReader implements ItemReader<JobOffer> {

    private final Supplier<Flux<JobOffer>> source;
    private Iterator<JobOffer> iterator;

    public AtsItemReader(AtsConnector connector, String keywords, String location) {
        this(() -> connector.fetchJobsPaginated(keywords, location));
    }

    public AtsItemReader(Flux<JobOffer> jobs) {
        this(() -> jobs);
    }

    private AtsItemReader(Supplier<Flux<JobOffer>> source) {
        this.source = source;
    }

    @Override
    public JobOffer read() {
        if (iterator == null) {
            // toIterable() bridges Flux (push/async) to Iterator (pull/sync) required by Spring Batch
            iterator = source.get().toIterable().iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }
}
