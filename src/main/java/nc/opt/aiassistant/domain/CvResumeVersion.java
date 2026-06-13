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
 * Immutable snapshot of a {@link CvResume} captured each time it is saved, enabling versioning
 * and restore.
 */
@Data
@Entity
@Table(name = "resume_version")
public class CvResumeVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "title")
    private String title;

    @Column(name = "template")
    private String template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private String data;

    @Column(name = "created_at")
    private Instant createdAt;
}
