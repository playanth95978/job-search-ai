package nc.opt.aiassistant.service.jobcopilot;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Drives a job-interview roleplay: the LLM plays the recruiter, asking interview questions
 * for a given job offer and giving feedback on the candidate's answers.
 */
@Slf4j
@Service
public class InterviewCoachService {

    private final ChatClient chatClient;

    public InterviewCoachService(@Qualifier("mistralAiChatModel") ChatModel chatModel, ChatMemoryRepository chatMemoryRepository) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(20).build();
        this.chatClient = ChatClient.builder(chatModel).defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    /** Starts a new roleplay session: the recruiter introduces itself and asks the first question. */
    public String startSession(CandidateProfile profile, JobOffer offer, String conversationId, String language) {
        return chatClient
            .prompt()
            .system(buildSystemPrompt(profile, offer, language))
            .user("Démarre l'entretien : présente-toi brièvement en tant que recruteur, puis pose ta première question.")
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    }

    /** Sends the candidate's answer and returns the recruiter's feedback plus the next question. */
    public String respond(CandidateProfile profile, JobOffer offer, String conversationId, String answer, String language) {
        return chatClient
            .prompt()
            .system(buildSystemPrompt(profile, offer, language))
            .user(answer)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    }

    private String buildSystemPrompt(CandidateProfile profile, JobOffer offer, String language) {
        String languageRule = (language == null || language.isBlank())
            ? "Réponds dans la langue de l'offre d'emploi."
            : "Réponds impérativement en %s, quelle que soit la langue utilisée par le candidat.".formatted(language);
        return """
        Tu es un recruteur expérimenté qui fait passer un entretien d'embauche oral pour le poste suivant.

        POSTE :
        Titre : %s
        Entreprise : %s
        Description : %s

        CANDIDAT :
        Nom : %s
        Compétences : %s
        Expérience : %s

        RÈGLES :
        - Pose une seule question d'entretien à la fois (technique, comportementale ou mise en situation), adaptée au poste.
        - Après la réponse du candidat, donne un retour court et constructif (points forts, points à améliorer),
          puis enchaîne avec la question suivante.
        - Reste dans le rôle du recruteur, sois professionnel et bienveillant.
        - Sois concis.
        - %s
        """.formatted(
            offer.getTitle(),
            offer.getCompany(),
            truncate(offer.getDescription(), 1500),
            profile.getFullName(),
            profile.getSkills(),
            profile.getExperiences(),
            languageRule
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
