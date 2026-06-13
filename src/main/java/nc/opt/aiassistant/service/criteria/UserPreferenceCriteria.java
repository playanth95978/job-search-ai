package nc.opt.aiassistant.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link nc.aattia.rag.domain.UserPreference} entity. This class is used
 * in {@link nc.aattia.rag.web.rest.UserPreferenceResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /user-preferences?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserPreferenceCriteria implements Serializable, Criteria {

    @Serial
    private static final long serialVersionUID = 1L;

    private UUIDFilter id;

    private StringFilter userId;

    private BooleanFilter remoteOnly;

    private StringFilter contractType;

    private IntegerFilter salaryMin;

    private IntegerFilter salaryMax;

    private Boolean distinct;

    public UserPreferenceCriteria() {}

    public UserPreferenceCriteria(UserPreferenceCriteria other) {
        this.id = other.optionalId().map(UUIDFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(StringFilter::copy).orElse(null);
        this.remoteOnly = other.optionalRemoteOnly().map(BooleanFilter::copy).orElse(null);
        this.contractType = other.optionalContractType().map(StringFilter::copy).orElse(null);
        this.salaryMin = other.optionalSalaryMin().map(IntegerFilter::copy).orElse(null);
        this.salaryMax = other.optionalSalaryMax().map(IntegerFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public UserPreferenceCriteria copy() {
        return new UserPreferenceCriteria(this);
    }

    public UUIDFilter getId() {
        return id;
    }

    public Optional<UUIDFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public UUIDFilter id() {
        if (id == null) {
            setId(new UUIDFilter());
        }
        return id;
    }

    public void setId(UUIDFilter id) {
        this.id = id;
    }

    public StringFilter getUserId() {
        return userId;
    }

    public Optional<StringFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public StringFilter userId() {
        if (userId == null) {
            setUserId(new StringFilter());
        }
        return userId;
    }

    public void setUserId(StringFilter userId) {
        this.userId = userId;
    }

    public BooleanFilter getRemoteOnly() {
        return remoteOnly;
    }

    public Optional<BooleanFilter> optionalRemoteOnly() {
        return Optional.ofNullable(remoteOnly);
    }

    public BooleanFilter remoteOnly() {
        if (remoteOnly == null) {
            setRemoteOnly(new BooleanFilter());
        }
        return remoteOnly;
    }

    public void setRemoteOnly(BooleanFilter remoteOnly) {
        this.remoteOnly = remoteOnly;
    }

    public StringFilter getContractType() {
        return contractType;
    }

    public Optional<StringFilter> optionalContractType() {
        return Optional.ofNullable(contractType);
    }

    public StringFilter contractType() {
        if (contractType == null) {
            setContractType(new StringFilter());
        }
        return contractType;
    }

    public void setContractType(StringFilter contractType) {
        this.contractType = contractType;
    }

    public IntegerFilter getSalaryMin() {
        return salaryMin;
    }

    public Optional<IntegerFilter> optionalSalaryMin() {
        return Optional.ofNullable(salaryMin);
    }

    public IntegerFilter salaryMin() {
        if (salaryMin == null) {
            setSalaryMin(new IntegerFilter());
        }
        return salaryMin;
    }

    public void setSalaryMin(IntegerFilter salaryMin) {
        this.salaryMin = salaryMin;
    }

    public IntegerFilter getSalaryMax() {
        return salaryMax;
    }

    public Optional<IntegerFilter> optionalSalaryMax() {
        return Optional.ofNullable(salaryMax);
    }

    public IntegerFilter salaryMax() {
        if (salaryMax == null) {
            setSalaryMax(new IntegerFilter());
        }
        return salaryMax;
    }

    public void setSalaryMax(IntegerFilter salaryMax) {
        this.salaryMax = salaryMax;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserPreferenceCriteria that = (UserPreferenceCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(remoteOnly, that.remoteOnly) &&
            Objects.equals(contractType, that.contractType) &&
            Objects.equals(salaryMin, that.salaryMin) &&
            Objects.equals(salaryMax, that.salaryMax) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, remoteOnly, contractType, salaryMin, salaryMax, distinct);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserPreferenceCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalRemoteOnly().map(f -> "remoteOnly=" + f + ", ").orElse("") +
            optionalContractType().map(f -> "contractType=" + f + ", ").orElse("") +
            optionalSalaryMin().map(f -> "salaryMin=" + f + ", ").orElse("") +
            optionalSalaryMax().map(f -> "salaryMax=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
