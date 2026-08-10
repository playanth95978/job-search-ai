package nc.opt.aiassistant.service.pdf;

import java.nio.charset.StandardCharsets;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Dedicated Thymeleaf engine for the CV → PDF templates.
 *
 * <p>It is deliberately separate from the auto-configured {@code templateEngine} (mail + error pages)
 * for one reason: the CV templates run in {@link TemplateMode#XML}, not HTML. openhtmltopdf parses its
 * input with a strict XML parser, and the HTML5 serializer would emit unclosed void elements
 * ({@code <br>}, {@code <meta>}) that make that parse fail. XML mode guarantees well-formed output for
 * free — which is also why no Jsoup "clean-up" pass is needed anywhere in this pipeline: the markup is
 * ours and templated, never model- or AI-authored.</p>
 *
 * <p>Consequence for template authors: only the five predefined XML entities are available
 * ({@code &lt; &gt; &amp; &quot; &apos;}). Anything else must be a numeric reference such as
 * {@code &#160;} — there is no DOCTYPE, on purpose, so no DTD is ever fetched at render time.</p>
 */
@Configuration
public class CvPdfTemplateConfig {

    @Bean
    public SpringTemplateEngine cvPdfTemplateEngine(ApplicationContext applicationContext) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/templates/cv/");
        resolver.setSuffix(".xhtml");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setEnableSpringELCompiler(true);
        return engine;
    }
}
