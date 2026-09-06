package nc.opt.aiassistant.config.ai;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.ai.MistralRateLimiter;
import nc.opt.aiassistant.service.ai.ThrottledChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wraps the auto-configured Mistral chat model in {@link ThrottledChatModel}, once, at the source.
 *
 * <p>Every {@code ChatClient} in the application is built from the {@code mistralAiChatModel} bean,
 * so decorating that single bean throttles all of them — including the services that never called
 * the limiter themselves. Doing it as a {@link BeanPostProcessor} rather than a replacement
 * {@code @Bean} leaves Spring AI's auto-configuration untouched: it still builds the real model,
 * with its own HTTP stack, observations and options, and we only swap what the context hands out.
 *
 * <p>The limiter is injected through an {@link ObjectProvider}: a bean post-processor is created
 * very early, and a hard dependency would drag the limiter — and anything it touches — out of the
 * normal lifecycle before it is ready.
 */
@Configuration
@Slf4j
public class MistralThrottleConfig {

    static final String MISTRAL_CHAT_MODEL_BEAN = "mistralAiChatModel";

    @Bean
    public static BeanPostProcessor mistralChatModelThrottle(ObjectProvider<MistralRateLimiter> rateLimiter) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!MISTRAL_CHAT_MODEL_BEAN.equals(beanName) || !(bean instanceof ChatModel model) || bean instanceof ThrottledChatModel) {
                    return bean;
                }
                log.info("Mistral : modèle de chat placé derrière le limiteur de débit partagé");
                return new ThrottledChatModel(model, rateLimiter.getObject());
            }
        };
    }
}
