package nc.opt.aiassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.Data;

/**
 * Per-user watermark for the Opportunity Radar: offers ingested at or before {@code lastOfferAt} have
 * already been considered for this user, so each scan only looks at what is genuinely new.
 */
@Data
@Entity
@Table(name = "radar_state")
public class RadarState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "last_offer_at")
    private Instant lastOfferAt;
}
