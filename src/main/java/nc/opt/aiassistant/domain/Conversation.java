package nc.opt.aiassistant.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import nc.opt.aiassistant.domain.enumeration.TypeChat;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A Conversation.
 */
@Data
@Entity
@Table(name = "conversation")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@EntityListeners(AuditingEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Conversation implements Serializable, Persistable<UUID> {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "title")
    private String title;

    @NotNull
    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    // Pas de @Lob : sur PostgreSQL le pilote creerait un large object et n'ecrirait que son OID
    // dans la colonne `text`, invisible a tout SQL exterieur a Hibernate (voir JobOffer).
    @Column(name = "summary")
    private String summary;

    // Pas de @Lob : sur PostgreSQL le pilote creerait un large object et n'ecrirait que son OID
    // dans la colonne `text`, invisible a tout SQL exterieur a Hibernate (voir JobOffer).
    @Column(name = "metadata")
    private String metadata;

    @Version
    private Long version;

    @Transient
    private boolean isNew = true;

    @Enumerated(EnumType.STRING)
    private TypeChat typeChat;

    @PostLoad
    @PostPersist
    public void markNotNew() {
        this.isNew = false;
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Conversation id(UUID id) {
        this.setId(id);
        return this;
    }

    public Conversation userId(String userId) {
        this.setUserId(userId);
        return this;
    }

    public Conversation title(String title) {
        this.setTitle(title);
        return this;
    }

    public Conversation createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public Conversation lastMessageAt(Instant lastMessageAt) {
        this.setLastMessageAt(lastMessageAt);
        return this;
    }

    public Conversation summary(String summary) {
        this.setSummary(summary);
        return this;
    }

    public Conversation metadata(String metadata) {
        this.setMetadata(metadata);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conversation)) {
            return false;
        }
        return getId() != null && getId().equals(((Conversation) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Conversation{" +
            "id=" + getId() +
            ", userId='" + getUserId() + "'" +
            ", title='" + getTitle() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", lastMessageAt='" + getLastMessageAt() + "'" +
            ", summary='" + getSummary() + "'" +
            ", metadata='" + getMetadata() + "'" +
            "}";
    }
}
