package nc.opt.aiassistant.config.ai;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Modele de chat de l'agent Job Copilot, interchangeable a la configuration.
 *
 * <p>L'agent est le seul consommateur de ce bean. Les autres services (lettre de motivation,
 * reecriture de CV, parcours de carriere) restent sur Mistral : on veut comparer deux modeles
 * sur <b>un</b> comportement precis — celui qui consiste a appeler les bons outils, dans le bon
 * ordre, et a ne jamais inventer un score. Changer le modele partout melangerait les effets.</p>
 *
 * <p>Bascule :</p>
 * <pre>{@code
 * jobcopilot.chat-model=gemini   # ou mistral (defaut)
 * }</pre>
 *
 * <p>La propriete est volontairement hors du prefixe {@code application.} : celui-ci est lie a
 * {@code ApplicationProperties} avec {@code ignoreUnknownFields = false}, donc toute cle non
 * declaree y ferait echouer le demarrage.</p>
 *
 * <p><b>Authentification</b> : aucune cle ici. Le client Vertex utilise les Application Default
 * Credentials — {@code gcloud auth application-default login} en local, le compte de service
 * attache a la machine en deploiement.</p>
 */
@Configuration
public class JobCopilotModelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JobCopilotModelConfiguration.class);

    /**
     * Modele pilotant l'agent. Bean nomme plutot que {@code @Primary} : plusieurs
     * {@link ChatModel} coexistent dans le contexte, et un primaire changerait silencieusement
     * le modele de tous les services qui injectent {@code ChatModel} sans qualifier.
     */
    @Bean("jobCopilotChatModel")
    public ChatModel jobCopilotChatModel(
        @Value("${jobcopilot.chat-model:mistral}") String provider,
        @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
        @Value("${jobcopilot.gemini.project-id:${VERTEX_AI_PROJECT_ID:}}") String projectId,
        @Value("${jobcopilot.gemini.location:europe-west1}") String location,
        @Value("${jobcopilot.gemini.model:gemini-2.5-flash}") String model,
        @Value("${jobcopilot.gemini.temperature:0.2}") Double temperature
    ) {
        if (!"gemini".equalsIgnoreCase(provider)) {
            log.info("Job Copilot : modele Mistral (jobcopilot.chat-model={})", provider);
            return mistralChatModel;
        }

        Assert.hasText(projectId, "jobcopilot.gemini.project-id (ou VERTEX_AI_PROJECT_ID) est requis pour jobcopilot.chat-model=gemini");

        // vertexAI(true) : on passe par Vertex AI et les ADC du projet, et non par l'API Gemini
        // publique qui attendrait une cle d'API.
        Client client = Client.builder().vertexAI(true).project(projectId).location(location).build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(model).temperature(temperature).build();

        log.info("Job Copilot : modele Gemini {} sur {}/{}", model, projectId, location);
        return GoogleGenAiChatModel.builder().genAiClient(client).options(options).build();
    }
}
