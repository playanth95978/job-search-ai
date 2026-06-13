//package nc.opt.aiassistant.config.ai;
//
//import nc.opt.aiassistant.config.ApplicationProperties;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.ollama.OllamaChatModel;
//import org.springframework.ai.openai.OpenAiChatModel;
//import org.springframework.ai.openai.OpenAiChatOptions;
//import org.springframework.ai.openai.api.OpenAiApi;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * Provides the {@code localFallbackClient} bean used as the last-resort
// * fallback when the primary cloud model (Mistral) is rate-limited.
// *
// * <p>Two mutually-exclusive inner configs:
// * <ul>
// *   <li>{@code application.ai.llama-server.enabled=true} → llama-server
// *       via OpenAI-compatible API (recommended for Qwen, LLaMA, etc.).</li>
// *   <li>Otherwise → Ollama as fallback (existing behaviour).</li>
// * </ul>
// */
//@Configuration
//public class LlamaServerConfig {
//
//    @Configuration
//    @ConditionalOnProperty(prefix = "application.ai.llama-server", name = "enabled", havingValue = "true")
//    static class LlamaServerFallback {
//
//        @Bean
//        @Qualifier("llamaServerChatModel")
//        public ChatModel llamaServerChatModel(ApplicationProperties properties) {
//            ApplicationProperties.Ai.LlamaServer cfg = properties.getAi().getLlamaServer();
//            OpenAiApi api = OpenAiApi.builder().baseUrl(cfg.getBaseUrl()).apiKey(cfg.getApiKey()).build();
//            OpenAiChatOptions options = OpenAiChatOptions.builder().model(cfg.getModel()).temperature(0.7).build();
//            return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
//        }
//
//        @Bean
//        public ChatClient localFallbackClient(@Qualifier("llamaServerChatModel") ChatModel llamaServerChatModel) {
//            return ChatClient.create(llamaServerChatModel);
//        }
//    }
//
//    @Configuration
//    @ConditionalOnProperty(prefix = "application.ai.llama-server", name = "enabled", havingValue = "false", matchIfMissing = true)
//    static class OllamaFallback {
//
//        @Bean
//        public ChatClient localFallbackClient(OllamaChatModel ollamaChatModel) {
//            return ChatClient.create(ollamaChatModel);
//        }
//    }
//}
