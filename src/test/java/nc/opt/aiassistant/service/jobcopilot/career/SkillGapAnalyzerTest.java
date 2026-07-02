package nc.opt.aiassistant.service.jobcopilot.career;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import nc.opt.aiassistant.service.jobcopilot.career.SkillGapAnalyzer.OfferSkills;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillGapDto;
import org.junit.jupiter.api.Test;

class SkillGapAnalyzerTest {

    private static SkillGapDto gap(SkillGapAnalyzer.Result r, String skill) {
        return r
            .gaps()
            .stream()
            .filter(g -> g.skill().equalsIgnoreCase(skill))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no gap for " + skill));
    }

    @Test
    void ranksSkillsByMarketDemandAndComputesPercentages() {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        UUID o3 = UUID.randomUUID();
        UUID o4 = UUID.randomUUID();

        List<OfferSkills> offers = List.of(
            new OfferSkills(o1, List.of("Kubernetes", "AWS", "Terraform")),
            new OfferSkills(o2, List.of("Kubernetes", "AWS")),
            new OfferSkills(o3, List.of("Kubernetes", "Go")),
            new OfferSkills(o4, List.of("Go"))
        );

        var result = SkillGapAnalyzer.analyze(List.of("Java", "Docker"), offers);

        assertThat(result.offersAnalyzed()).isEqualTo(4);
        // Sorted by demand desc: Kubernetes (3) first, then AWS & Go (2), then Terraform (1).
        assertThat(result.gaps().get(0).skill()).isEqualToIgnoringCase("kubernetes");
        assertThat(gap(result, "kubernetes").demandCount()).isEqualTo(3);
        assertThat(gap(result, "kubernetes").demandPct()).isEqualTo(75); // 3/4
        assertThat(gap(result, "aws").demandCount()).isEqualTo(2);
        assertThat(gap(result, "aws").demandPct()).isEqualTo(50); // 2/4
        assertThat(gap(result, "terraform").demandPct()).isEqualTo(25); // 1/4
    }

    @Test
    void flagsOwnedSkillsCaseInsensitively() {
        UUID o1 = UUID.randomUUID();
        var offers = List.of(new OfferSkills(o1, List.of("Kubernetes", "AWS")));

        var result = SkillGapAnalyzer.analyze(List.of("kubernetes"), offers);

        assertThat(gap(result, "kubernetes").owned()).isTrue();
        assertThat(gap(result, "aws").owned()).isFalse();
    }

    @Test
    void readinessIsDemandWeighted() {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        UUID o3 = UUID.randomUUID();
        // Kubernetes demanded 3x, AWS 1x. Owning Kubernetes only → 3/(3+1) = 75%.
        var offers = List.of(
            new OfferSkills(o1, List.of("Kubernetes")),
            new OfferSkills(o2, List.of("Kubernetes")),
            new OfferSkills(o3, List.of("Kubernetes", "AWS"))
        );

        var result = SkillGapAnalyzer.analyze(List.of("Kubernetes"), offers);

        assertThat(result.readinessScore()).isEqualTo(75);
    }

    @Test
    void groundsEachSkillToTheOffersThatDemandIt() {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        var offers = List.of(new OfferSkills(o1, List.of("Kubernetes")), new OfferSkills(o2, List.of("Kubernetes", "AWS")));

        var result = SkillGapAnalyzer.analyze(List.of(), offers);

        assertThat(result.evidenceBySkill().get("kubernetes")).containsExactlyInAnyOrder(o1, o2);
        assertThat(result.evidenceBySkill().get("aws")).containsExactly(o2);
    }

    @Test
    void countsEachSkillAtMostOncePerOfferAndIgnoresBlanks() {
        UUID o1 = UUID.randomUUID();
        var offers = List.of(new OfferSkills(o1, List.of("AWS", "aws", " ", "AWS")));

        var result = SkillGapAnalyzer.analyze(List.of(), offers);

        assertThat(result.gaps()).hasSize(1);
        assertThat(gap(result, "aws").demandCount()).isEqualTo(1);
    }

    @Test
    void emptyOffersYieldZeroReadinessAndNoGaps() {
        var result = SkillGapAnalyzer.analyze(List.of("Java"), List.of());

        assertThat(result.offersAnalyzed()).isZero();
        assertThat(result.readinessScore()).isZero();
        assertThat(result.gaps()).isEmpty();
    }
}
