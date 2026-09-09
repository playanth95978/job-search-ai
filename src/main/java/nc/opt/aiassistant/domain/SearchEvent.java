package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Une recherche telle qu'elle a ete effectuee : la requete, ses filtres, et la strategie employee.
 *
 * <p>Existe pour rendre le classement apprenable. Une ligne d'entrainement de Learning to Rank est
 * un triplet (requete, document, pertinence) accompagne des features disponibles au moment du
 * classement ; sans journaliser la recherche, ces features sont calculees puis jetees a chaque
 * appel et aucun modele ne peut etre entraine dessus.
 *
 * <p>La requete est stockee en clair : c'est la cle de regroupement des impressions, et deux
 * recherches identiques doivent produire deux evenements distincts pour que la position moyenne
 * d'un clic reste mesurable.
 */
@Data
@Entity
@Table(name = "search_event")
public class SearchEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Null pour une recherche anonyme : /search-smart est accessible sans authentification. */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "query", nullable = false, length = 1000)
    private String query;

    @Column(name = "location")
    private String location;

    /** {@code vector}, {@code bm25}, {@code hybrid} ou {@code ltr} — la strategie qui a produit ce classement. */
    @Column(name = "mode", length = 20)
    private String mode;

    /** Origine de l'appel ({@code search-smart}, {@code radar}, {@code matching}...), pour ne pas melanger des contextes. */
    @Column(name = "origin", length = 40)
    private String origin;

    @Column(name = "result_count")
    private int resultCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "searchEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchImpression> impressions = new ArrayList<>();

    public void addImpression(SearchImpression impression) {
        impression.setSearchEvent(this);
        impressions.add(impression);
    }
}
