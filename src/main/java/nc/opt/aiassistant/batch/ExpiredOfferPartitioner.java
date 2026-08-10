package nc.opt.aiassistant.batch;

import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Découpe la purge des offres expirées en {@code gridSize} partitions disjointes, réparties par hachage
 * de l'identifiant ({@code mod(abs(hashtext(id)), gridSize)}).
 *
 * <p>Contrairement au partitionnement métier de {@link GrandDomainePartitioner}, il n'y a ici aucun
 * axe fonctionnel pertinent : le seul objectif est de répartir la charge de suppression. Le hachage
 * garantit des lots <b>disjoints</b> (aucune ligne traitée deux fois, aucune contention de verrous
 * entre workers) et une répartition homogène, les UUID étant uniformément distribués.</p>
 */
public class ExpiredOfferPartitioner implements Partitioner {

    /** Clés déposées dans le contexte de chaque partition, relues par le tasklet (step-scoped). */
    public static final String PARTITION_INDEX = "partitionIndex";
    public static final String GRID_SIZE = "gridSize";

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            context.putInt(PARTITION_INDEX, i);
            context.putInt(GRID_SIZE, gridSize);
            partitions.put("purge-partition-" + i, context);
        }
        return partitions;
    }
}
