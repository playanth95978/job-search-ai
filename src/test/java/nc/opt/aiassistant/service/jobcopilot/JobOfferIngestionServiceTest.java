package nc.opt.aiassistant.service.jobcopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * L'ingestion reçoit des objets <b>transitoires</b> venus des connecteurs, et l'appelant les garde
 * en main après coup : {@code JobSearchService.searchAll} rend la liste scrapée, que le chat cite
 * pour construire ses boutons « ouvrir cette offre ». L'identifiant doit donc revenir sur l'objet
 * fourni, y compris quand l'offre est déjà connue et qu'aucune écriture n'a lieu.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobOfferIngestionServiceTest {

    @Mock
    private JobOfferRepository jobOfferRepository;

    @Mock
    private JobCategoryResolver categoryResolver;

    private JobOfferIngestionService service;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getJobCopilot().getIngestion().setOfferTtlDays(30);
        service = new JobOfferIngestionService(jobOfferRepository, properties, categoryResolver);
    }

    private static JobOffer scraped() {
        JobOffer offer = new JobOffer();
        offer.setTitle("Développeur Java");
        offer.setCompany("OPT-NC");
        offer.setDescription("Spring Boot, Angular");
        offer.setSource(JobSource.FRANCE_TRAVAIL);
        offer.setSourceId("ft-123");
        return offer;
    }

    @Test
    void carriesTheStoredIdBackOntoAnUnchangedScrapedOffer() {
        JobOffer incoming = scraped();
        UUID storedId = UUID.randomUUID();

        // Même contenu que l'entrant : la préparation s'arrête sur « inchangé » et ne sauvegarde rien.
        JobOffer stored = scraped();
        stored.setId(storedId);
        stored.setContentHash(hashOf(incoming));
        when(jobOfferRepository.findBySourceIdAndSource("ft-123", JobSource.FRANCE_TRAVAIL)).thenReturn(List.of(stored));

        assertThat(service.prepareForIngestion(incoming)).isNull();
        // C'est tout l'objet du correctif : sans cet id, le bouton du chat visait `?offerId=null`.
        assertThat(incoming.getId()).isEqualTo(storedId);
    }

    @Test
    void carriesTheStoredIdBackWhenTheOfferChanged() {
        JobOffer incoming = scraped();
        UUID storedId = UUID.randomUUID();

        JobOffer stored = scraped();
        stored.setId(storedId);
        stored.setContentHash("un-hash-different");
        when(jobOfferRepository.findBySourceIdAndSource("ft-123", JobSource.FRANCE_TRAVAIL)).thenReturn(List.of(stored));

        assertThat(service.prepareForIngestion(incoming)).isSameAs(incoming);
        assertThat(incoming.getId()).isEqualTo(storedId);
    }

    @Test
    void leavesAGenuinelyNewOfferWithoutAnId() {
        JobOffer incoming = scraped();
        when(jobOfferRepository.findBySourceIdAndSource(any(), any())).thenReturn(List.of());
        when(jobOfferRepository.findFirstByContentHash(any())).thenReturn(Optional.empty());

        assertThat(service.prepareForIngestion(incoming)).isSameAs(incoming);
        // L'id viendra de la base au `save` : le poser ici forcerait un merge sur une ligne absente.
        assertThat(incoming.getId()).isNull();
    }

    /** Reproduit le hachage interne, pour fabriquer un « déjà stocké et inchangé » crédible. */
    private static String hashOf(JobOffer offer) {
        String content = (offer.getTitle() + offer.getCompany() + offer.getDescription()).toLowerCase();
        return org.springframework.util.DigestUtils.md5DigestAsHex(content.getBytes());
    }
}
