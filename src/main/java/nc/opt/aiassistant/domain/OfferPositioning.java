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
 * Cached AI positioning analysis of a candidate against a job offer. The {@code result} column holds
 * the serialized {@code CvMatchResultDto} so re-opening the same offer doesn't trigger a new LLM call.
 * One row per {@code (userId, jobOfferId)}.
 */
@Data
@Entity
@Table(
    name = "offer_positioning",
    uniqueConstraints = @UniqueConstraint(name = "ux_offer_positioning_user_offer", columnNames = { "user_id", "job_offer_id" })
)
public class OfferPositioning implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "job_offer_id", nullable = false)
    private UUID jobOfferId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb", nullable = false)
    private String result;

    @Column(name = "created_at")
    private Instant createdAt;
}
