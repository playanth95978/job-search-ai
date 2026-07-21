package nc.opt.aiassistant.config.ai.rag;

import java.time.Duration;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class EmbeddingConfig {

    @Bean
    public OllamaApi ollamaApi(ApplicationProperties applicationProperties) {
        // On CPU-only hosts embeddings are slow: give the Ollama client a generous read timeout so a
        // slow /api/embed completes instead of failing with a ReadTimeoutException.
        Duration timeout = Duration.ofSeconds(applicationProperties.getAi().getOllama().getTimeoutSeconds());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);
        return OllamaApi.builder()
            .baseUrl(applicationProperties.getAi().getOllama().getBaseUrl())
            .restClientBuilder(restClientBuilder)
            .build();
    }

    @Bean("docsEmbeddingModel")
    public EmbeddingModel docsEmbeddingModel(OllamaApi ollamaApi, ApplicationProperties applicationProperties) {
        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaEmbeddingOptions.builder().model(applicationProperties.getAi().getEmbedding().getDocs().getModel()).build())
            .build();
    }

    @Bean("codeEmbeddingModel")
    public EmbeddingModel codeEmbeddingModel(OllamaApi ollamaApi, ApplicationProperties applicationProperties) {
        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaEmbeddingOptions.builder().model(applicationProperties.getAi().getEmbedding().getCode().getModel()).build())
            .build();
    }

    @Bean("jobOfferEmbeddingModel")
    public EmbeddingModel jobOfferEmbeddingModel(OllamaApi ollamaApi, ApplicationProperties applicationProperties) {
        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaEmbeddingOptions.builder().model(applicationProperties.getAi().getEmbedding().getDocs().getModel()).build())
            .build();
    }
}
