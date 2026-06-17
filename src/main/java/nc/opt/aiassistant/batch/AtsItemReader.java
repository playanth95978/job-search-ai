package nc.opt.aiassistant.batch;

import java.util.Iterator;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector;
import org.springframework.batch.infrastructure.item.ItemReader;

/**
 * ItemReader that iterates over job offers from an AtsConnector using its paginated Flux.
 */
public class AtsItemReader implements ItemReader<JobOffer> {

    private final Iterator<JobOffer> iterator;

    public AtsItemReader(AtsConnector connector, String keywords, String location) {
        // toIterable() allows us to bridge Flux (push/async) to Iterator (pull/sync) required by Spring Batch
        this.iterator = connector.fetchJobsPaginated(keywords, location).toIterable().iterator();
    }

    @Override
    public JobOffer read() {
        return iterator.hasNext() ? iterator.next() : null;
    }
}
