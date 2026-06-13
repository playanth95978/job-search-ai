package nc.opt.aiassistant.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MistralSmallConfig {

    @Value("${spring.ai.mistralai.api-key}")
    private String apiKey;

    @Bean
    public ChatClient chatClientSmall(@Qualifier("mistralAiChatModel") org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(org.springframework.ai.mistralai.MistralAiChatOptions.builder().model("mistral-small-latest").temperature(0.1))
            .build();
    }
}

//    @Bean
//    ChatClient chatClientKimi() {
//        var api = new org.springframework.ai.openai.api.OpenAiApi("https://openrouter.ai/api/v1", "sk-RcYAkVIW2RZJyyEWF8LzW3IKE3CQuyydH2Cyr2wAJlkyHm3c");
//        var model = new org.springframework.ai.openai.OpenAiChatModel(api, org.springframework.ai.openai.OpenAiChatOptions.builder()
//            .model("moonshotai/kimi-k2:free")
//            .temperature(0.3)
//            .build());
//        return ChatClient.builder(model).build();
//    }
//}
