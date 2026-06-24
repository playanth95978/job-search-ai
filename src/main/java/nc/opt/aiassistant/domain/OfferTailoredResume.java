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
 * Cached AI-tailored resume for a given job offer. {@code data} holds the serialized
 * {@code GeneratedResumeDto} (ATS-optimized variant) so re-opening the offer doesn't re-call the LLM.
 * Non-destructive: the user's master resume is untouched. One row per {@code (userId, jobOfferId)}.
 */
@Data
@Entity
@Table(
    name = "offer_tailored_resume",
    uniqueConstraints = @UniqueConstraint(name = "ux_offer_tailored_user_offer", columnNames = { "user_id", "job_offer_id" })
)
public class OfferTailoredResume implements Serializable {

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
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private String data;

    @Column(name = "title")
    private String title;

    @Column(name = "created_at")
    private Instant createdAt;
}
