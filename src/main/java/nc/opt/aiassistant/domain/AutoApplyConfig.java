package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import nc.opt.aiassistant.domain.enumeration.AutoApplyMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Per-user configuration of the auto-apply agent: how prepared applications are handled
 * ({@link AutoApplyMode}) and the guardrails (minimum match score, daily cap, allowed sources).
 */
@Data
@Entity
@Table(name = "auto_apply_config")
public class AutoApplyConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private AutoApplyMode mode = AutoApplyMode.VALIDATION;

    /** Minimum match score [0..1] an offer must reach to be auto-applied. */
    @Column(name = "min_score")
    private Double minScore = 0.7;

    /** Maximum applications the agent may create per day. */
    @Column(name = "max_per_day")
    private Integer maxPerDay = 5;

    /** JSON array of allowed source names (empty/null = every auto-apply-capable source). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources", columnDefinition = "jsonb")
    private String sources;
}
