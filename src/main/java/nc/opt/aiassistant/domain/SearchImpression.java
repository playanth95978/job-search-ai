package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Une offre affichee dans les resultats d'une recherche, avec les features qui ont determine son rang.
 *
 * <p>C'est la ligne d'entrainement du futur modele de classement. Les rangs par bras sont conserves
 * separement du rang final : c'est precisement ce que le RRF ecrase aujourd'hui en appliquant
 * {@code 1/(k + rang)} identiquement aux deux, alors que leur valeur predictive differe selon le
 * corpus et le type de requete.
 *
 * <p>{@code vectorRank} ou {@code bm25Rank} a null signifie « absente de ce bras » — une information
 * en soi, et non une donnee manquante : c'est le cas d'une offre trouvee par le seul lexical, ou
 * l'inverse.
 */
@Data
@Entity
@Table(name = "search_impression", uniqueConstraints = @UniqueConstraint(columnNames = { "search_event_id", "job_offer_id" }))
public class SearchImpression implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_event_id", nullable = false)
    private SearchEvent searchEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    /** Rang final affiche a l'utilisateur, a partir de 1. Porte le biais de position. */
    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "vector_rank")
    private Integer vectorRank;

    @Column(name = "bm25_rank")
    private Integer bm25Rank;

    /** Similarite cosinus dans [0,1]. Null si l'offre ne vient pas du bras vectoriel. */
    @Column(name = "vector_score")
    private Double vectorScore;

    /** Score BM25, non borne. Null si l'offre ne vient pas du bras lexical. */
    @Column(name = "bm25_score")
    private Double bm25Score;

    /** Score de fusion effectivement utilise pour trier (RRF aujourd'hui). */
    @Column(name = "fused_score")
    private Double fusedScore;

    /** Horodate a l'ouverture de l'offre. Null tant que l'utilisateur ne l'a pas ouverte. */
    @Column(name = "clicked_at")
    private Instant clickedAt;

    /** Signal positif le plus fort : l'utilisateur est alle jusqu'a la candidature. */
    @Column(name = "applied")
    private boolean applied = false;

    /**
     * Vrai pour une impression produite par simulation plutot que par un utilisateur.
     *
     * <p>Indispensable : melanger des clics simules et reels dans un meme entrainement apprendrait
     * au modele a imiter le juge LLM. La colonne permet de filtrer, et de mesurer l'ecart entre les
     * deux populations le jour ou les vraies donnees arrivent.
     */
    @Column(name = "simulated", nullable = false)
    private boolean simulated = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
