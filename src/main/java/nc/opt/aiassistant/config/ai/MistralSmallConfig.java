package nc.opt.aiassistant.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class MistralSmallConfig {

    /**
     * Options of the cheap / high-quota Mistral model used as a fallback when the primary model
     * answers 429 (free-tier quota). Exposed as a bean so any client can degrade to it by overriding
     * its request options, instead of building a second {@code ChatModel} with its own HTTP stack.
     *
     * <p>Prototype-scoped: {@code ChatClient#defaultOptions} takes a mutable builder, so each
     * injection point gets its own instance rather than sharing (and possibly mutating) a singleton.
     */
    @Bean("mistralSmallChatOptions")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChatOptions.Builder mistralSmallChatOptions(
        @Value("${spring.ai.mistral.fallback.model:mistral-small-latest}") String model,
        @Value("${spring.ai.mistral.fallback.temperature:0.1}") Double temperature
    ) {
        return MistralAiChatOptions.builder().model(model).temperature(temperature);
    }

    @Bean
    public ChatClient chatClientSmall(
        @Qualifier("mistralAiChatModel") ChatModel chatModel,
        @Qualifier("mistralSmallChatOptions") ChatOptions.Builder mistralSmallChatOptions
    ) {
        return ChatClient.builder(chatModel).defaultOptions(mistralSmallChatOptions).build();
    }
}
