package nc.opt.aiassistant.service.jobcopilot;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobOfferTranslationService {

    private final ChatClient chatClient;

    public JobOfferTranslationService(@Qualifier("chatClientSmall") ChatClient chatClientSmall) {
        this.chatClient = chatClientSmall;
    }

    public JobOffer translate(JobOffer offer, String language) {
        if (language == null || language.isBlank()) {
            return offer;
        }

        log.info("Translating job offer {} to {}", offer.getId(), language);

        String prompt = """
            Translate the following job offer into %s.
            Keep the original tone and professional terminology.
            The description should stay in HTML if it was originally in HTML.

            Return ONLY a JSON object with two fields: "title" and "description".
            Do not include any other text or markdown fences.

            TITLE: %s
            DESCRIPTION: %s
            """.formatted(language, offer.getTitle(), offer.getDescription());

        record TranslatedOffer(String title, String description) {}

        TranslatedOffer translated = chatClient.prompt().user(prompt).call().entity(TranslatedOffer.class);

        JobOffer result = new JobOffer();
        // On copie les données essentielles
        result.setId(offer.getId());
        result.setTitle(translated.title());
        result.setDescription(translated.description());
        result.setCompany(offer.getCompany());
        result.setLocation(offer.getLocation());
        result.setCountry(offer.getCountry());
        result.setRemote(offer.getRemote());
        result.setApplyUrl(offer.getApplyUrl());
        result.setSalaryMin(offer.getSalaryMin());
        result.setSalaryMax(offer.getSalaryMax());
        result.setSalaryCurrency(offer.getSalaryCurrency());
        result.setContractType(offer.getContractType());
        result.setExperienceLevel(offer.getExperienceLevel());
        result.setSource(offer.getSource());
        result.setPublishedAt(offer.getPublishedAt());

        return result;
    }
}
