package nc.opt.aiassistant.config.ai;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(prefix = "application.ai.ollama", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OllamaLocalCloudConfig {

    @Bean
    @Primary
    public OllamaChatModel ollamaChatModel(
        @Value("${application.ai.ollama.base-url}") String baseUrl,
        @Value("${application.ai.ollama.model}") String model,
        @Value("${application.ai.ollama.token}") String token
    ) {
        WebClient.Builder webClientBuilder;
        if (token == null || token.isBlank()) {
            webClientBuilder = WebClient.builder().baseUrl(baseUrl).defaultHeader("Content-Type", "application/json");
        } else {
            webClientBuilder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", "application/json");
        }

        OllamaApi ollamaApi = OllamaApi.builder().webClientBuilder(webClientBuilder).build();

        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaChatOptions.builder().model(model).temperature(0.9).build())
            .build();
    }
}
