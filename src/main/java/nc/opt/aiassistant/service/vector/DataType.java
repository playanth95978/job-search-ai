package nc.opt.aiassistant.service.vector;

import lombok.Getter;
import nc.opt.aiassistant.domain.JobOffer_;

@Getter
public enum DataType {
    PDF(0.9),
    CONFLUENCE(1.0),
    GITHUB(1.2),
    JIRA(0.8),
    DOCUMENT(0.8),
    FILE_UPLOAD(0.8),
    JOB_OFFER(1.0);

    // pondération pour calcule score final grace au reranking RRF
    private final double weight;

    DataType(double weight) {
        this.weight = weight;
    }
}
