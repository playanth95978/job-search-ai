package nc.opt.aiassistant.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Creates one partition per ROME grand domaine (optionally crossed with départements), so each
 * worker step runs an independent, bounded France Travail query. This both covers every job category
 * and keeps each query under the API's 3150-result ceiling.
 *
 * <p>When {@code departements} is empty, partitions are national (one per grand domaine). Supplying a
 * list of département codes multiplies the partitions (grand domaine × département), which is the lever
 * to pull when a single national grand domaine query still exceeds 3150 results.</p>
 */
public class GrandDomainePartitioner implements Partitioner {

    private final List<String> departements;

    public GrandDomainePartitioner(List<String> departements) {
        this.departements = departements == null ? List.of() : departements;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (RomeGrandDomaine domaine : RomeGrandDomaine.ALL) {
            if (departements.isEmpty()) {
                partitions.put("domaine:" + domaine.code(), context(domaine, null));
            } else {
                for (String departement : departements) {
                    partitions.put("domaine:" + domaine.code() + ":dep:" + departement, context(domaine, departement));
                }
            }
        }
        return partitions;
    }

    private ExecutionContext context(RomeGrandDomaine domaine, String departement) {
        ExecutionContext context = new ExecutionContext();
        context.putString("grandDomaine", domaine.code());
        context.putString("grandDomaineLabel", domaine.label());
        if (departement != null) {
            context.putString("departement", departement);
        }
        return context;
    }
}
