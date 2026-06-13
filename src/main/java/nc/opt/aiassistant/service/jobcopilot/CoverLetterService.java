package nc.opt.aiassistant.service.jobcopilot;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CoverLetterService {

    private final ChatClient chatClient;

    public CoverLetterService(@Qualifier("mistralAiChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public String generate(CandidateProfile profile, JobOffer offer) {
        return generate(profile, offer, null);
    }

    /**
     * @param language If provided, the cover letter is written in this language instead of the
     *                  language matching the job offer.
     */
    public String generate(CandidateProfile profile, JobOffer offer, String language) {
        String languageInstruction = (language == null || language.isBlank())
            ? "Write a professional, personalized cover letter in the language matching the job offer."
            : "Write a professional, personalized cover letter in " + language + ".";

        String prompt = """
            %s
            Keep it concise (max 400 words). Be specific — reference the candidate's actual experience.

            CANDIDATE PROFILE:
            Name: %s
            Skills: %s
            Experience: %s
            Location: %s

            JOB OFFER:
            Title: %s
            Company: %s
            Description: %s

            Write ONLY the cover letter, no extra commentary.
            Transforme cette lettre en Markdown professionnel.

            Contraintes :
            - Utiliser des titres pour les sections.
            - Mettre l'objet en évidence.
            - Conserver tous les paragraphes.
            - Générer uniquement le Markdown.
            """.formatted(
            languageInstruction,
            profile.getFullName(),
            profile.getSkills(),
            profile.getExperiences(),
            profile.getLocation(),
            offer.getTitle(),
            offer.getCompany(),
            truncate(offer.getDescription(), 2000)
        );

        return stripMarkdownFence(chatClient.prompt().user(prompt).call().content());
    }

    /**
     * Removes a leading/trailing ```markdown ... ``` (or ``` ... ```) fence the model sometimes
     * wraps its output in, so the stored text is plain Markdown ready for rendering.
     */
    private String stripMarkdownFence(String text) {
        if (text == null) return null;
        String trimmed = text.strip();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                return trimmed.substring(firstNewline + 1, trimmed.length() - 3).strip();
            }
        }
        return trimmed;
    }

    public String generateHrAnswer(CandidateProfile profile, String question) {
        String prompt = """
            Answer the following HR/recruiter question using the candidate's profile.
            Be professional and concise.

            CANDIDATE: %s
            Skills: %s
            Experience: %s

            QUESTION: %s
            """.formatted(profile.getFullName(), profile.getSkills(), profile.getExperiences(), question);

        return chatClient.prompt().user(prompt).call().content();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
