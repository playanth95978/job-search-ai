package nc.opt.aiassistant.service.jobcopilot.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import nc.opt.aiassistant.service.jobcopilot.dto.JobOfferDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.ToolOutcome;
import org.junit.jupiter.api.Test;

class ToolSupportTest {

    private static MatchResultDto match(String title, double score) {
        JobOfferDto offer = new JobOfferDto(
            UUID.randomUUID(),
            title,
            "OPT-NC",
            "Nouméa",
            false,
            "description",
            List.of(),
            "FRANCE_TRAVAIL",
            null,
            null,
            null,
            "CDI"
        );
        return new MatchResultDto(offer, score, List.of(), List.of());
    }

    /**
     * Les boutons doivent nommer les offres que la réponse affiche. Le matcher rend ses résultats
     * triés par score décroissant : citer les trois premiers, c'est citer ce que l'utilisateur lit.
     */
    @Test
    void citesTheTopScoredMatchesInTheOrderTheAnswerShowsThem() {
        List<MatchResultDto> ranked = List.of(
            match("Lead Java", 0.92),
            match("Dev Java", 0.81),
            match("Dev Angular", 0.77),
            match("Stage", 0.40)
        );

        List<ToolOutcome.CitedOffer> cited = ToolSupport.citedMatches(ranked, 3);

        assertThat(cited).extracting(ToolOutcome.CitedOffer::title).containsExactly("Lead Java", "Dev Java", "Dev Angular");
        assertThat(cited).extracting(ToolOutcome.CitedOffer::id).doesNotContainNull();
    }

    @Test
    void citesEverythingWhenThereAreFewerMatchesThanTheLimit() {
        assertThat(ToolSupport.citedMatches(List.of(match("Dev Java", 0.5)), 3)).hasSize(1);
        assertThat(ToolSupport.citedMatches(List.of(), 3)).isEmpty();
    }
}
