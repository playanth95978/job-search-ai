package nc.opt.aiassistant.service.jobcopilot.panel;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.service.jobcopilot.dto.JurorVerdict;
import nc.opt.aiassistant.service.jobcopilot.dto.NextSpeaker;
import nc.opt.aiassistant.service.jobcopilot.dto.PanelMessageDto;
import nc.opt.aiassistant.service.jobcopilot.dto.PanelReportDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * "Shadow Interview Panel": a multi-agent interview simulator. Three specialised jurors
 * ({@link JurorRole}) share one labelled transcript (JDBC chat memory). On each candidate answer a
 * moderator (LLM routing) picks who speaks next; at the end each juror delivers a structured verdict
 * and a president synthesises a cross deliberation.
 *
 * <p>Not built on any multi-agent framework — just several {@link ChatClient} calls (routing +
 * evaluator patterns) orchestrated in plain Java, with structured outputs via {@code .entity(...)}.
 */
@Slf4j
@Service
public class ShadowPanelService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ShadowPanelService(@Qualifier("mistralAiChatModel") ChatModel chatModel, ChatMemoryRepository chatMemoryRepository) {
        this.chatClient = ChatClient.builder(chatModel).build();
        // Manual memory (not an advisor): we tag each juror's reply so every agent sees who said what.
        this.chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(60).build();
    }

    /** Opens the session: the HR juror introduces the panel and asks the first question. */
    public PanelMessageDto startSession(CandidateProfile profile, JobOffer offer, String conversationId, String language) {
        chatMemory.clear(conversationId);
        String context = buildContext(profile, offer);
        String opening = stripLeadingLabel(
            juror(
                JurorRole.HR,
                context,
                language,
                List.of(),
                "Ouvre l'entretien : présente-toi par ton nom et présente brièvement le jury (" +
                    roster() +
                    "), puis pose ta première question."
            )
        );
        chatMemory.add(conversationId, new AssistantMessage(tag(JurorRole.HR, opening)));
        return new PanelMessageDto(conversationId, JurorRole.HR.name(), JurorRole.HR.label(), opening);
    }

    /** Processes the candidate's answer: the moderator routes to a juror, who reacts and asks the next question. */
    public PanelMessageDto respond(CandidateProfile profile, JobOffer offer, String conversationId, String answer, String language) {
        String context = buildContext(profile, offer);
        chatMemory.add(conversationId, new UserMessage(answer));
        List<Message> history = chatMemory.get(conversationId);

        JurorRole speaker = route(context, history);
        String reply = stripLeadingLabel(
            juror(
                speaker,
                context,
                language,
                history,
                "Réagis brièvement à la dernière réponse du candidat (point fort / point à creuser), puis pose ta prochaine question, dans ton rôle."
            )
        );
        chatMemory.add(conversationId, new AssistantMessage(tag(speaker, reply)));
        return new PanelMessageDto(conversationId, speaker.name(), speaker.label(), reply);
    }

    /** Ends the interview: each juror delivers a verdict; the president aggregates a cross synthesis. */
    public PanelReportDto report(CandidateProfile profile, JobOffer offer, String conversationId, String language) {
        String context = buildContext(profile, offer);
        List<Message> history = chatMemory.get(conversationId);

        List<PanelReportDto.JurorReport> reports = new ArrayList<>();
        int sum = 0;
        for (JurorRole role : JurorRole.values()) {
            JurorVerdict v = verdict(role, context, history);
            int score = Math.max(0, Math.min(10, v.score()));
            sum += score;
            reports.add(
                new PanelReportDto.JurorReport(
                    role.name(),
                    role.label(),
                    score,
                    safeList(v.strengths()),
                    safeList(v.concerns()),
                    v.recommendation()
                )
            );
        }
        int overall = Math.round((float) sum / JurorRole.values().length);
        String synthesis = synthesis(context, language, reports);
        return new PanelReportDto(conversationId, reports, overall, overallRecommendation(overall), synthesis);
    }

    // ---- individual agent calls ------------------------------------------------------------------

    private JurorRole route(String context, List<Message> history) {
        try {
            NextSpeaker decision = chatClient
                .prompt()
                .system(
                    context +
                        "\nTu es le modérateur du jury. À partir de la discussion, choisis QUEL juré doit parler ensuite " +
                        "pour challenger au mieux le candidat : HR, TECH_LEAD ou MANAGER. Varie les intervenants et suis la tournure de la discussion."
                )
                .messages(history)
                .user("Quel juré doit prendre la parole maintenant ? Donne le rôle (HR, TECH_LEAD ou MANAGER) et une courte raison.")
                .call()
                .entity(NextSpeaker.class);
            JurorRole role = JurorRole.from(decision == null ? null : decision.role());
            log.debug("Panel moderator routed to {} ({})", role, decision == null ? "?" : decision.reason());
            return role;
        } catch (Exception e) {
            log.debug("Panel routing failed, defaulting to HR: {}", e.getMessage());
            return JurorRole.HR;
        }
    }

    private String juror(JurorRole role, String context, String language, List<Message> history, String instruction) {
        return chatClient
            .prompt()
            .system(
                context +
                    "\n" +
                    role.persona() +
                    "\nRÈGLES : une seule question à la fois, reste dans ton rôle, sois concis et professionnel. " +
                    "N'utilise jamais de texte à compléter entre crochets (aucun placeholder du type [Prénom]) " +
                    "et ne préfixe pas ta réponse par ton nom ou ton rôle entre crochets. " +
                    languageRule(language)
            )
            .messages(history)
            .user(instruction)
            .call()
            .content();
    }

    private JurorVerdict verdict(JurorRole role, String context, List<Message> history) {
        try {
            JurorVerdict v = chatClient
                .prompt()
                .system(
                    context +
                        "\n" +
                        role.persona() +
                        "\nTu rends maintenant ton verdict final sur le candidat, de ton point de vue uniquement."
                )
                .messages(history)
                .user(
                    "Donne ton verdict final : score sur 10, points forts, réserves, et recommandation " +
                        "(exactement l'une de : STRONG_HIRE, HIRE, NEUTRAL, NO_HIRE)."
                )
                .call()
                .entity(JurorVerdict.class);
            return v != null ? v : new JurorVerdict(5, List.of(), List.of(), "NEUTRAL");
        } catch (Exception e) {
            log.debug("Panel verdict failed for {}: {}", role, e.getMessage());
            return new JurorVerdict(5, List.of(), List.of(), "NEUTRAL");
        }
    }

    private String synthesis(String context, String language, List<PanelReportDto.JurorReport> reports) {
        StringBuilder sb = new StringBuilder();
        for (PanelReportDto.JurorReport r : reports) {
            sb.append("### ")
                .append(r.speaker())
                .append(" — ")
                .append(r.score())
                .append("/10 (")
                .append(r.recommendation())
                .append(")\n")
                .append("Points forts : ")
                .append(String.join(", ", r.strengths()))
                .append("\nRéserves : ")
                .append(String.join(", ", r.concerns()))
                .append("\n\n");
        }
        return chatClient
            .prompt()
            .system(
                context +
                    "\nTu es le président du jury. Synthétise les délibérations des trois membres en un compte-rendu " +
                    "équilibré et actionnable pour le candidat. " +
                    languageRule(language)
            )
            .user("Délibérations du jury :\n\n" + sb + "\nRédige une synthèse : convergences, divergences, et 2-3 conseils concrets.")
            .call()
            .content();
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Prefixes a juror reply with its label so the shared transcript stays attributable. */
    private String tag(JurorRole role, String message) {
        return "[" + role.label() + "] " + message;
    }

    /** Removes a leading "[Something]" the model may echo (it sees the labelled transcript and mimics it). */
    private String stripLeadingLabel(String reply) {
        return reply == null ? "" : reply.replaceFirst("^\\s*\\[[^\\]]{1,40}\\]\\s*", "");
    }

    /** Nominative roster of the jury, e.g. "Camille Rivière (RH), Alex Moreau (Tech Lead), …". */
    private String roster() {
        StringBuilder sb = new StringBuilder();
        for (JurorRole r : JurorRole.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.displayName()).append(" (").append(r.label()).append(")");
        }
        return sb.toString();
    }

    private String buildContext(CandidateProfile profile, JobOffer offer) {
        return """
        CONTEXTE DE L'ENTRETIEN
        POSTE : %s chez %s
        Description : %s

        CANDIDAT : %s
        Compétences : %s
        Expérience : %s
        """.formatted(
            offer.getTitle(),
            offer.getCompany(),
            truncate(offer.getDescription(), 1200),
            profile.getFullName(),
            profile.getSkills(),
            profile.getExperiences()
        );
    }

    private String languageRule(String language) {
        return (language == null || language.isBlank())
            ? "Réponds dans la langue de l'offre d'emploi."
            : "Réponds impérativement en %s.".formatted(language);
    }

    private String overallRecommendation(int avg) {
        if (avg >= 8) return "STRONG_HIRE";
        if (avg >= 6) return "HIRE";
        if (avg >= 4) return "NEUTRAL";
        return "NO_HIRE";
    }

    private List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
