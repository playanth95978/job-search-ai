package nc.opt.aiassistant.config.ai.rag;

import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public OllamaApi ollamaApi(ApplicationProperties applicationProperties) {
        return OllamaApi.builder().baseUrl(applicationProperties.getAi().getOllama().getBaseUrl()).build();
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
