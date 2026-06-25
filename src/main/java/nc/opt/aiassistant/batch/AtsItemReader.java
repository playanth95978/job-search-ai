package nc.opt.aiassistant.batch;

import java.util.Iterator;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector;
import org.springframework.batch.infrastructure.item.ItemReader;
import reactor.core.publisher.Flux;

/**
 * ItemReader that iterates over job offers from an AtsConnector using its paginated Flux.
 */
public class AtsItemReader implements ItemReader<JobOffer> {

    private final Iterator<JobOffer> iterator;

    public AtsItemReader(AtsConnector connector, String keywords, String location) {
        this(connector.fetchJobsPaginated(keywords, location));
    }

    public AtsItemReader(Flux<JobOffer> jobs) {
        // toIterable() allows us to bridge Flux (push/async) to Iterator (pull/sync) required by Spring Batch
        this.iterator = jobs.toIterable().iterator();
    }

    @Override
    public JobOffer read() {
        return iterator.hasNext() ? iterator.next() : null;
    }
}
