package nc.opt.aiassistant.service.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.opt.aiassistant.service.vector.DataType;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class ReciprocalRankFusionService {

    private final int RRF_k = 60;

    public List<Document> rrf(List<List<Document>> rankings, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (List<Document> documentList : rankings) {
            for (int i = 0; i < documentList.size(); i++) {
                Document doc = documentList.get(i);
                if (doc == null) continue;
                String id = doc.getId(); // ⚠️ important

                docMap.putIfAbsent(id, doc);
                String source = (String) doc.getMetadata().get("source");
                double weight = DataType.valueOf(source.toUpperCase()).getWeight();

                double score = weight * (1.0 / (RRF_k + i + 1));
                scores.merge(id, score, Double::sum);
            }
        }

        return scores
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(entry -> docMap.get(entry.getKey()))
            .limit(topK)
            .toList();
    }
}
