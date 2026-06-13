package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "user_preference")
public class UserPreference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "remote_only")
    private Boolean remoteOnly;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_roles", columnDefinition = "jsonb")
    private String preferredRoles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_technologies", columnDefinition = "jsonb")
    private String excludedTechnologies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations", columnDefinition = "jsonb")
    private String preferredLocations;
}
