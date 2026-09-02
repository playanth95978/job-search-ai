package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import nc.opt.aiassistant.domain.enumeration.ApplicationStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Data
@Entity
@Table(name = "job_application")
@EntityListeners(AuditingEntityListener.class)
public class JobApplication implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_offer_id")
    private JobOffer jobOffer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id")
    private CandidateProfile candidateProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ApplicationStatus status;

    // Pas de @Lob : sur PostgreSQL le pilote creerait un large object et n'ecrirait que son OID
    // dans la colonne `text`, invisible a tout SQL exterieur a Hibernate (voir JobOffer).
    @Column(name = "cover_letter")
    private String coverLetter;

    // Pas de @Lob : sur PostgreSQL le pilote creerait un large object et n'ecrirait que son OID
    // dans la colonne `text`, invisible a tout SQL exterieur a Hibernate (voir JobOffer).
    @Column(name = "notes")
    private String notes;

    @Column(name = "match_score")
    private Double matchScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;
}
