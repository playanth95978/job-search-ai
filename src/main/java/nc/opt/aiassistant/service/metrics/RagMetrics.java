package nc.opt.aiassistant.service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RagMetrics {

    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRetrieval(long ms) {
        registry.timer("rag.retrieval.time").record(ms, TimeUnit.MILLISECONDS);
    }

    public void counterIncremante(String property) {
        registry.counter(property).increment();
    }

    public void recordRerank(long ms) {
        registry.timer("rag.rerank.time").record(ms, TimeUnit.MILLISECONDS);
    }

    public void recordOnnxInference(long ms) {
        Timer.builder("rag.rerank.onnx.inference.time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(ms, TimeUnit.MILLISECONDS);
    }

    public void recordLlm(long ms) {
        registry.timer("rag.llm.time").record(ms, TimeUnit.MILLISECONDS);
    }

    public void incrementTokensInput(int tokens) {
        registry.counter("rag.tokens.input").increment(tokens);
        registry.summary("rag.tokens.input.average").record(tokens);
    }

    public void recordTotalTokens(int tokens) {
        registry.summary("rag.tokens.total.average").record(tokens);
    }

    public void incrementTokensOutput(int tokens) {
        registry.counter("rag.tokens.output").increment(tokens);
        registry.summary("rag.tokens.output.average").record(tokens);
    }

    public void cacheHit() {
        registry.counter("rag.cache.hit").increment();
    }

    public void cacheMiss() {
        registry.counter("rag.cache.miss").increment();
    }
}
