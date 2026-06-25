package nc.opt.aiassistant.batch;

import java.util.List;

/**
 * The 14 ROME "grands domaines" used to partition the France Travail ingestion.
 *
 * <p>The France Travail offers API caps a single search at 3150 results (range 0-3149). To ingest
 * every category without hitting that ceiling, the ingestion is split into one partition per grand
 * domaine, each issuing its own {@code grandDomaine} filtered query.</p>
 *
 * @param code  the single-letter ROME grand domaine code passed as the {@code grandDomaine} API param
 * @param label human-readable label, for logging only
 */
public record RomeGrandDomaine(String code, String label) {
    public static final List<RomeGrandDomaine> ALL = List.of(
        new RomeGrandDomaine("A", "Agriculture et Pêche, Espaces naturels et Espaces verts, Soins aux animaux"),
        new RomeGrandDomaine("B", "Arts et Façonnage d'ouvrages d'art"),
        new RomeGrandDomaine("C", "Banque, Assurance, Immobilier"),
        new RomeGrandDomaine("D", "Commerce, Vente et Grande distribution"),
        new RomeGrandDomaine("E", "Communication, Média et Multimédia"),
        new RomeGrandDomaine("F", "Construction, Bâtiment et Travaux publics"),
        new RomeGrandDomaine("G", "Hôtellerie-Restauration, Tourisme, Loisirs et Animation"),
        new RomeGrandDomaine("H", "Industrie"),
        new RomeGrandDomaine("I", "Installation et Maintenance"),
        new RomeGrandDomaine("J", "Santé"),
        new RomeGrandDomaine("K", "Services à la personne et à la collectivité"),
        new RomeGrandDomaine("L", "Spectacle"),
        new RomeGrandDomaine("M", "Support à l'entreprise"),
        new RomeGrandDomaine("N", "Transport et Logistique")
    );
}
