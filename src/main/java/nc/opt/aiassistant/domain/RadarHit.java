package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One opportunity surfaced by the Opportunity Radar: a freshly ingested offer that matched a user's
 * profile above threshold. Unique per (user, offer) so a re-scan never duplicates it.
 */
@Data
@Entity
@Table(name = "radar_hit", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "job_offer_id" }))
public class RadarHit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @Column(name = "score")
    private double score;

    /** Serialized list of MatchTagDto — the "why you" strengths behind the match. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "why_you", columnDefinition = "jsonb")
    private String whyYou;

    @Column(name = "seen")
    private boolean seen = false;

    @Column(name = "dismissed")
    private boolean dismissed = false;

    @Column(name = "created_at")
    private Instant createdAt;
}
