package nc.opt.aiassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.UnboundElementsSourceFilter;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/**
 * Lie le vrai {@code config/application.yml} à {@link ApplicationProperties}, sans démarrer de
 * contexte Spring.
 *
 * <p><b>Pourquoi ce test</b> : {@code @ConfigurationProperties(ignoreUnknownFields = false)} fait
 * échouer le démarrage de l'application dès qu'une clé YAML sous {@code application.} n'a pas de
 * champ correspondant — typiquement quand on ajoute un bloc de configuration en oubliant sa classe,
 * ou qu'on supprime une classe en laissant son bloc. Rien à la compilation ne détecte ce décalage :
 * il ne se voit qu'au lancement. Ce test le rend visible en quelques millisecondes.</p>
 */
class ApplicationPropertiesBindingTest {

    /**
     * Chemin explicite vers le YAML de <b>production</b>. Passer par le classpath chargerait celui de
     * {@code src/test/resources}, qui masque le premier et ne contient aucun bloc {@code application:} —
     * le test ne vérifierait alors plus rien.
     */
    private static final String PRODUCTION_YAML = "src/main/resources/config/application.yml";

    private ApplicationProperties bindActualYaml() throws IOException {
        FileSystemResource yaml = new FileSystemResource(PRODUCTION_YAML);
        assertThat(yaml.exists()).as("YAML de production introuvable : %s", PRODUCTION_YAML).isTrue();

        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application.yml", yaml);

        StandardEnvironment environment = new StandardEnvironment();
        // Les blocs YAML se déclarent dans l'ordre du fichier ; l'ordre d'ajout est sans importance
        // ici, aucune clé n'étant définie deux fois.
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        // `Binder` seul ignore les clés inconnues : le refus vient d'un BindHandler que Spring
        // n'installe qu'au démarrage, pour les @ConfigurationProperties(ignoreUnknownFields = false).
        // Sans lui, ce test passerait sur une configuration qui fait pourtant échouer l'application.
        BindHandler failOnUnknownKeys = new NoUnboundElementsBindHandler(BindHandler.DEFAULT, new UnboundElementsSourceFilter());

        return Binder.get(environment)
            .bind("application", Bindable.of(ApplicationProperties.class), failOnUnknownKeys)
            .orElseThrow(() -> new AssertionError("Aucune propriété liée sous le préfixe « application »"));
    }

    @Test
    void everyApplicationKeyInYamlMapsToAField() throws IOException {
        // L'assertion est le fait même que le binding aboutisse : une clé orpheline le ferait échouer,
        // exactement comme au démarrage de l'application.
        assertThat(bindActualYaml()).isNotNull();
    }

    @Test
    void gdprRetentionDefaultsMatchTheDocumentedPolicy() throws IOException {
        ApplicationProperties.Gdpr gdpr = bindActualYaml().getJobCopilot().getGdpr();

        assertThat(gdpr.isEnabled()).isTrue();
        // 2 ans à compter de la dernière activité : recommandation CNIL sur les données de candidature.
        assertThat(gdpr.getRetentionDays()).isEqualTo(730);
        assertThat(gdpr.getBatchSize()).isEqualTo(100);
        // Après l'ingestion (2 h) et la purge des offres (4 h), pour ne pas les concurrencer.
        assertThat(gdpr.getCron()).isEqualTo("0 0 5 * * *");
    }
}
